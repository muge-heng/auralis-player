package com.example

import android.content.Context
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AnthropicStreamCallbacks
import com.example.data.ai.AnthropicTurnAccumulator
import com.example.data.ai.DeepSeekChatClient
import com.example.data.ai.DeepSeekEndpointResolver
import com.example.data.ai.buildAnthropicMessagesArray
import com.example.data.ai.formatCitationsFooter
import com.example.data.local.PreferencesRepository
import com.example.model.AiChatMessageEntity
import com.example.model.AiConfig
import com.example.model.DEFAULT_AI_MAX_OUTPUT_TOKENS
import com.example.model.EqPreset
import com.example.model.ReasoningEffortLevel
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Auralis", appName)
  }

  @Test
  fun `custom fonts load and resolve through Compose FontFamilyResolver`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertNotNull(ResourcesCompat.getFont(context, R.font.outfit))
    assertNotNull(ResourcesCompat.getFont(context, R.font.plus_jakarta_sans))
    assertNotNull(ResourcesCompat.getFont(context, R.font.jetbrains_mono))

    val resolver = createFontFamilyResolver(context)
    assertNotNull(resolver.resolve(OutfitFontFamily, FontWeight.Bold).value)
    assertNotNull(resolver.resolve(PlusJakartaSansFontFamily, FontWeight.Normal).value)
    assertNotNull(resolver.resolve(JetBrainsMonoFontFamily, FontWeight.Medium).value)
  }

  @Test
  fun `custom EQ presets JSON round-trip preserves 10 bands`() {
    val original = listOf(
      EqPreset(
        id = "custom_1",
        name = "深夜爵士暖声",
        bandLevelsDb = listOf(3.0f, 2.5f, 1.5f, 0.5f, 0.0f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f),
        bassBoostPercent = 35,
        virtualizerPercent = 40,
        isCustom = true
      )
    )
    val json = PreferencesRepository.serializeCustomPresetsJson(original)
    val decoded = PreferencesRepository.parseCustomPresetsJson(json)
    assertEquals(1, decoded.size)
    assertEquals("深夜爵士暖声", decoded.first().name)
    assertEquals(10, decoded.first().bandLevelsDb.size)
    assertEquals(35, decoded.first().bassBoostPercent)
    assertEquals(40, decoded.first().virtualizerPercent)
    assertTrue(decoded.first().isCustom)
  }

  @Test
  fun `AI EQ parser handles 10-band and legacy 5-band JSON`() {
    val tenBandJson = """{"name":"流光监听","bands":[3.5,2.5,1.0,0.0,-0.5,0.5,1.5,2.5,3.0,3.5],"bass":32,"virtualizer":42}"""
    val parsedTen = DeepSeekChatClient.parseEqPreset(tenBandJson)
    assertNotNull(parsedTen)
    assertEquals("流光监听", parsedTen!!.name)
    assertEquals(10, parsedTen.bandLevelsDb.size)
    assertEquals(32, parsedTen.bassBoostPercent)
    assertEquals(42, parsedTen.virtualizerPercent)

    val fiveBandJson = """{"name":"经典人声","bands":[-1,0,3,2,1],"bass":10,"virtualizer":20}"""
    val parsedFive = DeepSeekChatClient.parseEqPreset(fiveBandJson)
    assertNotNull(parsedFive)
    assertEquals(10, parsedFive!!.bandLevelsDb.size)
  }

  @Test
  fun `AI playlist parser extracts structured PLAYLIST_JSON cleanly`() {
    val playlistJson = """{"name":"深夜温柔巡航","description":"适合雨夜独处的细腻人声与原声吉他","tracks":["暖暖","晴天","夜曲"]}"""
    val parsed = DeepSeekChatClient.parseSuggestedPlaylist(playlistJson)
    assertNotNull(parsed)
    assertEquals("深夜温柔巡航", parsed!!.name)
    assertEquals("适合雨夜独处的细腻人声与原声吉他", parsed.description)
    assertEquals(listOf("暖暖", "晴天", "夜曲"), parsed.trackTitles)
  }

  @Test
  fun `DeepSeek payload includes native web search tools without tool_choice and 384K max_tokens`() {
    val client = DeepSeekChatClient()
    val payload = client.buildRequestPayload(
      modelId = "deepseek-flash",
      messages = JSONArray(),
      config = AiConfig(
        enableWebSearch = true,
        maxOutputTokens = DEFAULT_AI_MAX_OUTPUT_TOKENS,
        enableStreaming = true
      ),
      effectiveEffort = ReasoningEffortLevel.HIGH
    )
    assertEquals(393_216, payload.getInt("max_tokens"))
    assertEquals("high", payload.getString("reasoning_effort"))
    assertEquals("enabled", payload.getJSONObject("thinking").getString("type"))
    assertFalse(payload.has("tool_choice"))
    val tools = payload.getJSONArray("tools")
    assertEquals(2, tools.length())
    assertEquals("deepseek_web_search", tools.getJSONObject(0).getString("type"))
    assertEquals("deepseek_read_url", tools.getJSONObject(1).getString("type"))

    val noSearchPayload = client.buildRequestPayload(
      modelId = "deepseek-flash",
      messages = JSONArray(),
      config = AiConfig(enableWebSearch = false, maxOutputTokens = 65_536),
      effectiveEffort = ReasoningEffortLevel.OFF
    )
    assertEquals(65_536, noSearchPayload.getInt("max_tokens"))
    assertEquals("disabled", noSearchPayload.getJSONObject("thinking").getString("type"))
    assertFalse(noSearchPayload.has("tools"))
    assertFalse(noSearchPayload.has("reasoning_effort"))
  }

  @Test
  fun `DeepSeek official endpoint routes to Anthropic Messages API like chat-demo`() {
    assertTrue(DeepSeekEndpointResolver.shouldUseAnthropicMessagesApi("https://api.deepseek.com"))
    assertTrue(DeepSeekEndpointResolver.shouldUseAnthropicMessagesApi("https://api.deepseek.com/v1"))
    assertTrue(DeepSeekEndpointResolver.shouldUseAnthropicMessagesApi("https://api.deepseek.com/anthropic"))
    assertFalse(DeepSeekEndpointResolver.shouldUseAnthropicMessagesApi("https://api.deepseek.com/chat/completions"))
    assertFalse(DeepSeekEndpointResolver.shouldUseAnthropicMessagesApi("https://openrouter.ai/api/v1"))

    assertEquals(
      "https://api.deepseek.com/anthropic/v1/messages",
      DeepSeekEndpointResolver.resolveAnthropicMessagesEndpoint("https://api.deepseek.com")
    )
    assertEquals(
      "https://api.deepseek.com/anthropic/v1/messages",
      DeepSeekEndpointResolver.resolveAnthropicMessagesEndpoint("https://api.deepseek.com/v1")
    )
    assertEquals(
      "https://api.deepseek.com/anthropic/v1/messages",
      DeepSeekEndpointResolver.resolveAnthropicMessagesEndpoint("https://api.deepseek.com/anthropic")
    )
  }

  @Test
  fun `Anthropic Messages payload configures thinking, output_config effort, tools, and alternating messages`() {
    val client = DeepSeekChatClient()
    val history = listOf(
      AiChatMessageEntity(sessionId = 1L, role = "assistant", content = "开场白"),
      AiChatMessageEntity(sessionId = 1L, role = "user", content = "第一句"),
      AiChatMessageEntity(sessionId = 1L, role = "user", content = "补充一句")
    )
    val messages = buildAnthropicMessagesArray(history, "请分析星瞳的歌", coverBase64 = "QUJD")
    assertEquals(1, messages.length())
    assertEquals("user", messages.getJSONObject(0).getString("role"))
    val contentBlocks = messages.getJSONObject(0).getJSONArray("content")
    assertEquals(2, contentBlocks.length())
    assertTrue(contentBlocks.getJSONObject(0).getString("text").contains("补充一句"))
    assertEquals("image", contentBlocks.getJSONObject(1).getString("type"))

    val payload = client.buildAnthropicRequestPayload(
      modelId = "deepseek-flash",
      systemPrompt = "SYSTEM",
      messages = messages,
      config = AiConfig(enableWebSearch = true, maxOutputTokens = DEFAULT_AI_MAX_OUTPUT_TOKENS),
      effectiveEffort = ReasoningEffortLevel.MAX
    )
    assertEquals("deepseek-flash", payload.getString("model"))
    assertEquals("SYSTEM", payload.getString("system"))
    assertEquals(393_216, payload.getInt("max_tokens"))
    assertEquals("enabled", payload.getJSONObject("thinking").getString("type"))
    assertEquals("max", payload.getJSONObject("output_config").getString("effort"))
    assertEquals(2, payload.getJSONArray("tools").length())
  }

  @Test
  fun `AnthropicTurnAccumulator parses interleaved Deep Thinking Search stream and preserves pause_turn blocks`() {
    val reasoning = StringBuilder()
    val content = StringBuilder()
    val searchEvents = mutableListOf<Pair<Boolean, String>>()

    val acc = AnthropicTurnAccumulator(
      hasExistingReasoning = { reasoning.isNotEmpty() },
      hasExistingContent = { content.isNotEmpty() }
    )
    val callbacks = object : AnthropicStreamCallbacks {
      override fun onReasoningDelta(delta: String) {
        reasoning.append(delta)
      }

      override fun onContentDelta(delta: String) {
        content.append(delta)
      }

      override fun onWebSearchStatus(searching: Boolean, label: String) {
        searchEvents += (searching to label)
      }
    }

    // 1. Initial thinking block
    acc.processSseEvent(
      "content_block_start",
      JSONObject("""{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}"""),
      callbacks
    )
    acc.processSseEvent(
      "content_block_delta",
      JSONObject("""{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"让我先搜索星瞳的资料。"}}"""),
      callbacks
    )
    acc.processSseEvent(
      "content_block_delta",
      JSONObject("""{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"sig_abc123"}}"""),
      callbacks
    )
    acc.processSseEvent("content_block_stop", JSONObject("""{"type":"content_block_stop","index":0}"""), callbacks)

    // 2. Server-side deepseek_web_search tool use
    acc.processSseEvent(
      "content_block_start",
      JSONObject("""{"type":"content_block_start","index":1,"content_block":{"type":"server_tool_use","id":"srv_1","name":"deepseek_web_search","input":{}}}"""),
      callbacks
    )
    acc.processSseEvent(
      "content_block_delta",
      JSONObject("""{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"query\":\"星瞳 虚拟歌手 专辑\"}"}}"""),
      callbacks
    )
    acc.processSseEvent("content_block_stop", JSONObject("""{"type":"content_block_stop","index":1}"""), callbacks)

    // 3. Server-side search results
    acc.processSseEvent(
      "content_block_start",
      JSONObject(
        """{"type":"content_block_start","index":2,"content_block":{"type":"web_search_tool_result","tool_use_id":"srv_1","content":[{"type":"web_search_result","title":"星瞳 - 虚拟歌手百科","url":"https://example.com/xingtong"}]}}"""
      ),
      callbacks
    )
    acc.processSseEvent("content_block_stop", JSONObject("""{"type":"content_block_stop","index":2}"""), callbacks)

    // 4. Second interleaved thinking block
    acc.processSseEvent(
      "content_block_start",
      JSONObject("""{"type":"content_block_start","index":3,"content_block":{"type":"thinking","thinking":""}}"""),
      callbacks
    )
    acc.processSseEvent(
      "content_block_delta",
      JSONObject("""{"type":"content_block_delta","index":3,"delta":{"type":"thinking_delta","thinking":"资料已确认，开始结合曲库分析。"}}"""),
      callbacks
    )
    acc.processSseEvent("content_block_stop", JSONObject("""{"type":"content_block_stop","index":3}"""), callbacks)

    // 5. Final text + citation
    acc.processSseEvent(
      "content_block_start",
      JSONObject("""{"type":"content_block_start","index":4,"content_block":{"type":"text","text":""}}"""),
      callbacks
    )
    acc.processSseEvent(
      "content_block_delta",
      JSONObject("""{"type":"content_block_delta","index":4,"delta":{"type":"text_delta","text":"## 星瞳音乐风格剖析\n星瞳是腾讯炫舞旗下虚拟偶像。"}}"""),
      callbacks
    )
    acc.processSseEvent(
      "message_delta",
      JSONObject("""{"type":"message_delta","delta":{"stop_reason":"pause_turn"}}"""),
      callbacks
    )

    assertEquals("pause_turn", acc.stopReason)
    assertTrue(reasoning.toString().contains("让我先搜索星瞳的资料"))
    assertTrue(reasoning.toString().contains("星瞳 虚拟歌手 专辑"))
    assertTrue(reasoning.toString().contains("已检索到 1 条网页资料"))
    assertTrue(reasoning.toString().contains("资料已确认"))
    assertTrue(content.toString().contains("星瞳音乐风格剖析"))
    assertTrue(searchEvents.any { it.first && it.second.contains("星瞳 虚拟歌手 专辑") })

    val preservedBlocks = acc.toAssistantContentBlocks()
    assertEquals(5, preservedBlocks.length())
    assertEquals("sig_abc123", preservedBlocks.getJSONObject(0).getString("signature"))
    assertEquals("server_tool_use", preservedBlocks.getJSONObject(1).getString("type"))
    assertEquals("web_search_tool_result", preservedBlocks.getJSONObject(2).getString("type"))

    val footer = formatCitationsFooter(content.toString(), acc.citations.values)
    assertTrue(footer.contains("参考来源"))
    assertTrue(footer.contains("[星瞳 - 虚拟歌手百科](https://example.com/xingtong)"))
  }

  @Test
  fun `playlist cover configs JSON round-trip preserves single, custom, and random modes`() {
    val input = mapOf(
      com.example.model.PlaylistCoverKeys.artist("星瞳") to com.example.model.PlaylistCoverConfig(
        mode = com.example.model.PlaylistCoverMode.SINGLE_TRACK,
        trackIds = listOf("track_lumen")
      ),
      com.example.model.PlaylistCoverKeys.custom(7L) to com.example.model.PlaylistCoverConfig(
        mode = com.example.model.PlaylistCoverMode.CUSTOM_COLLAGE,
        trackIds = listOf("a", "b", "c", "d")
      ),
      com.example.model.PlaylistCoverKeys.FAVORITES to com.example.model.PlaylistCoverConfig(
        mode = com.example.model.PlaylistCoverMode.RANDOM_COLLAGE,
        randomSeed = 998877L
      )
    )
    val raw = PreferencesRepository.serializePlaylistCoverConfigsJson(input)
    val decoded = PreferencesRepository.parsePlaylistCoverConfigsJson(raw)
    assertEquals(3, decoded.size)
    assertEquals(
      com.example.model.PlaylistCoverMode.SINGLE_TRACK,
      decoded[com.example.model.PlaylistCoverKeys.artist("星瞳")]?.mode
    )
    assertEquals(
      listOf("track_lumen"),
      decoded[com.example.model.PlaylistCoverKeys.artist("星瞳")]?.trackIds
    )
    assertEquals(
      listOf("a", "b", "c", "d"),
      decoded[com.example.model.PlaylistCoverKeys.custom(7L)]?.trackIds
    )
    assertEquals(
      998877L,
      decoded[com.example.model.PlaylistCoverKeys.FAVORITES]?.randomSeed
    )
  }
}

