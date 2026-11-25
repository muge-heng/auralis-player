package com.example.data.ai

import com.example.model.AiChatMessageEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

internal data class WebCitation(
    val title: String,
    val url: String
)

internal data class AnthropicClientToolCall(
    val id: String,
    val name: String,
    val input: JSONObject
)

internal data class OpenAiToolCallAccumulator(
    var id: String = "",
    var type: String = "function",
    var name: String = "",
    val arguments: StringBuilder = StringBuilder()
)

internal data class ToolExecutionOutcome(
    val payloadForModel: String,
    val citations: List<WebCitation> = emptyList(),
    val summaryForTrace: String? = null
)

internal interface AnthropicStreamCallbacks {
    fun onReasoningDelta(delta: String)
    fun onContentDelta(delta: String)
    fun onWebSearchStatus(searching: Boolean, label: String)
}

internal object DeepSeekEndpointResolver {

    /**
     * DeepSeek's official open-source chat-demo uses `${origin}/anthropic/v1/messages`
     * to support native interleaved Deep Thinking Search (`thinking` + `server_tool_use`
     * + `web_search_tool_result` + `pause_turn` + `citations_delta`).
     *
     * Use Anthropic Messages API whenever targeting official `api.deepseek.com` (unless
     * the user explicitly typed `/chat/completions`) or any URL explicitly containing
     * `/anthropic` or ending with `/messages`.
     */
    fun shouldUseAnthropicMessagesApi(baseUrl: String): Boolean {
        val clean = baseUrl.trim().trimEnd('/').lowercase()
        if (clean.isEmpty()) return true
        if (clean.endsWith("/chat/completions")) return false
        if (clean.contains("/anthropic") || clean.endsWith("/messages")) return true
        val host = runCatching { URI(clean).host?.lowercase().orEmpty() }.getOrDefault("")
        return host == "api.deepseek.com" || host.endsWith(".deepseek.com")
    }

    fun resolveAnthropicMessagesEndpoint(baseUrl: String): String {
        val clean = baseUrl.trim().trimEnd('/').ifBlank { "https://api.deepseek.com" }
        val lower = clean.lowercase()
        return when {
            lower.endsWith("/messages") -> clean
            lower.endsWith("/anthropic/v1") -> "$clean/messages"
            lower.endsWith("/anthropic") -> "$clean/v1/messages"
            lower.endsWith("/v1") -> "${clean.dropLast(3)}/anthropic/v1/messages"
            else -> "$clean/anthropic/v1/messages"
        }
    }

    fun resolveOpenAiChatEndpoint(baseUrl: String): String {
        val clean = baseUrl.trim().trimEnd('/').ifBlank { "https://api.deepseek.com" }
        val lower = clean.lowercase()
        return when {
            lower.endsWith("/chat/completions") -> clean
            lower.contains("/anthropic") -> "${extractOrigin(clean)}/chat/completions"
            lower.endsWith("/messages") -> "${extractOrigin(clean)}/chat/completions"
            else -> "$clean/chat/completions"
        }
    }

    fun extractOrigin(baseUrl: String): String {
        val clean = baseUrl.trim().trimEnd('/').ifBlank { "https://api.deepseek.com" }
        return runCatching {
            val uri = URI(clean)
            if (uri.scheme != null && uri.host != null) {
                val portPart = if (uri.port != -1) ":${uri.port}" else ""
                "${uri.scheme}://${uri.host}$portPart"
            } else {
                "https://api.deepseek.com"
            }
        }.getOrDefault("https://api.deepseek.com")
    }
}

