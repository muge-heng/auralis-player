package com.example.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "ai_chat_sessions")
data class AiChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val modelId: String = "deepseek-flash",
    val reasoningEffort: String = ReasoningEffortLevel.OFF.apiValue,
    val boundTrackTitle: String? = null
)

@Entity(
    tableName = "ai_chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = AiChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId", "createdAt"])]
)
data class AiChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val role: String, // "user", "assistant", "error"
    val content: String,
    val reasoningContent: String? = null,
    val reasoningDurationMs: Long = 0L,
    val reasoningEffortUsed: String? = null,
    val modelUsed: String? = null,
    val usedVision: Boolean = false,
    val attachedTrackLabel: String? = null,
    val errorSummary: String? = null,
    val errorRawDetail: String? = null,
    val httpStatusCode: Int? = null,
    val errorEndpoint: String? = null,
    val suggestedEqJson: String? = null,
    val curatedTitlesCsv: String? = null,
    val suggestedPlaylistJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class AiSuggestedPlaylist(
    val name: String,
    val description: String = "",
    val trackTitles: List<String> = emptyList()
)

data class AiErrorReport(
    val summary: String,
    val httpStatusCode: Int? = null,
    val endpoint: String = "",
    val modelId: String = "",
    val reasoningEffort: String = "off",
    val rawDetail: String = "",
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun buildModelFeedbackPrompt(): String = buildString {
        appendLine("刚才的 API 请求或执行出现以下具体报错信息，请你帮我分析原因并给出修复/调优建议（如果是参数或模型兼容问题，请直接指出应如何调整配置；随后也可以直接回答我刚才的问题）：")
        appendLine("```text")
        appendLine("摘要: $summary")
        if (httpStatusCode != null) appendLine("HTTP 状态码: $httpStatusCode")
        if (modelId.isNotBlank()) appendLine("请求模型: $modelId")
        appendLine("reasoning_effort: $reasoningEffort")
        if (endpoint.isNotBlank()) appendLine("接口地址: $endpoint")
        if (rawDetail.isNotBlank()) {
            appendLine("原始报错详情:")
            appendLine(rawDetail.take(2400))
        }
        appendLine("```")
    }
}

data class AiSessionRuntimeState(
    val isGenerating: Boolean = false,
    val isThinkingPhase: Boolean = false,
    val isSearchingWeb: Boolean = false,
    val webSearchStatusLabel: String = "",
    val liveReasoning: String = "",
    val liveContent: String = "",
    val startedAtMs: Long = 0L,
    val activeModelId: String = "",
    val activeEffort: ReasoningEffortLevel = ReasoningEffortLevel.OFF
)

enum class AiAttachmentBadgeType {
    TRACK,
    CATALOG,
    LYRICS,
    VISION,
    WEB_SEARCH
}

data class AiAttachmentBadge(
    val type: AiAttachmentBadgeType,
    val label: String
)

object AiAttachmentBadgeCodec {
    private const val SEP = "||"

    fun encode(
        trackTitle: String?,
        catalogCount: Int,
        lyricCount: Int,
        usedVision: Boolean,
        usedWebSearch: Boolean
    ): String? {
        val tokens = mutableListOf<String>()
        if (!trackTitle.isNullOrBlank()) tokens += "TRACK:$trackTitle"
        if (catalogCount > 0) tokens += "CATALOG:$catalogCount"
        if (lyricCount > 0) tokens += "LYRICS:$lyricCount"
        if (usedVision) tokens += "VISION:1"
        if (usedWebSearch) tokens += "WEB:1"
        return tokens.takeIf { it.isNotEmpty() }?.joinToString(SEP)
    }

    fun decode(raw: String?, usedVisionFallback: Boolean = false): List<AiAttachmentBadge> {
        if (raw.isNullOrBlank()) {
            return if (usedVisionFallback) {
                listOf(AiAttachmentBadge(AiAttachmentBadgeType.VISION, "封面识图"))
            } else {
                emptyList()
            }
        }
        val badges = mutableListOf<AiAttachmentBadge>()
        var hasVisionTag = false
        val parts = raw.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }
        for (part in parts) {
            when {
                part.startsWith("TRACK:") -> {
                    val title = part.removePrefix("TRACK:").trim()
                    if (title.isNotEmpty()) {
                        badges += AiAttachmentBadge(AiAttachmentBadgeType.TRACK, "正在播放 · $title")
                    }
                }
                part.startsWith("CATALOG:") -> {
                    val cnt = part.removePrefix("CATALOG:").trim()
                    badges += AiAttachmentBadge(AiAttachmentBadgeType.CATALOG, "曲库清单 ($cnt 首)")
                }
                part.startsWith("LYRICS:") -> {
                    val cnt = part.removePrefix("LYRICS:").trim()
                    badges += AiAttachmentBadge(AiAttachmentBadgeType.LYRICS, "歌词 ($cnt 首)")
                }
                part.startsWith("VISION:") -> {
                    hasVisionTag = true
                    badges += AiAttachmentBadge(AiAttachmentBadgeType.VISION, "封面识图")
                }
                part.startsWith("WEB:") -> {
                    badges += AiAttachmentBadge(AiAttachmentBadgeType.WEB_SEARCH, "联网搜索")
                }
                else -> {
                    // Legacy plain string format compatibility
                    badges += AiAttachmentBadge(AiAttachmentBadgeType.TRACK, part)
                }
            }
        }
        if (usedVisionFallback && !hasVisionTag) {
            badges += AiAttachmentBadge(AiAttachmentBadgeType.VISION, "封面识图")
        }
        return badges
    }
}
