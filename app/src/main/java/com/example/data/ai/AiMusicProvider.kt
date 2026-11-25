package com.example.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.model.AiChatMessageEntity
import com.example.model.AiConfig
import com.example.model.AiErrorReport
import com.example.model.AiProviderType
import com.example.model.AiSuggestedPlaylist
import com.example.model.EqPreset
import com.example.model.ReasoningEffortLevel
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

private const val TAG = "AiMusicProvider"
private const val MAX_AGENT_ROUNDS = 6

enum class AiTaskType(val title: String, val subtitle: String) {
    TRACK_DEEP_DIVE("深度乐评与封面美学", "结合曲目信息、歌词与专辑封面视觉剖析创作背景"),
    LYRIC_TRANSLATION("双语精译与意象赏析", "深度解读当前歌词文学意象并提供优雅译注"),
    ACOUSTIC_EQ("AI 10段声学调音", "针对音频规格与风格生成 10 段专业 EQ，支持一键应用与保存"),
    MOOD_CURATION("本地曲库氛围策展", "基于当前扫描的本地曲库编排高审美连播歌单")
}

class AiApiException(val report: AiErrorReport) : IllegalStateException(report.summary)

data class AiChatCompletionResult(
    val content: String,
    val reasoningContent: String?,
    val reasoningDurationMs: Long,
    val modelUsed: String,
    val reasoningEffortUsed: ReasoningEffortLevel,
    val usedVision: Boolean,
    val suggestedEqJson: String?,
    val curatedTrackTitles: List<String>,
    val suggestedPlaylistJson: String? = null
)

class DeepSeekChatClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun streamChatCompletion(
        config: AiConfig,
        history: List<AiChatMessageEntity>,
        currentUserText: String,
        effortOverride: ReasoningEffortLevel? = null,
        currentTrack: Track? = null,
        lyricsSnippet: String? = null,
        attachTrackContext: Boolean = false,
        attachCoverVision: Boolean = false,
        attachLibraryCatalog: Boolean = false,
        librarySample: List<Track> = emptyList(),
        catalogLyricsByTrackId: Map<String, String> = emptyMap(),
        onWebSearchStatus: (Boolean, String) -> Unit = { _, _ -> },
        onReasoningDelta: (String) -> Unit = {},
        onContentDelta: (String) -> Unit = {}
    ): Result<AiChatCompletionResult> = withContext(Dispatchers.IO) {
        val effectiveEffort = effortOverride ?: config.reasoningEffort
        val modelId = config.deepSeekModelId.trim().ifBlank { "deepseek-flash" }
        val baseUrl = config.deepSeekBaseUrl.trim().trimEnd('/').ifBlank { "https://api.deepseek.com" }
        val useAnthropicProtocol = DeepSeekEndpointResolver.shouldUseAnthropicMessagesApi(baseUrl)
        val endpoint = if (useAnthropicProtocol) {
            DeepSeekEndpointResolver.resolveAnthropicMessagesEndpoint(baseUrl)
        } else {
            DeepSeekEndpointResolver.resolveOpenAiChatEndpoint(baseUrl)
        }

        if (config.activeProvider == AiProviderType.GEMINI) {
            return@withContext Result.failure(
                AiApiException(
                    AiErrorReport(
                        summary = "Google Gemini 接口已预留但当前未启用，请在右上角模型配置切换至 DeepSeek / 兼容 API。",
                        endpoint = "gemini://reserved",
                        modelId = "gemini-reserved",
                        reasoningEffort = effectiveEffort.apiValue,
                        rawDetail = "Provider GEMINI is intentionally disabled on shared builds to protect owner API quota. Switch to DeepSeek or any OpenAI-compatible API endpoint."
                    )
                )
            )
        }

        if (config.deepSeekApiKey.isBlank()) {
            return@withContext Result.failure(
                AiApiException(
                    AiErrorReport(
                        summary = "尚未配置 API Key，请点击右上角「模型配置」填入你的密钥。",
                        endpoint = endpoint,
                        modelId = modelId,
                        reasoningEffort = effectiveEffort.apiValue,
                        rawDetail = "Missing API Key (deepSeekApiKey is blank)."
                    )
                )
            )
        }

        val systemPrompt = buildSystemPrompt(
            config = config,
            attachTrackContext = attachTrackContext,
            track = currentTrack,
            lyricsSnippet = lyricsSnippet,
            attachLibraryCatalog = attachLibraryCatalog,
            librarySample = librarySample,
            catalogLyricsByTrackId = catalogLyricsByTrackId
        )
        val coverBase64 = if (attachCoverVision && config.enableVisionForCovers && currentTrack != null) {
            encodeArtworkBase64(currentTrack.artworkPath)
        } else {
            null
        }

        val toolsOrigin = DeepSeekEndpointResolver.extractOrigin(baseUrl)
        if (useAnthropicProtocol) {
            executeAnthropicMessagesFlow(
                endpoint = endpoint,
                toolsOrigin = toolsOrigin,
                modelId = modelId,
                systemPrompt = systemPrompt,
                history = history,
                currentUserText = currentUserText,
                coverBase64 = coverBase64,
                config = config,
                effectiveEffort = effectiveEffort,
                librarySample = librarySample,
                onWebSearchStatus = onWebSearchStatus,
                onReasoningDelta = onReasoningDelta,
                onContentDelta = onContentDelta
            )
        } else {
            executeOpenAiChatFlow(
                endpoint = endpoint,
                toolsOrigin = toolsOrigin,
                modelId = modelId,
                systemPrompt = systemPrompt,
                history = history,
                currentUserText = currentUserText,
                coverBase64 = coverBase64,
                config = config,
                effectiveEffort = effectiveEffort,
                librarySample = librarySample,
                onWebSearchStatus = onWebSearchStatus,
                onReasoningDelta = onReasoningDelta,
                onContentDelta = onContentDelta
            )
        }
    }

    /**
     * Native DeepSeek Anthropic-compatible Messages API flow (`/anthropic/v1/messages`),
     * matching `deepseek-ai/chat-demo`.
     *
     * Supports:
     * - Interleaved Deep Thinking Search (`thinking` + `server_tool_use` + `*_tool_result` + `text`)
     * - `output_config.effort` & `thinking.type`
     * - Automatic `pause_turn` continuation across long server-side search sessions
     * - Automatic client-side `tool_use` execution via `/v1/tools/search` & `/v1/tools/fetch`
     */
    private suspend fun executeAnthropicMessagesFlow(
        endpoint: String,
        toolsOrigin: String,
        modelId: String,
        systemPrompt: String,
        history: List<AiChatMessageEntity>,
        currentUserText: String,
        coverBase64: String?,
        config: AiConfig,
        effectiveEffort: ReasoningEffortLevel,
        librarySample: List<Track>,
        onWebSearchStatus: (Boolean, String) -> Unit,
        onReasoningDelta: (String) -> Unit,
        onContentDelta: (String) -> Unit
    ): Result<AiChatCompletionResult> {
        val workingMessages = buildAnthropicMessagesArray(
            history = history,
            currentUserText = currentUserText,
            coverBase64 = coverBase64
        )
        val reasoningBuilder = StringBuilder()
        val contentBuilder = StringBuilder()
        val allCitations = linkedMapOf<String, WebCitation>()
        var reasoningStartedMs = 0L
        var reasoningEndedMs = 0L
        var lastHttpCode = 200

        val callbacks = object : AnthropicStreamCallbacks {
            override fun onReasoningDelta(delta: String) {
                if (delta.isEmpty()) return
                if (reasoningStartedMs == 0L) reasoningStartedMs = System.currentTimeMillis()
                reasoningBuilder.append(delta)
                onReasoningDelta(delta)
            }

            override fun onContentDelta(delta: String) {
                if (delta.isEmpty()) return
                if (reasoningStartedMs > 0L) {
                    reasoningEndedMs = System.currentTimeMillis()
                }
                contentBuilder.append(delta)
                onContentDelta(delta)
            }

            override fun onWebSearchStatus(searching: Boolean, label: String) {
                onWebSearchStatus(searching, label)
            }
        }

        for (round in 0 until MAX_AGENT_ROUNDS) {
            coroutineContext.ensureActive()
            val payload = buildAnthropicRequestPayload(
                modelId = modelId,
                systemPrompt = systemPrompt,
                messages = workingMessages,
                config = config,
                effectiveEffort = effectiveEffort
            )

            val request = Request.Builder()
                .url(endpoint)
                .header("x-api-key", config.deepSeekApiKey.trim())
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .header(
                    "Accept",
                    if (config.enableStreaming) "text/event-stream, application/json" else "application/json"
                )
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val call = client.newCall(request)
            val accumulator = AnthropicTurnAccumulator(
                hasExistingReasoning = { reasoningBuilder.isNotEmpty() },
                hasExistingContent = { contentBuilder.isNotEmpty() }
            )

            try {
                call.execute().use { response ->
                    lastHttpCode = response.code
                    if (!response.isSuccessful) {
                        val rawBody = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                        return Result.failure(
                            buildHttpErrorException(
                                code = response.code,
                                message = response.message,
                                rawBody = rawBody,
                                endpoint = endpoint,
                                modelId = modelId,
                                effort = effectiveEffort
                            )
                        )
                    }

                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    if (config.enableStreaming && contentType.contains("text/event-stream")) {
                        val source = response.body?.source()
                        if (source != null) {
                            var currentEvent: String? = null
                            while (!source.exhausted()) {
                                coroutineContext.ensureActive()
                                val line = source.readUtf8Line() ?: break
                                when {
                                    line.isBlank() -> currentEvent = null
                                    line.startsWith("event:") -> {
                                        currentEvent = line.removePrefix("event:").trim()
                                    }

                                    line.startsWith("data:") -> {
                                        val data = line.removePrefix("data:").trim()
                                        if (data.isEmpty() || data == "[DONE]") continue
                                        val json = runCatching { JSONObject(data) }.getOrNull() ?: continue
                                        accumulator.processSseEvent(currentEvent, json, callbacks)
                                        val err = accumulator.streamError
                                        if (!err.isNullOrBlank()) {
                                            return Result.failure(
                                                AiApiException(
                                                    AiErrorReport(
                                                        summary = "流式输出中断 · $err",
                                                        httpStatusCode = response.code,
                                                        endpoint = endpoint,
                                                        modelId = modelId,
                                                        reasoningEffort = effectiveEffort.apiValue,
                                                        rawDetail = data
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val rawBody = response.body?.string().orEmpty()
                        val root = runCatching { JSONObject(rawBody) }.getOrElse { parseErr ->
                            return Result.failure(
                                AiApiException(
                                    AiErrorReport(
                                        summary = "响应 JSON 解析失败 · ${parseErr.localizedMessage}",
                                        httpStatusCode = response.code,
                                        endpoint = endpoint,
                                        modelId = modelId,
                                        reasoningEffort = effectiveEffort.apiValue,
                                        rawDetail = rawBody.take(3000)
                                    )
                                )
                            )
                        }
                        if (root.optString("type") == "error" || root.has("error")) {
                            val errMsg = extractServerErrorMessage(rawBody) ?: "API 返回错误"
                            return Result.failure(
                                AiApiException(
                                    AiErrorReport(
                                        summary = errMsg,
                                        httpStatusCode = response.code,
                                        endpoint = endpoint,
                                        modelId = modelId,
                                        reasoningEffort = effectiveEffort.apiValue,
                                        rawDetail = rawBody.take(3000)
                                    )
                                )
                            )
                        }
                        accumulator.processNonStreamingMessage(root, callbacks)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                call.cancel()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Anthropic Messages request failed on round $round", e)
                return Result.failure(buildNetworkErrorException(e, endpoint, modelId, effectiveEffort))
            }

            allCitations.putAll(accumulator.citations)
            val assistantBlocks = accumulator.toAssistantContentBlocks()
            val stopReason = accumulator.stopReason

            when {
                stopReason == "pause_turn" && assistantBlocks.length() > 0 && round < MAX_AGENT_ROUNDS - 1 -> {
                    onWebSearchStatus(true, "正在继续多步深度检索与思考...")
                    workingMessages.put(
                        JSONObject()
                            .put("role", "assistant")
                            .put("content", assistantBlocks)
                    )
                    continue
                }

                stopReason == "tool_use" && round < MAX_AGENT_ROUNDS - 1 -> {
                    val toolCalls = accumulator.extractClientToolUses()
                    if (toolCalls.isEmpty()) break
                    workingMessages.put(
                        JSONObject()
                            .put("role", "assistant")
                            .put("content", assistantBlocks)
                    )
                    val toolResultsArr = JSONArray()
                    for (tc in toolCalls) {
                        coroutineContext.ensureActive()
                        val queryPreview = extractQuerySummary(tc.input) ?: extractUrlSummary(tc.input)
                        val label = if (tc.name.contains("read_url", true) || tc.name.contains("fetch", true)) {
                            "正在读取网页：${queryPreview?.take(28) ?: "目标链接"}"
                        } else {
                            "正在联网搜索：${queryPreview?.let { "「${it.take(26)}」" } ?: "实时资料"}"
                        }
                        onWebSearchStatus(true, label)

                        val outcome = DeepSeekStandaloneTools.executeToolCall(
                            client = client,
                            origin = toolsOrigin,
                            apiKey = config.deepSeekApiKey,
                            toolName = tc.name,
                            arguments = tc.input,
                            originalQuestion = currentUserText
                        )
                        outcome.citations.forEach { allCitations.putIfAbsent(it.url, it) }
                        outcome.summaryForTrace?.let { trace ->
                            callbacks.onReasoningDelta("\n> [[RESULT]] **$trace**\n")
                        }
                        toolResultsArr.put(
                            JSONObject()
                                .put("type", "tool_result")
                                .put("tool_use_id", tc.id)
                                .put("content", outcome.payloadForModel)
                        )
                    }
                    workingMessages.put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", toolResultsArr)
                    )
                    continue
                }

                else -> break
            }
        }

        onWebSearchStatus(false, "")
        return finalizeCompletionResult(
            contentBuilder = contentBuilder,
            reasoningBuilder = reasoningBuilder,
            citations = allCitations.values,
            reasoningStartedMs = reasoningStartedMs,
            reasoningEndedMs = reasoningEndedMs,
            lastHttpCode = lastHttpCode,
            endpoint = endpoint,
            modelId = modelId,
            effectiveEffort = effectiveEffort,
            usedVision = coverBase64 != null,
            librarySample = librarySample,
            onContentDelta = onContentDelta
        )
    }

    /**
     * OpenAI-compatible Chat Completions flow (`/chat/completions`) for third-party gateways
     * or explicit OpenAI endpoints. Includes `thinking` toggle + client-side `tool_calls`
     * agent loop via DeepSeek Search & Fetch APIs so tool calls never stall mid-turn.
     */
    private suspend fun executeOpenAiChatFlow(
        endpoint: String,
        toolsOrigin: String,
        modelId: String,
        systemPrompt: String,
        history: List<AiChatMessageEntity>,
        currentUserText: String,
        coverBase64: String?,
        config: AiConfig,
        effectiveEffort: ReasoningEffortLevel,
        librarySample: List<Track>,
        onWebSearchStatus: (Boolean, String) -> Unit,
        onReasoningDelta: (String) -> Unit,
        onContentDelta: (String) -> Unit
    ): Result<AiChatCompletionResult> {
        val workingMessages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            history
                .filter { it.role == "user" || it.role == "assistant" }
                .takeLast(24)
                .forEach { msg ->
                    val cleanedContent = msg.content.trim()
                    if (cleanedContent.isNotEmpty()) {
                        put(JSONObject().put("role", msg.role).put("content", cleanedContent))
                    }
                }

            val userObj = JSONObject().put("role", "user")
            if (coverBase64 != null) {
                val parts = JSONArray().apply {
                    put(JSONObject().put("type", "text").put("text", currentUserText))
                    put(
                        JSONObject()
                            .put("type", "image_url")
                            .put(
                                "image_url",
                                JSONObject().put("url", "data:image/jpeg;base64,$coverBase64")
                            )
                    )
                }
                userObj.put("content", parts)
            } else {
                userObj.put("content", currentUserText)
            }
            put(userObj)
        }

        val reasoningBuilder = StringBuilder()
        val contentBuilder = StringBuilder()
        val allCitations = linkedMapOf<String, WebCitation>()
        var reasoningStartedMs = 0L
        var reasoningEndedMs = 0L
        var lastHttpCode = 200

        for (round in 0 until MAX_AGENT_ROUNDS) {
            coroutineContext.ensureActive()
            val payload = buildRequestPayload(
                modelId = modelId,
                messages = workingMessages,
                config = config,
                effectiveEffort = effectiveEffort
            )

            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer ${config.deepSeekApiKey.trim()}")
                .header("Content-Type", "application/json")
                .header(
                    "Accept",
                    if (config.enableStreaming) "text/event-stream, application/json" else "application/json"
                )
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val call = client.newCall(request)
            val roundReasoning = StringBuilder()
            val roundContent = StringBuilder()
            val roundToolCalls = linkedMapOf<Int, OpenAiToolCallAccumulator>()
            var finishReason: String? = null

            try {
                call.execute().use { response ->
                    lastHttpCode = response.code
                    if (!response.isSuccessful) {
                        val rawBody = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                        return Result.failure(
                            buildHttpErrorException(
                                code = response.code,
                                message = response.message,
                                rawBody = rawBody,
                                endpoint = endpoint,
                                modelId = modelId,
                                effort = effectiveEffort
                            )
                        )
                    }

                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    if (config.enableStreaming && contentType.contains("text/event-stream")) {
                        val source = response.body?.source()
                        if (source != null) {
                            val thinkTagBuffer = StringBuilder()
                            var insideThinkTag = false
                            if (round > 0 && contentBuilder.isNotEmpty()) {
                                contentBuilder.append("\n\n")
                                onContentDelta("\n\n")
                            }
                            while (!source.exhausted()) {
                                coroutineContext.ensureActive()
                                val line = source.readUtf8Line() ?: break
                                if (!line.startsWith("data:")) continue
                                val data = line.removePrefix("data:").trim()
                                if (data.isEmpty() || data == "[DONE]") continue

                                val chunkObj = runCatching { JSONObject(data) }.getOrNull() ?: continue
                                val streamErr = chunkObj.optJSONObject("error")
                                if (streamErr != null) {
                                    val errMsg = streamErr.optString("message", data)
                                    return Result.failure(
                                        AiApiException(
                                            AiErrorReport(
                                                summary = "流式输出中断 · $errMsg",
                                                httpStatusCode = response.code,
                                                endpoint = endpoint,
                                                modelId = modelId,
                                                reasoningEffort = effectiveEffort.apiValue,
                                                rawDetail = data
                                            )
                                        )
                                    )
                                }

                                val choice0 = chunkObj.optJSONArray("choices")?.optJSONObject(0)
                                val fr = choice0?.optString("finish_reason", "")
                                    ?.takeIf { it.isNotBlank() && it != "null" }
                                if (fr != null) finishReason = fr

                                val delta = choice0?.optJSONObject("delta")
                                val hasServerToolEvent = chunkObj.has("server_tool_event") ||
                                    choice0?.has("server_tool_event") == true ||
                                    delta?.has("server_tool_event") == true ||
                                    delta?.has("server_tool_calls") == true
                                if (hasServerToolEvent) {
                                    onWebSearchStatus(true, "正在联网检索资料")
                                }
                                if (delta == null) continue

                                // Extract OpenAI annotations/citations if present
                                extractCitationsFromAnyJson(delta.opt("annotations")).forEach {
                                    allCitations.putIfAbsent(it.url, it)
                                }

                                // Accumulate streamed tool_calls
                                val tcArr = delta.optJSONArray("tool_calls")
                                if (tcArr != null) {
                                    onWebSearchStatus(true, "正在准备联网检索参数")
                                    for (i in 0 until tcArr.length()) {
                                        val tcObj = tcArr.optJSONObject(i) ?: continue
                                        val idx = tcObj.optInt("index", i)
                                        val acc = roundToolCalls.getOrPut(idx) { OpenAiToolCallAccumulator() }
                                        val id = tcObj.optString("id", "")
                                        if (id.isNotBlank() && id != "null") acc.id = id
                                        val tType = tcObj.optString("type", "")
                                        if (tType.isNotBlank() && tType != "null") acc.type = tType
                                        val fn = tcObj.optJSONObject("function")
                                        if (fn != null) {
                                            val name = fn.optString("name", "")
                                            if (name.isNotBlank() && name != "null") acc.name += name
                                            val args = fn.optString("arguments", "")
                                            if (args.isNotEmpty() && args != "null") acc.arguments.append(args)
                                        }
                                    }
                                }

                                val rDelta = extractReasoningField(delta)
                                if (!rDelta.isNullOrEmpty()) {
                                    if (reasoningStartedMs == 0L) reasoningStartedMs = System.currentTimeMillis()
                                    roundReasoning.append(rDelta)
                                    reasoningBuilder.append(rDelta)
                                    onReasoningDelta(rDelta)
                                }

                                val cDelta = delta.optString("content", "")
                                    .takeIf { it.isNotEmpty() && it != "null" }
                                if (!cDelta.isNullOrEmpty()) {
                                    onWebSearchStatus(false, "")
                                    if (reasoningStartedMs > 0L) {
                                        reasoningEndedMs = System.currentTimeMillis()
                                    }
                                    thinkTagBuffer.append(cDelta)
                                    insideThinkTag = drainThinkTags(
                                        buffer = thinkTagBuffer,
                                        inThink = insideThinkTag,
                                        onThink = { t ->
                                            if (reasoningStartedMs == 0L) reasoningStartedMs = System.currentTimeMillis()
                                            roundReasoning.append(t)
                                            reasoningBuilder.append(t)
                                            onReasoningDelta(t)
                                        },
                                        onText = { txt ->
                                            if (reasoningStartedMs > 0L) {
                                                reasoningEndedMs = System.currentTimeMillis()
                                            }
                                            roundContent.append(txt)
                                            contentBuilder.append(txt)
                                            onContentDelta(txt)
                                        }
                                    )
                                }
                            }
                            if (thinkTagBuffer.isNotEmpty()) {
                                val rem = thinkTagBuffer.toString()
                                if (insideThinkTag) {
                                    roundReasoning.append(rem)
                                    reasoningBuilder.append(rem)
                                    onReasoningDelta(rem)
                                } else {
                                    roundContent.append(rem)
                                    contentBuilder.append(rem)
                                    onContentDelta(rem)
                                }
                                thinkTagBuffer.clear()
                            }
                        }
                    } else {
                        val rawBody = response.body?.string().orEmpty()
                        val root = runCatching { JSONObject(rawBody) }.getOrElse { parseErr ->
                            return Result.failure(
                                AiApiException(
                                    AiErrorReport(
                                        summary = "响应 JSON 解析失败 · ${parseErr.localizedMessage}",
                                        httpStatusCode = response.code,
                                        endpoint = endpoint,
                                        modelId = modelId,
                                        reasoningEffort = effectiveEffort.apiValue,
                                        rawDetail = rawBody.take(3000)
                                    )
                                )
                            )
                        }
                        val choice0 = root.optJSONArray("choices")?.optJSONObject(0)
                        finishReason = choice0?.optString("finish_reason", "")
                            ?.takeIf { it.isNotBlank() && it != "null" }
                        val messageObj = choice0?.optJSONObject("message")
                        extractCitationsFromAnyJson(messageObj?.opt("annotations")).forEach {
                            allCitations.putIfAbsent(it.url, it)
                        }

                        val tcArr = messageObj?.optJSONArray("tool_calls")
                        if (tcArr != null) {
                            for (i in 0 until tcArr.length()) {
                                val tcObj = tcArr.optJSONObject(i) ?: continue
                                val fn = tcObj.optJSONObject("function")
                                val acc = OpenAiToolCallAccumulator(
                                    id = tcObj.optString("id", "call_$i"),
                                    type = tcObj.optString("type", "function"),
                                    name = fn?.optString("name", "").orEmpty()
                                )
                                acc.arguments.append(fn?.optString("arguments", "").orEmpty())
                                roundToolCalls[i] = acc
                            }
                        }

                        val rawReasoning = messageObj?.let { extractReasoningField(it) }.orEmpty()
                        val rawContent = messageObj?.optString("content", "")
                            ?.takeIf { it != "null" }
                            .orEmpty()
                        val (inlineThink, cleanContent) = splitInlineThinkBlock(rawContent)
                        val combinedReasoning = listOf(rawReasoning, inlineThink)
                            .filter { it.isNotBlank() }
                            .joinToString("\n")
                        if (combinedReasoning.isNotBlank()) {
                            if (reasoningStartedMs == 0L) reasoningStartedMs = System.currentTimeMillis()
                            if (round > 0 && reasoningBuilder.isNotEmpty()) {
                                reasoningBuilder.append("\n\n")
                                onReasoningDelta("\n\n")
                            }
                            roundReasoning.append(combinedReasoning)
                            reasoningBuilder.append(combinedReasoning)
                            onReasoningDelta(combinedReasoning)
                        }
                        if (cleanContent.isNotBlank()) {
                            if (round > 0 && contentBuilder.isNotEmpty()) {
                                contentBuilder.append("\n\n")
                                onContentDelta("\n\n")
                            }
                            roundContent.append(cleanContent)
                            contentBuilder.append(cleanContent)
                            onContentDelta(cleanContent)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                call.cancel()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "OpenAI Chat request failed on round $round", e)
                return Result.failure(buildNetworkErrorException(e, endpoint, modelId, effectiveEffort))
            }

            // Handle agentic client-side tool_calls loop
            if ((finishReason == "tool_calls" || roundToolCalls.isNotEmpty()) &&
                roundToolCalls.isNotEmpty() &&
                round < MAX_AGENT_ROUNDS - 1
            ) {
                val assistantToolCallsArr = JSONArray()
                val sortedCalls = roundToolCalls.toSortedMap().values.toList()
                sortedCalls.forEachIndexed { idx, tc ->
                    val callId = tc.id.ifBlank { "call_${round}_$idx" }
                    assistantToolCallsArr.put(
                        JSONObject()
                            .put("id", callId)
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject()
                                    .put("name", tc.name)
                                    .put("arguments", tc.arguments.toString().ifBlank { "{}" })
                            )
                    )
                }
                val assistantMsg = JSONObject()
                    .put("role", "assistant")
                    .put("content", roundContent.toString())
                    .put("tool_calls", assistantToolCallsArr)
                if (roundReasoning.isNotEmpty()) {
                    assistantMsg.put("reasoning_content", roundReasoning.toString())
                }
                workingMessages.put(assistantMsg)

                sortedCalls.forEachIndexed { idx, tc ->
                    coroutineContext.ensureActive()
                    val callId = tc.id.ifBlank { "call_${round}_$idx" }
                    val parsedArgs = runCatching { JSONObject(tc.arguments.toString()) }.getOrDefault(JSONObject())
                    val queryPreview = extractQuerySummary(parsedArgs) ?: extractUrlSummary(parsedArgs)
                    val isRead = tc.name.contains("read_url", true) ||
                        tc.name.contains("fetch", true) ||
                        (!queryPreview.isNullOrBlank() && isHttpUrl(queryPreview))
                    val tracePrefix = if (reasoningBuilder.isNotEmpty()) "\n\n" else ""
                    val traceLine = if (isRead) {
                        "$tracePrefix> [[FETCH]] **读取网页**：${queryPreview?.let(::formatReadableUrl) ?: "目标链接"}\n"
                    } else {
                        "$tracePrefix> [[SEARCH]] **联网搜索**：${queryPreview ?: "实时网络检索"}\n"
                    }
                    if (reasoningStartedMs == 0L) reasoningStartedMs = System.currentTimeMillis()
                    reasoningBuilder.append(traceLine)
                    onReasoningDelta(traceLine)
                    onWebSearchStatus(
                        true,
                        if (isRead) "正在读取网页：${queryPreview?.let(::formatReadableUrl)?.take(28) ?: "目标链接"}"
                        else "正在联网搜索：${queryPreview?.let { "「${it.take(26)}」" } ?: "实时资料"}"
                    )

                    val outcome = DeepSeekStandaloneTools.executeToolCall(
                        client = client,
                        origin = toolsOrigin,
                        apiKey = config.deepSeekApiKey,
                        toolName = tc.name,
                        arguments = parsedArgs,
                        originalQuestion = currentUserText
                    )
                    outcome.citations.forEach { allCitations.putIfAbsent(it.url, it) }
                    outcome.summaryForTrace?.let { summary ->
                        val summaryLine = "\n> [[RESULT]] **$summary**\n"
                        reasoningBuilder.append(summaryLine)
                        onReasoningDelta(summaryLine)
                    }
                    workingMessages.put(
                        JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", callId)
                            .put("content", outcome.payloadForModel)
                    )
                }
                continue
            }

            break
        }

        onWebSearchStatus(false, "")
        return finalizeCompletionResult(
            contentBuilder = contentBuilder,
            reasoningBuilder = reasoningBuilder,
            citations = allCitations.values,
            reasoningStartedMs = reasoningStartedMs,
            reasoningEndedMs = reasoningEndedMs,
            lastHttpCode = lastHttpCode,
            endpoint = endpoint,
            modelId = modelId,
            effectiveEffort = effectiveEffort,
            usedVision = coverBase64 != null,
            librarySample = librarySample,
            onContentDelta = onContentDelta
        )
    }

    private fun finalizeCompletionResult(
        contentBuilder: StringBuilder,
        reasoningBuilder: StringBuilder,
        citations: Collection<WebCitation>,
        reasoningStartedMs: Long,
        reasoningEndedMs: Long,
        lastHttpCode: Int,
        endpoint: String,
        modelId: String,
        effectiveEffort: ReasoningEffortLevel,
        usedVision: Boolean,
        librarySample: List<Track>,
        onContentDelta: (String) -> Unit
    ): Result<AiChatCompletionResult> {
        val citationsFooter = formatCitationsFooter(contentBuilder.toString(), citations)
        if (citationsFooter.isNotEmpty()) {
            contentBuilder.append(citationsFooter)
            onContentDelta(citationsFooter)
        }

        val finalContent = contentBuilder.toString().trim()
        val finalReasoning = reasoningBuilder.toString().trim().takeIf { it.isNotBlank() }
        val reasoningDuration = when {
            reasoningStartedMs > 0L && reasoningEndedMs > reasoningStartedMs ->
                reasoningEndedMs - reasoningStartedMs
            reasoningStartedMs > 0L ->
                (System.currentTimeMillis() - reasoningStartedMs).coerceAtLeast(100L)
            else -> 0L
        }

        if (finalContent.isBlank() && finalReasoning.isNullOrBlank()) {
            return Result.failure(
                AiApiException(
                    AiErrorReport(
                        summary = "模型返回内容为空，请检查模型 ID 或重试。",
                        httpStatusCode = lastHttpCode,
                        endpoint = endpoint,
                        modelId = modelId,
                        reasoningEffort = effectiveEffort.apiValue,
                        rawDetail = "Empty assistant content and reasoning_content in HTTP $lastHttpCode response."
                    )
                )
            )
        }

        val eqJson = extractEqJsonRaw(finalContent)
        val playlistJson = extractPlaylistJsonRaw(finalContent)
        val parsedPlaylist = parseSuggestedPlaylist(playlistJson)
        val curated = parsedPlaylist?.trackTitles?.ifEmpty { null }
            ?: extractCuratedTitles(finalContent, librarySample)

        return Result.success(
            AiChatCompletionResult(
                content = finalContent.ifBlank { "（模型仅输出了思考过程，未生成最终回复文本）" },
                reasoningContent = finalReasoning,
                reasoningDurationMs = reasoningDuration,
                modelUsed = modelId,
                reasoningEffortUsed = effectiveEffort,
                usedVision = usedVision,
                suggestedEqJson = eqJson,
                curatedTrackTitles = curated,
                suggestedPlaylistJson = playlistJson
            )
        )
    }

    private fun buildHttpErrorException(
        code: Int,
        message: String,
        rawBody: String,
        endpoint: String,
        modelId: String,
        effort: ReasoningEffortLevel
    ): AiApiException {
        val parsedMsg = extractServerErrorMessage(rawBody)
        val summary = buildString {
            append("HTTP $code")
            if (!parsedMsg.isNullOrBlank()) {
                append(" · $parsedMsg")
            } else if (message.isNotBlank()) {
                append(" · $message")
            }
        }
        return AiApiException(
            AiErrorReport(
                summary = summary,
                httpStatusCode = code,
                endpoint = endpoint,
                modelId = modelId,
                reasoningEffort = effort.apiValue,
                rawDetail = rawBody.ifBlank { "HTTP $code $message" }
            )
        )
    }

    private fun buildNetworkErrorException(
        e: Exception,
        endpoint: String,
        modelId: String,
        effort: ReasoningEffortLevel
    ): AiApiException {
        val detail = buildString {
            appendLine("${e.javaClass.name}: ${e.message}")
            e.cause?.let { appendLine("Caused by: ${it.javaClass.name}: ${it.message}") }
        }.trim()
        return AiApiException(
            AiErrorReport(
                summary = "请求异常 · ${e.localizedMessage ?: e.javaClass.simpleName}",
                httpStatusCode = null,
                endpoint = endpoint,
                modelId = modelId,
                reasoningEffort = effort.apiValue,
                rawDetail = detail
            )
        )
    }

    /**
     * Returns concise, readable user-facing prompt text for quick tasks.
     * Actual track details, lyrics, and selected music catalog are transmitted cleanly
     * via the structured system context rather than polluting the user chat bubble.
     */
    fun buildQuickTaskPrompt(
        taskType: AiTaskType,
        track: Track? = null
    ): String {
        val trackRef = track?.let { "「${it.title}」" } ?: "当前曲目"
        return when (taskType) {
            AiTaskType.TRACK_DEEP_DIVE ->
                "请为正在聆听的$trackRef 撰写一份杂志级的唱片导赏：包含声学与编曲气质、情感叙事与聆听场景；若附带了专辑封面，也请结合封面色彩与构图做视觉美学点评。"

            AiTaskType.LYRIC_TRANSLATION ->
                "请为$trackRef 的歌词提供高审美的文学意象赏析与关键句双语精译及注释。"

            AiTaskType.ACOUSTIC_EQ ->
                "请作为母带声学工程师，为$trackRef 推荐最优的 10 段均衡器参数（31Hz~16kHz）与声场设定，简要解释各频段调音思路，并在末尾附带标准 EQ_JSON。"

            AiTaskType.MOOD_CURATION ->
                "请结合我附带的本地音乐曲库，挑选 5~10 首最契合「深夜沉浸 / 高级沙龙氛围」的曲目，编排一个起承转合流畅的连播歌单并给出策展导语，同时在末尾附带 PLAYLIST_JSON 供我一键创建歌单。"
        }
    }

    suspend fun generateSessionTitle(
        config: AiConfig,
        userPrompt: String,
        assistantReply: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        if (config.activeProvider == AiProviderType.GEMINI || config.deepSeekApiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("未配置可用 API"))
        }
        val modelId = config.deepSeekModelId.trim().ifBlank { "deepseek-flash" }
        val baseUrl = config.deepSeekBaseUrl.trim().trimEnd('/').ifBlank { "https://api.deepseek.com" }
        val endpoint = DeepSeekEndpointResolver.resolveOpenAiChatEndpoint(baseUrl)

        val messages = JSONArray().apply {
            put(
                JSONObject()
                    .put("role", "system")
                    .put("content", "你是对话标题提炼助手。请根据用户问题提炼一个简洁、雅致的中文短标题（4~14个字），不要加引号、书名号或任何标点，直接输出标题文本。")
            )
            val contextText = buildString {
                appendLine("用户提问：${userPrompt.take(360)}")
                if (!assistantReply.isNullOrBlank()) {
                    appendLine("助手摘要：${assistantReply.take(260)}")
                }
            }
            put(JSONObject().put("role", "user").put("content", contextText))
        }
        val payload = JSONObject()
            .put("model", modelId)
            .put("messages", messages)
            .put("stream", false)
            .put("max_tokens", 48)
            .put("thinking", JSONObject().put("type", "disabled"))

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${config.deepSeekApiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val root = JSONObject(body)
                val raw = root.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content", "")
                    .orEmpty()
                val (_, cleaned) = splitInlineThinkBlock(raw)
                cleaned.lineSequence()
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                    .trim()
                    .trim('"', '“', '”', '\'', '《', '》', '【', '】', '。', '.', '：', ':')
                    .take(18)
                    .ifBlank { error("标题为空") }
            }
        }
    }

    internal fun buildAnthropicRequestPayload(
        modelId: String,
        systemPrompt: String,
        messages: JSONArray,
        config: AiConfig,
        effectiveEffort: ReasoningEffortLevel
    ): JSONObject {
        val payload = JSONObject()
            .put("model", modelId)
            .put("max_tokens", config.maxOutputTokens.coerceAtLeast(256))
            .put("stream", config.enableStreaming)
            .put("system", systemPrompt)
            .put("messages", messages)

        if (effectiveEffort == ReasoningEffortLevel.OFF) {
            payload.put("thinking", JSONObject().put("type", "disabled"))
        } else {
            payload.put("thinking", JSONObject().put("type", "enabled"))
            payload.put("output_config", JSONObject().put("effort", effectiveEffort.apiValue))
        }

        if (config.enableWebSearch) {
            val tools = JSONArray().apply {
                put(
                    JSONObject()
                        .put("type", "deepseek_web_search")
                        .put("name", "deepseek_web_search")
                )
                put(
                    JSONObject()
                        .put("type", "deepseek_read_url")
                        .put("name", "deepseek_read_url")
                )
            }
            payload.put("tools", tools)
        }
        return payload
    }

    internal fun buildRequestPayload(
        modelId: String,
        messages: JSONArray,
        config: AiConfig,
        effectiveEffort: ReasoningEffortLevel
    ): JSONObject {
        val payload = JSONObject()
            .put("model", modelId)
            .put("messages", messages)
            .put("stream", config.enableStreaming)
            .put("max_tokens", config.maxOutputTokens.coerceAtLeast(256))

        if (effectiveEffort != ReasoningEffortLevel.OFF) {
            payload.put("thinking", JSONObject().put("type", "enabled"))
            payload.put("reasoning_effort", effectiveEffort.apiValue)
        } else {
            payload.put("thinking", JSONObject().put("type", "disabled"))
        }

        // DeepSeek native server-side web search & URL reading tools.
        // Per DeepSeek API spec, NEVER pass tool_choice alongside built-in web tools.
        if (config.enableWebSearch) {
            val tools = JSONArray().apply {
                put(
                    JSONObject()
                        .put("type", "deepseek_web_search")
                        .put("name", "deepseek_web_search")
                )
                put(
                    JSONObject()
                        .put("type", "deepseek_read_url")
                        .put("name", "deepseek_read_url")
                )
            }
            payload.put("tools", tools)
        }
        return payload
    }

    internal fun buildSystemPrompt(
        config: AiConfig,
        attachTrackContext: Boolean,
        track: Track?,
        lyricsSnippet: String?,
        attachLibraryCatalog: Boolean,
        librarySample: List<Track>,
        catalogLyricsByTrackId: Map<String, String> = emptyMap()
    ): String {
        val base = config.customSystemPrompt.trim().ifBlank {
            "你是 Auralis 私人黑胶唱片馆的资深声学工程师、音乐策展人与技术诊断助手。回答风格克制、专业、富有审美洞察，善用清晰的 Markdown（标题、加粗、列表、引用）排版。当用户向你反馈报错信息时，请精准定位原因并给出清晰可执行的排查步骤；当用户要求调音时，请在回复末尾单独附带一行 10 段均衡器 JSON（31Hz..16kHz）：EQ_JSON:{\"name\":\"...\",\"bands\":[0,0,0,0,0,0,0,0,0,0],\"bass\":20,\"virtualizer\":25}；当用户要求整理、推荐或创建歌单时，请在回复末尾单独附带一行标准歌单 JSON（tracks 数组填写用户本地音乐曲库中的准确曲名）：PLAYLIST_JSON:{\"name\":\"歌单名称\",\"description\":\"简短策展导语\",\"tracks\":[\"曲名1\",\"曲名2\"]}。"
        }
        val currentTrackInCatalog = attachLibraryCatalog &&
            track != null &&
            librarySample.any { it.id == track.id }
        val currentLyricsSentInCatalog = currentTrackInCatalog &&
            attachTrackContext &&
            (!catalogLyricsByTrackId[track!!.id].isNullOrBlank() || !lyricsSnippet.isNullOrBlank())

        return buildString {
            appendLine(base)
            if (config.enableWebSearch) {
                appendLine("已为你开启原生联网搜索能力（deepseek_web_search / deepseek_read_url），支持边思考边搜索。当需要查证专辑发行背景、歌手资料、歌词出处或最新声学信息时，请直接调用搜索工具完成检索后再给出完整分析，不要仅说出“我去查证一下”就结束回复。")
            }
            if (attachLibraryCatalog && librarySample.isNotEmpty()) {
                appendLine()
                appendLine("【用户附带的本地音乐曲库清单（共 ${librarySample.size} 首，已过滤非音乐普通音频）】")
                librarySample.take(150).forEach { t ->
                    val isPlayingHere = attachTrackContext && track != null && t.id == track.id
                    val playingTag = if (isPlayingHere) " 【当前正在播放】" else ""
                    appendLine("- ${t.title} — ${t.artist} [${t.album}] (${t.technicalSpecLine})$playingTag")
                    val attachedLyric = when {
                        !catalogLyricsByTrackId[t.id].isNullOrBlank() -> catalogLyricsByTrackId[t.id]
                        isPlayingHere && !lyricsSnippet.isNullOrBlank() -> lyricsSnippet
                        else -> null
                    }
                    if (!attachedLyric.isNullOrBlank()) {
                        appendLine("  歌词内容：")
                        appendLine("  ${attachedLyric.take(900).replace("\n", "\n  ")}")
                    }
                }
                appendLine("若用户请你筛选、编排或整理歌单，请优先从上述真实曲库挑选，并在末尾附带 PLAYLIST_JSON:{\"name\":\"...\",\"description\":\"...\",\"tracks\":[...]}。")
            }
            if (attachTrackContext && track != null) {
                appendLine()
                appendLine("【当前正在播放的曲目上下文】")
                appendLine("曲名：${track.title} | 艺术家：${track.artist} | 专辑：${track.album} (${if (track.year > 0) track.year else "未知年份"})")
                appendLine("风格：${track.genre.ifBlank { "未标注" }} | 音质规格：${track.technicalSpecLine}")
                if (!lyricsSnippet.isNullOrBlank() && !currentLyricsSentInCatalog) {
                    appendLine("歌词参考：")
                    appendLine(lyricsSnippet.take(1100))
                }
            }
        }.trim()
    }

    private fun extractReasoningField(obj: JSONObject): String? {
        for (key in listOf("reasoning_content", "reasoning", "thinking")) {
            val v = obj.optString(key, "")
            if (v.isNotEmpty() && v != "null") return v
        }
        return null
    }

    private fun drainThinkTags(
        buffer: StringBuilder,
        inThink: Boolean,
        onThink: (String) -> Unit,
        onText: (String) -> Unit
    ): Boolean {
        var inside = inThink
        while (buffer.isNotEmpty()) {
            if (!inside) {
                val startIdx = buffer.indexOf("<think>")
                if (startIdx >= 0) {
                    if (startIdx > 0) {
                        onText(buffer.substring(0, startIdx))
                    }
                    buffer.delete(0, startIdx + "<think>".length)
                    inside = true
                } else {
                    val safeLen = (buffer.length - 6).coerceAtLeast(0)
                    if (safeLen > 0) {
                        onText(buffer.substring(0, safeLen))
                        buffer.delete(0, safeLen)
                    }
                    break
                }
            } else {
                val endIdx = buffer.indexOf("</think>")
                if (endIdx >= 0) {
                    if (endIdx > 0) {
                        onThink(buffer.substring(0, endIdx))
                    }
                    buffer.delete(0, endIdx + "</think>".length)
                    inside = false
                } else {
                    val safeLen = (buffer.length - 7).coerceAtLeast(0)
                    if (safeLen > 0) {
                        onThink(buffer.substring(0, safeLen))
                        buffer.delete(0, safeLen)
                    }
                    break
                }
            }
        }
        return inside
    }

    private fun splitInlineThinkBlock(raw: String): Pair<String, String> {
        val regex = Regex("""<think>(.*?)</think>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val matches = regex.findAll(raw).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
        if (matches.isEmpty()) return "" to raw
        val cleaned = raw.replace(regex, "").trim()
        return matches.joinToString("\n") to cleaned
    }

    private fun extractServerErrorMessage(rawBody: String): String? {
        if (rawBody.isBlank()) return null
        return runCatching {
            val root = JSONObject(rawBody)
            root.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: root.optString("message").takeIf { it.isNotBlank() }
                ?: root.optString("detail").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun extractEqJsonRaw(text: String): String? {
        val markerIdx = listOf("EQ_JSON:", "EQ_JSON", "eq_json:")
            .map { text.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull() ?: return null
        val slice = text.substring(markerIdx)
        val jsonStr = extractBalancedJsonObject(slice) ?: return null
        return if (parseEqPreset(jsonStr) != null) jsonStr else null
    }

    private fun extractPlaylistJsonRaw(text: String): String? {
        val markerIdx = listOf("PLAYLIST_JSON:", "PLAYLIST_JSON", "playlist_json:")
            .map { text.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull() ?: return null
        val slice = text.substring(markerIdx)
        val jsonStr = extractBalancedJsonObject(slice) ?: return null
        return if (parseSuggestedPlaylist(jsonStr) != null) jsonStr else null
    }

    private fun extractBalancedJsonObject(slice: String): String? {
        val start = slice.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until slice.length) {
            val ch = slice[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\' && inString) {
                escaped = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (ch == '{') depth++
                else if (ch == '}') {
                    depth--
                    if (depth == 0) {
                        return slice.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun extractCuratedTitles(text: String, library: List<Track>): List<String> {
        return library.filter { track ->
            track.title.length >= 2 && text.contains(track.title, ignoreCase = true)
        }.map { it.title }.distinct().take(16)
    }

    private fun encodeArtworkBase64(artworkPath: String?): String? {
        if (artworkPath.isNullOrBlank()) return null
        val file = File(artworkPath)
        if (!file.exists()) return null
        return runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(artworkPath, opts)
            val maxDim = maxOf(opts.outWidth, opts.outHeight).coerceAtLeast(1)
            val sample = (maxDim / 384).coerceAtLeast(1)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(artworkPath, decodeOpts) ?: return null
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 78, out)
            bmp.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    companion object {
        fun parseEqPreset(jsonStr: String?): EqPreset? {
            if (jsonStr.isNullOrBlank()) return null
            return runCatching {
                val obj = JSONObject(jsonStr)
                val bandsArr = obj.getJSONArray("bands")
                val rawBands = (0 until bandsArr.length()).map { i ->
                    bandsArr.optDouble(i, 0.0).toFloat().coerceIn(-12f, 12f)
                }
                if (rawBands.isEmpty()) return null
                val normalized = com.example.model.normalizeEqBands(rawBands)
                EqPreset(
                    id = "ai_${System.currentTimeMillis()}",
                    name = obj.optString("name", "AI 定制声场").ifBlank { "AI 定制声场" },
                    bandLevelsDb = normalized,
                    bassBoostPercent = obj.optInt("bass", 20).coerceIn(0, 100),
                    virtualizerPercent = obj.optInt("virtualizer", 25).coerceIn(0, 100),
                    isCustom = true
                )
            }.getOrNull()
        }

        fun parseSuggestedPlaylist(jsonStr: String?): AiSuggestedPlaylist? {
            if (jsonStr.isNullOrBlank()) return null
            return runCatching {
                val obj = JSONObject(jsonStr)
                val tracksArr = obj.optJSONArray("tracks") ?: return null
                val titles = (0 until tracksArr.length())
                    .mapNotNull { idx -> tracksArr.optString(idx, "").trim().takeIf { it.isNotEmpty() } }
                    .distinct()
                if (titles.isEmpty()) return null
                AiSuggestedPlaylist(
                    name = obj.optString("name", "AI 策展歌单").trim().ifBlank { "AI 策展歌单" },
                    description = obj.optString("description", "").trim(),
                    trackTitles = titles
                )
            }.getOrNull()
        }
    }
}