internal fun buildAnthropicMessagesArray(
    history: List<AiChatMessageEntity>,
    currentUserText: String,
    coverBase64: String?
): JSONArray {
    // Anthropic Messages API requires alternating user/assistant turns starting with user.
    data class NormalizedTurn(val role: String, val text: String)

    val merged = mutableListOf<NormalizedTurn>()
    history
        .filter { it.role == "user" || it.role == "assistant" }
        .takeLast(24)
        .forEach { msg ->
            val cleaned = msg.content.trim()
            if (cleaned.isEmpty()) return@forEach
            if (merged.isEmpty() && msg.role != "user") return@forEach
            val last = merged.lastOrNull()
            if (last != null && last.role == msg.role) {
                merged[merged.lastIndex] = last.copy(text = "${last.text}\n\n$cleaned")
            } else {
                merged += NormalizedTurn(msg.role, cleaned)
            }
        }

    val result = JSONArray()
    val pendingUserPrefix = if (merged.lastOrNull()?.role == "user") {
        val prev = merged.removeAt(merged.lastIndex)
        "${prev.text}\n\n"
    } else {
        ""
    }

    merged.forEach { turn ->
        result.put(JSONObject().put("role", turn.role).put("content", turn.text))
    }

    val combinedCurrentText = (pendingUserPrefix + currentUserText).trim()
    val currentUserObj = JSONObject().put("role", "user")
    if (coverBase64 != null) {
        val blocks = JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", combinedCurrentText))
            put(
                JSONObject()
                    .put("type", "image")
                    .put(
                        "source",
                        JSONObject()
                            .put("type", "base64")
                            .put("media_type", "image/jpeg")
                            .put("data", coverBase64)
                    )
            )
        }
        currentUserObj.put("content", blocks)
    } else {
        currentUserObj.put("content", combinedCurrentText)
    }
    result.put(currentUserObj)
    return result
}

internal class AnthropicTurnAccumulator(
    private val hasExistingReasoning: () -> Boolean,
    private val hasExistingContent: () -> Boolean
) {
    private class BlockState(
        val type: String,
        val id: String = "",
        val name: String = "",
        val text: StringBuilder = StringBuilder(),
        val thinking: StringBuilder = StringBuilder(),
        val signature: StringBuilder = StringBuilder(),
        val inputJson: StringBuilder = StringBuilder(),
        var initialInput: JSONObject? = null,
        var rawBlock: JSONObject? = null,
        var traceEmitted: Boolean = false
    )

    private val blocks = linkedMapOf<Int, BlockState>()
    var stopReason: String? = null
        private set
    var streamError: String? = null
        private set
    val citations = linkedMapOf<String, WebCitation>()

    fun processSseEvent(
        eventType: String?,
        payload: JSONObject,
        callbacks: AnthropicStreamCallbacks
    ) {
        val type = payload.optString("type").ifBlank { eventType.orEmpty() }
        when (type) {
            "error" -> {
                val errObj = payload.optJSONObject("error")
                streamError = errObj?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: payload.optString("message").takeIf { it.isNotBlank() }
                    ?: payload.toString()
            }

            "content_block_start" -> {
                val index = payload.optInt("index", blocks.size)
                val cb = payload.optJSONObject("content_block") ?: return
                val blockType = cb.optString("type")
                when {
                    blockType == "thinking" -> {
                        callbacks.onWebSearchStatus(false, "")
                        val state = BlockState(type = "thinking")
                        blocks[index] = state
                        if (hasExistingReasoning()) {
                            callbacks.onReasoningDelta("\n\n")
                        }
                        val initialThink = cb.optString("thinking", "")
                        if (initialThink.isNotEmpty() && initialThink != "null") {
                            state.thinking.append(initialThink)
                            callbacks.onReasoningDelta(initialThink)
                        }
                        val initialSig = cb.optString("signature", "")
                        if (initialSig.isNotEmpty() && initialSig != "null") {
                            state.signature.append(initialSig)
                        }
                    }

                    blockType == "text" -> {
                        callbacks.onWebSearchStatus(false, "")
                        val state = BlockState(type = "text")
                        blocks[index] = state
                        if (hasExistingContent()) {
                            callbacks.onContentDelta("\n\n")
                        }
                        val initialText = cb.optString("text", "")
                        if (initialText.isNotEmpty() && initialText != "null") {
                            state.text.append(initialText)
                            callbacks.onContentDelta(initialText)
                        }
                    }

                    blockType == "server_tool_use" || blockType == "tool_use" -> {
                        val name = cb.optString("name", "")
                        val id = cb.optString("id", "")
                        val initialInput = cb.optJSONObject("input")
                        val state = BlockState(
                            type = blockType,
                            id = id,
                            name = name,
                            initialInput = initialInput
                        )
                        blocks[index] = state
                        val statusLabel = toolStartLabel(name, initialInput)
                        callbacks.onWebSearchStatus(true, statusLabel)
                        if (initialInput != null && initialInput.length() > 0) {
                            emitToolUseTrace(state, initialInput, callbacks)
                        }
                    }

                    blockType.endsWith("_tool_result") || blockType == "tool_result" -> {
                        val state = BlockState(type = blockType, rawBlock = cb)
                        blocks[index] = state
                        handleServerToolResultBlock(cb, callbacks)
                    }

                    else -> {
                        blocks[index] = BlockState(type = blockType, rawBlock = cb)
                    }
                }
            }

            "content_block_delta" -> {
                val index = payload.optInt("index", blocks.keys.lastOrNull() ?: 0)
                val delta = payload.optJSONObject("delta") ?: return
                val deltaType = delta.optString("type")
                val state = blocks[index]
                when (deltaType) {
                    "thinking_delta" -> {
                        val chunk = delta.optString("thinking", "")
                        if (chunk.isNotEmpty() && chunk != "null") {
                            callbacks.onWebSearchStatus(false, "")
                            state?.thinking?.append(chunk)
                            callbacks.onReasoningDelta(chunk)
                        }
                    }

                    "signature_delta" -> {
                        val sig = delta.optString("signature", "")
                        if (sig.isNotEmpty() && sig != "null") {
                            state?.signature?.append(sig)
                        }
                    }

                    "text_delta" -> {
                        val chunk = delta.optString("text", "")
                        if (chunk.isNotEmpty() && chunk != "null") {
                            callbacks.onWebSearchStatus(false, "")
                            state?.text?.append(chunk)
                            callbacks.onContentDelta(chunk)
                        }
                    }

                    "input_json_delta" -> {
                        val partial = delta.optString("partial_json", "")
                        if (partial.isNotEmpty() && partial != "null") {
                            state?.inputJson?.append(partial)
                        }
                    }

                    "citations_delta" -> {
                        val citation = delta.optJSONObject("citation")
                        if (citation != null) {
                            registerCitation(
                                title = citation.optString("title", ""),
                                url = citation.optString("url", "")
                            )
                        }
                    }
                }
            }

            "content_block_stop" -> {
                val index = payload.optInt("index", -1)
                val state = blocks[index] ?: return
                if ((state.type == "server_tool_use" || state.type == "tool_use") && !state.traceEmitted) {
                    val parsedInput = parseToolInput(state)
                    val label = toolStartLabel(state.name, parsedInput)
                    callbacks.onWebSearchStatus(true, label)
                    emitToolUseTrace(state, parsedInput, callbacks)
                }
            }

            "message_delta" -> {
                val delta = payload.optJSONObject("delta")
                val reason = delta?.optString("stop_reason", "")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                if (reason != null) {
                    stopReason = reason
                }
            }
        }
    }

    fun processNonStreamingMessage(
        root: JSONObject,
        callbacks: AnthropicStreamCallbacks
    ) {
        val reason = root.optString("stop_reason", "")
            .takeIf { it.isNotBlank() && it != "null" }
        if (reason != null) stopReason = reason

        val contentArr = root.optJSONArray("content") ?: return
        for (i in 0 until contentArr.length()) {
            val cb = contentArr.optJSONObject(i) ?: continue
            val blockType = cb.optString("type")
            when {
                blockType == "thinking" -> {
                    val think = cb.optString("thinking", "")
                    val sig = cb.optString("signature", "")
                    val state = BlockState(type = "thinking")
                    state.thinking.append(think)
                    state.signature.append(sig)
                    blocks[i] = state
                    if (think.isNotBlank()) {
                        if (hasExistingReasoning()) callbacks.onReasoningDelta("\n\n")
                        callbacks.onReasoningDelta(think)
                    }
                }

                blockType == "server_tool_use" || blockType == "tool_use" -> {
                    val inputObj = cb.optJSONObject("input") ?: JSONObject()
                    val state = BlockState(
                        type = blockType,
                        id = cb.optString("id", ""),
                        name = cb.optString("name", ""),
                        initialInput = inputObj
                    )
                    blocks[i] = state
                    emitToolUseTrace(state, inputObj, callbacks)
                }

                blockType.endsWith("_tool_result") || blockType == "tool_result" -> {
                    blocks[i] = BlockState(type = blockType, rawBlock = cb)
                    handleServerToolResultBlock(cb, callbacks)
                }

                blockType == "text" -> {
                    val txt = cb.optString("text", "")
                    val state = BlockState(type = "text")
                    state.text.append(txt)
                    blocks[i] = state
                    val citesArr = cb.optJSONArray("citations")
                    if (citesArr != null) {
                        for (c in 0 until citesArr.length()) {
                            val co = citesArr.optJSONObject(c) ?: continue
                            registerCitation(co.optString("title", ""), co.optString("url", ""))
                        }
                    }
                    if (txt.isNotBlank()) {
                        if (hasExistingContent()) callbacks.onContentDelta("\n\n")
                        callbacks.onContentDelta(txt)
                    }
                }

                else -> {
                    blocks[i] = BlockState(type = blockType, rawBlock = cb)
                }
            }
        }
    }

    fun toAssistantContentBlocks(): JSONArray {
        val arr = JSONArray()
        blocks.toSortedMap().values.forEach { state ->
            when {
                state.type == "thinking" -> {
                    val obj = JSONObject()
                        .put("type", "thinking")
                        .put("thinking", state.thinking.toString())
                    if (state.signature.isNotEmpty()) {
                        obj.put("signature", state.signature.toString())
                    }
                    arr.put(obj)
                }

                state.type == "text" -> {
                    val txt = state.text.toString()
                    if (txt.isNotEmpty()) {
                        arr.put(JSONObject().put("type", "text").put("text", txt))
                    }
                }

                state.type == "server_tool_use" || state.type == "tool_use" -> {
                    arr.put(
                        JSONObject()
                            .put("type", state.type)
                            .put("id", state.id)
                            .put("name", state.name)
                            .put("input", parseToolInput(state))
                    )
                }

                state.rawBlock != null -> {
                    arr.put(state.rawBlock)
                }
            }
        }
        return arr
    }

    fun extractClientToolUses(): List<AnthropicClientToolCall> {
        return blocks.toSortedMap().values
            .filter { it.type == "tool_use" && it.id.isNotBlank() }
            .map { state ->
                AnthropicClientToolCall(
                    id = state.id,
                    name = state.name,
                    input = parseToolInput(state)
                )
            }
    }

    private fun parseToolInput(state: BlockState): JSONObject {
        val raw = state.inputJson.toString().trim()
        if (raw.isNotEmpty()) {
            val parsed = runCatching { JSONObject(raw) }.getOrNull()
            if (parsed != null) return parsed
        }
        return state.initialInput ?: JSONObject()
    }

    private fun emitToolUseTrace(
        state: BlockState,
        input: JSONObject,
        callbacks: AnthropicStreamCallbacks
    ) {
        state.traceEmitted = true
        val queryDesc = extractQuerySummary(input)
        val urlDesc = extractUrlSummary(input)
        val isUrlTarget = !urlDesc.isNullOrBlank() ||
            (!queryDesc.isNullOrBlank() && isHttpUrl(queryDesc))
        val isReadAction = state.name.contains("read_url", ignoreCase = true) ||
            state.name.contains("fetch", ignoreCase = true) ||
            state.name.contains("browse", ignoreCase = true) ||
            (isUrlTarget && (queryDesc.isNullOrBlank() || isHttpUrl(queryDesc)))
        val prefix = if (hasExistingReasoning()) "\n\n" else ""
        val traceLine = when {
            isReadAction -> {
                val rawTarget = urlDesc ?: queryDesc ?: "目标网页"
                val readable = formatReadableUrl(rawTarget)
                "$prefix> [[FETCH]] **读取网页**：$readable\n"
            }

            state.name.contains("search", ignoreCase = true) -> {
                val target = queryDesc ?: urlDesc?.let(::formatReadableUrl) ?: "实时网络检索"
                "$prefix> [[SEARCH]] **联网搜索**：$target\n"
            }

            else -> {
                val target = queryDesc ?: urlDesc?.let(::formatReadableUrl) ?: state.name
                "$prefix> [[TOOL]] **调用工具 (${state.name})**：$target\n"
            }
        }
        callbacks.onReasoningDelta(traceLine)
    }

    private fun handleServerToolResultBlock(
        cb: JSONObject,
        callbacks: AnthropicStreamCallbacks
    ) {
        val found = extractCitationsFromAnyJson(cb.opt("content"))
        found.forEach { registerCitation(it.title, it.url) }
        val blockType = cb.optString("type", "")
        val prefix = if (hasExistingReasoning()) "\n" else ""
        if (found.isNotEmpty()) {
            val topTitles = found.take(3).map { it.title.take(28) }.filter { it.isNotBlank() }
            val preview = if (topTitles.isNotEmpty()) "（${topTitles.joinToString(" · ")}）" else ""
            callbacks.onReasoningDelta("$prefix> [[RESULT]] **已检索到 ${found.size} 条网页资料**$preview\n")
            callbacks.onWebSearchStatus(true, "已检索到 ${found.size} 条资料，正在整理分析...")
        } else if (blockType.contains("fetch", ignoreCase = true) || blockType.contains("read_url", ignoreCase = true)) {
            callbacks.onReasoningDelta("$prefix> [[FETCH]] **网页正文读取完成**\n")
            callbacks.onWebSearchStatus(true, "网页读取完成，正在结合曲库思考...")
        }
    }

    private fun registerCitation(title: String, url: String) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) return
        val cleanTitle = title.trim().ifBlank {
            runCatching { URI(cleanUrl).host ?: cleanUrl }.getOrDefault(cleanUrl)
        }
        if (!citations.containsKey(cleanUrl)) {
            citations[cleanUrl] = WebCitation(title = cleanTitle, url = cleanUrl)
        }
    }

    private fun toolStartLabel(name: String, input: JSONObject?): String {
        val q = input?.let { extractQuerySummary(it) }
        val u = input?.let { extractUrlSummary(it) }
        val isUrlRead = name.contains("read_url", ignoreCase = true) ||
            name.contains("fetch", ignoreCase = true) ||
            (!u.isNullOrBlank() && (q.isNullOrBlank() || isHttpUrl(q))) ||
            (!q.isNullOrBlank() && isHttpUrl(q))
        return when {
            isUrlRead -> {
                val targetUrl = u ?: q
                val host = targetUrl?.let { runCatching { URI(it).host }.getOrNull() ?: formatReadableUrl(it).take(32) }
                if (!host.isNullOrBlank()) "正在读取网页：$host" else "正在读取网页内容"
            }

            name.contains("search", ignoreCase = true) -> {
                if (!q.isNullOrBlank()) "正在联网搜索：「${q.take(26)}」" else "正在联网检索资料"
            }

            else -> "正在执行工具：$name"
        }
    }
}

internal object DeepSeekStandaloneTools {

    fun executeToolCall(
        client: OkHttpClient,
        origin: String,
        apiKey: String,
        toolName: String,
        arguments: JSONObject,
        originalQuestion: String?
    ): ToolExecutionOutcome {
        return when {
            toolName.contains("read_url", ignoreCase = true) ||
                toolName.contains("fetch", ignoreCase = true) -> {
                val url = extractUrlSummary(arguments).orEmpty()
                if (url.isBlank()) {
                    ToolExecutionOutcome(
                        payloadForModel = JSONObject().put("error", "Missing url parameter").toString()
                    )
                } else {
                    executeFetch(client, origin, apiKey, url)
                }
            }

            toolName.contains("search", ignoreCase = true) -> {
                val query = extractQuerySummary(arguments).orEmpty()
                if (query.isBlank()) {
                    ToolExecutionOutcome(
                        payloadForModel = JSONObject().put("error", "Missing query parameter").toString()
                    )
                } else {
                    executeSearch(client, origin, apiKey, query, originalQuestion)
                }
            }

            else -> ToolExecutionOutcome(
                payloadForModel = JSONObject()
                    .put("error", "Unsupported client tool: $toolName")
                    .toString()
            )
        }
    }

    private fun executeSearch(
        client: OkHttpClient,
        origin: String,
        apiKey: String,
        query: String,
        originalQuestion: String?
    ): ToolExecutionOutcome {
        val bodyObj = JSONObject()
            .put("query", query.take(128))
            .put("count", 8)
            .put("need_content", false)
        if (!originalQuestion.isNullOrBlank()) {
            bodyObj.put("ori_question", originalQuestion.take(300))
        }
        val req = Request.Builder()
            .url("$origin/v1/tools/search")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(bodyObj.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return runCatching {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use ToolExecutionOutcome(
                        payloadForModel = JSONObject()
                            .put("error", "Search API HTTP ${resp.code}: ${raw.take(400)}")
                            .toString(),
                        summaryForTrace = "搜索请求返回 HTTP ${resp.code}"
                    )
                }
                val parsed = runCatching { JSONObject(raw) }.getOrNull()
                val citations = extractCitationsFromAnyJson(parsed)
                ToolExecutionOutcome(
                    payloadForModel = raw.take(16_000),
                    citations = citations,
                    summaryForTrace = if (citations.isNotEmpty()) {
                        "已检索到 ${citations.size} 条网页资料"
                    } else {
                        "搜索完成"
                    }
                )
            }
        }.getOrElse { err ->
            ToolExecutionOutcome(
                payloadForModel = JSONObject()
                    .put("error", "Search request exception: ${err.localizedMessage}")
                    .toString(),
                summaryForTrace = "联网搜索异常"
            )
        }
    }

    private fun executeFetch(
        client: OkHttpClient,
        origin: String,
        apiKey: String,
        url: String
    ): ToolExecutionOutcome {
        val bodyObj = JSONObject().put("url", url.take(8192))
        val req = Request.Builder()
            .url("$origin/v1/tools/fetch")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(bodyObj.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return runCatching {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use ToolExecutionOutcome(
                        payloadForModel = JSONObject()
                            .put("error", "Fetch API HTTP ${resp.code}: ${raw.take(400)}")
                            .toString(),
                        summaryForTrace = "网页读取返回 HTTP ${resp.code}"
                    )
                }
                val parsed = runCatching { JSONObject(raw) }.getOrNull()
                val title = parsed?.optString("title", "").orEmpty()
                val citation = WebCitation(
                    title = title.ifBlank { runCatching { URI(url).host ?: url }.getOrDefault(url) },
                    url = url
                )
                ToolExecutionOutcome(
                    payloadForModel = raw.take(16_000),
                    citations = listOf(citation),
                    summaryForTrace = "网页正文读取完成"
                )
            }
        }.getOrElse { err ->
            ToolExecutionOutcome(
                payloadForModel = JSONObject()
                    .put("error", "Fetch request exception: ${err.localizedMessage}")
                    .toString(),
                summaryForTrace = "网页读取异常"
            )
        }
    }
}

internal fun extractQuerySummary(input: JSONObject): String? {
    for (key in listOf("query", "q", "search_query", "keyword", "keywords")) {
        val str = input.optString(key, "").trim()
        if (str.isNotEmpty() && str != "null") return str
    }
    val queriesArr = input.optJSONArray("queries")
    if (queriesArr != null && queriesArr.length() > 0) {
        val items = (0 until queriesArr.length())
            .mapNotNull { i -> queriesArr.optString(i, "").trim().takeIf { it.isNotEmpty() } }
        if (items.isNotEmpty()) return items.joinToString(" / ")
    }
    return null
}

internal fun extractUrlSummary(input: JSONObject): String? {
    for (key in listOf("url", "link", "target_url", "uri")) {
        val str = input.optString(key, "").trim()
        if (str.isNotEmpty() && str != "null") return str
    }
    val urlsArr = input.optJSONArray("urls")
    if (urlsArr != null && urlsArr.length() > 0) {
        val items = (0 until urlsArr.length())
            .mapNotNull { i -> urlsArr.optString(i, "").trim().takeIf { it.isNotEmpty() } }
        if (items.isNotEmpty()) return items.first()
    }
    return null
}

internal fun extractCitationsFromAnyJson(node: Any?): List<WebCitation> {
    if (node == null) return emptyList()
    val results = linkedMapOf<String, WebCitation>()

    fun walk(current: Any?) {
        when (current) {
            is JSONArray -> {
                for (i in 0 until current.length()) {
                    walk(current.opt(i))
                }
            }

            is JSONObject -> {
                val url = current.optString("url", "")
                    .ifBlank { current.optString("link", "") }
                    .ifBlank { current.optString("href", "") }
                    .trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val title = current.optString("title", "")
                        .ifBlank { current.optString("site_name", "") }
                        .ifBlank { current.optString("name", "") }
                        .trim()
                        .ifBlank { runCatching { URI(url).host ?: url }.getOrDefault(url) }
                    if (!results.containsKey(url)) {
                        results[url] = WebCitation(title = title, url = url)
                    }
                }
                for (nestedKey in listOf("results", "items", "data", "pages", "content", "web_results", "citations")) {
                    val nested = current.opt(nestedKey)
                    if (nested is JSONArray || nested is JSONObject) {
                        walk(nested)
                    }
                }
            }
        }
    }

    walk(node)
    return results.values.toList()
}

internal fun isHttpUrl(value: String): Boolean {
    val trimmed = value.trim().lowercase()
    return trimmed.startsWith("http://") || trimmed.startsWith("https://")
}

internal fun formatReadableUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (!isHttpUrl(trimmed)) return trimmed
    return runCatching {
        val uri = URI(trimmed)
        val host = uri.host?.removePrefix("www.").orEmpty()
        val rawPath = uri.rawPath.orEmpty().trimEnd('/')
        val decodedPath = runCatching { URLDecoder.decode(rawPath, "UTF-8") }.getOrDefault(rawPath)
        val combined = if (decodedPath.isNotEmpty() && decodedPath != "/") {
            "$host$decodedPath"
        } else {
            host.ifBlank { trimmed }
        }
        if (combined.length > 78) combined.take(75) + "..." else combined
    }.getOrDefault(trimmed.take(78))
}

internal fun formatCitationsFooter(
    existingContent: String,
    citations: Collection<WebCitation>
): String {
    if (existingContent.isBlank() || citations.isEmpty()) return ""
    val fresh = citations
        .filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
        .distinctBy { it.url }
        .filterNot { existingContent.contains(it.url, ignoreCase = true) }
        .take(6)
    if (fresh.isEmpty()) return ""
    return buildString {
        append("\n\n---\n### 参考来源\n")
        fresh.forEach { cite ->
            val safeTitle = cite.title.replace("[", "「").replace("]", "」").take(64)
            appendLine("- [$safeTitle](${cite.url})")
        }
    }.trimEnd()
}
