package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ai.AiTaskType
import com.example.data.ai.DeepSeekChatClient
import com.example.model.AI_MAX_TOKEN_PRESETS
import com.example.model.AiAttachmentBadgeCodec
import com.example.model.AiAttachmentBadgeType
import com.example.model.AiCatalogAttachmentPrefs
import com.example.model.AiChatMessageEntity
import com.example.model.AiChatSessionEntity
import com.example.model.AiConfig
import com.example.model.AiProviderType
import com.example.model.AiSessionRuntimeState
import com.example.model.AiSuggestedPlaylist
import com.example.model.DEFAULT_AI_MAX_OUTPUT_TOKENS
import com.example.model.EqPreset
import com.example.model.ReasoningEffortLevel
import com.example.model.Track
import com.example.model.formatTokenCountLabel
import com.example.model.parseTokenCountInput
import com.example.ui.components.AiCatalogPickerSheet
import com.example.ui.components.MarkdownText
import com.example.ui.components.ReasoningEffortSliderCard
import com.example.ui.components.bouncyClickable
import com.example.ui.components.reasoningAccentColor
import com.example.ui.theme.AuraViolet
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.EmeraldLossless
import com.example.ui.theme.RubyAccent
import kotlinx.coroutines.delay
import java.util.Locale

private data class FollowupQuickPrompt(
    val label: String,
    val prompt: String,
    val forceAttachCatalog: Boolean = false
)

private val EXTRA_FOLLOWUP_PROMPTS = listOf(
    FollowupQuickPrompt(
        label = "帮我整理主题歌单",
        prompt = "请结合我附带的本地音乐曲库，按氛围与听感连贯性帮我整理一份精品主题歌单，并输出 PLAYLIST_JSON 供我一键创建。",
        forceAttachCatalog = true
    ),
    FollowupQuickPrompt(
        label = "分析编曲与配器层次",
        prompt = "请从声场层次、配器编排和动态起伏三个维度，拆解当前曲目的编曲巧思。"
    ),
    FollowupQuickPrompt(
        label = "定制更通透的人声 10 段 EQ",
        prompt = "请为当前曲目重新设计一套人声更通透、声场更开阔的 10 段均衡器参数，并附带 EQ_JSON。"
    ),
    FollowupQuickPrompt(
        label = "解析情绪转折与意象",
        prompt = "结合歌词与旋律氛围，剖析这首歌的情绪转折点和最打动人心的意象细节。"
    ),
    FollowupQuickPrompt(
        label = "推荐同气质延伸听单",
        prompt = "基于这首歌的听感气质，结合我的曲库推荐 5 首风格相近的曲目或延伸听单，并说明理由。",
        forceAttachCatalog = true
    )
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiChatScreen(
    aiConfig: AiConfig,
    sessions: List<AiChatSessionEntity>,
    activeSessionId: Long,
    messages: List<AiChatMessageEntity>,
    sessionRuntimeStates: Map<Long, AiSessionRuntimeState>,
    currentTrack: Track?,
    musicTracks: List<Track>,
    catalogAttachmentPrefs: AiCatalogAttachmentPrefs,
    onCreateSession: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onRenameSession: (Long, String) -> Unit,
    onRegenerateSessionTitle: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onSendMessage: (text: String, attachTrack: Boolean, attachVision: Boolean, attachLibraryCatalog: Boolean) -> Unit,
    onRunQuickTask: (AiTaskType, attachVision: Boolean, attachLibraryCatalog: Boolean) -> Unit,
    onStopGeneration: (Long) -> Unit,
    onFeedbackErrorToModel: (AiChatMessageEntity) -> Unit,
    onRetryLastMessage: (ReasoningEffortLevel?) -> Unit,
    onUpdateReasoningEffort: (ReasoningEffortLevel) -> Unit,
    onToggleExtendedReasoningLevel: (ReasoningEffortLevel) -> Unit,
    onDetentHaptic: (ReasoningEffortLevel) -> Unit,
    onToggleWebSearch: () -> Unit,
    onUpdateCatalogAttachmentPrefs: (AiCatalogAttachmentPrefs) -> Unit,
    onUpdateAiConfig: (AiConfig) -> Unit,
    onApplySuggestedEq: (EqPreset) -> Unit,
    onApplyAndSaveSuggestedEq: (EqPreset) -> Unit,
    onCreatePlaylistFromAi: (AiSuggestedPlaylist) -> Unit,
    onPlayCuratedTitles: (List<String>) -> Unit,
    onCopiedNotice: (String) -> Unit,
    contentBottomPadding: Int
) {
    val activeSession = sessions.firstOrNull { it.id == activeSessionId }
    val activeRuntime = sessionRuntimeStates[activeSessionId] ?: AiSessionRuntimeState()
    val runningSessionsCount = sessionRuntimeStates.values.count { it.isGenerating }

    var inputText by rememberSaveable(activeSessionId) { mutableStateOf("") }
    var attachTrackContext by rememberSaveable { mutableStateOf(true) }
    var attachCoverVision by rememberSaveable { mutableStateOf(false) }
    var showReasoningDeck by rememberSaveable { mutableStateOf(false) }
    var showQuickPromptsDeck by rememberSaveable { mutableStateOf(false) }
    var showSessionsSheet by remember { mutableStateOf(false) }
    var showConfigSheet by remember { mutableStateOf(false) }
    var showCatalogPicker by remember { mutableStateOf(false) }
    var renamingSession by remember { mutableStateOf<AiChatSessionEntity?>(null) }

    val effectiveSelectedCatalogTracks = remember(musicTracks, catalogAttachmentPrefs) {
        if (!catalogAttachmentPrefs.enabled) {
            emptyList()
        } else if (!catalogAttachmentPrefs.hasCustomSelection) {
            musicTracks
        } else {
            musicTracks.filter { it.id in catalogAttachmentPrefs.selectedTrackIds }
        }
    }
    val effectiveCatalogLyricsCount = remember(effectiveSelectedCatalogTracks, catalogAttachmentPrefs) {
        effectiveSelectedCatalogTracks.count { it.id in catalogAttachmentPrefs.lyricTrackIds }
    }
    val attachLibraryCatalog = catalogAttachmentPrefs.enabled && effectiveSelectedCatalogTracks.isNotEmpty()

    val listState = rememberLazyListState()
    val effortColor by animateColorAsState(
        targetValue = reasoningAccentColor(aiConfig.reasoningEffort),
        label = "ai_effort_pill_color"
    )

    // Auto-scroll smoothly when new messages arrive or streaming chunks update
    LaunchedEffect(activeSessionId, messages.size, activeRuntime.liveContent.length, activeRuntime.liveReasoning.length) {
        val total = messages.size + (if (activeRuntime.isGenerating) 1 else 0)
        if (total > 0) {
            listState.animateScrollToItem(total)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .padding(bottom = contentBottomPadding.dp)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Session Switcher Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                    .bouncyClickable(pressedScale = 0.97f) { showSessionsSheet = true }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .testTag("ai_sessions_switcher")
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(RubyAccent.copy(alpha = 0.25f), AuraViolet.copy(alpha = 0.25f)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (activeRuntime.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = effortColor
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Forum,
                            contentDescription = stringResource(R.string.cd_ai_sessions),
                            tint = ChampagneGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = activeSession?.title ?: "Auralis AI 对话",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (sessions.size > 1) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${sessions.size} 窗口",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChampagneGold
                                )
                            }
                        }
                    }
                    Text(
                        text = buildString {
                            if (aiConfig.activeProvider == AiProviderType.GEMINI) {
                                append("Google Gemini · 预留接口")
                            } else {
                                append(aiConfig.deepSeekModelId)
                                append(" · 思考 ")
                                append(aiConfig.reasoningEffort.shortLabel)
                                if (aiConfig.enableWebSearch) {
                                    append(" · 联网")
                                }
                            }
                            if (runningSessionsCount > 0) {
                                append(" · ${runningSessionsCount} 个窗口生成中")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // New Independent Window Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(RubyAccent.copy(alpha = 0.16f))
                    .border(0.75.dp, RubyAccent.copy(alpha = 0.4f), CircleShape)
                    .bouncyClickable { onCreateSession() }
                    .testTag("ai_new_session_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.cd_ai_new_session),
                    tint = RubyAccent
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // API & Model Settings Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .bouncyClickable { showConfigSheet = true }
                    .testTag("ai_config_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.cd_ai_config),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Quick Multi-Window Pill Bar (shown when 2+ independent windows exist)
        if (sessions.size >= 2) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    val selected = session.id == activeSessionId
                    val isBusy = sessionRuntimeStates[session.id]?.isGenerating == true
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) ChampagneGold.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                            .border(
                                width = if (selected) 1.dp else 0.5.dp,
                                color = if (selected) ChampagneGold.copy(alpha = 0.65f)
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onSelectSession(session.id) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("ai_session_tab_${session.id}")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.8.dp,
                                color = RubyAccent
                            )
                        }
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) ChampagneGold else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp)
                        )
                    }
                }
            }
        }

        // Provider Status Banner (distinct for Gemini vs unconfigured DeepSeek)
        if (aiConfig.activeProvider == AiProviderType.GEMINI) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AuraViolet.copy(alpha = 0.16f))
                    .border(0.75.dp, AuraViolet.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .clickable { showConfigSheet = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Google Gemini 接口当前为预留状态 · 点击切换至 DeepSeek / 兼容 API",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFD8B4FE),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "配置 →",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChampagneGold
                )
            }
        } else if (aiConfig.deepSeekApiKey.isBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ChampagneGold.copy(alpha = 0.14f))
                    .border(0.75.dp, ChampagneGold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .clickable { showConfigSheet = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "尚未配置 API Key · 点击此处填入 DeepSeek 或兼容接口密钥",
                    style = MaterialTheme.typography.labelMedium,
                    color = ChampagneGold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "去配置 →",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChampagneGold
                )
            }
        }

        // Conversation Message Timeline or Empty Welcome Stage
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && !activeRuntime.isGenerating) {
                EmptyAiWelcomeStage(
                    currentTrack = currentTrack,
                    aiConfig = aiConfig,
                    onRunQuickTask = { task -> onRunQuickTask(task, attachCoverVision, attachLibraryCatalog) },
                    onOpenReasoningDeck = {
                        showQuickPromptsDeck = false
                        showReasoningDeck = true
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        when (msg.role) {
                            "user" -> UserMessageBubble(message = msg)
                            "error" -> ErrorDiagnosticCard(
                                message = msg,
                                onFeedbackToModel = { onFeedbackErrorToModel(msg) },
                                onRetryWithEffortOff = { onRetryLastMessage(ReasoningEffortLevel.OFF) },
                                onRetrySame = { onRetryLastMessage(null) },
                                onCopied = onCopiedNotice
                            )
                            else -> AssistantMessageBubble(
                                message = msg,
                                showThinkingByDefault = aiConfig.showThinkingProcess,
                                onApplySuggestedEq = onApplySuggestedEq,
                                onApplyAndSaveSuggestedEq = onApplyAndSaveSuggestedEq,
                                onCreatePlaylistFromAi = onCreatePlaylistFromAi,
                                onPlayCuratedTitles = onPlayCuratedTitles,
                                onCopied = onCopiedNotice
                            )
                        }
                    }

                    if (activeRuntime.isGenerating) {
                        item(key = "live_stream_${activeSessionId}") {
                            LiveStreamingBubble(
                                runtime = activeRuntime,
                                showThinking = aiConfig.showThinkingProcess,
                                onStop = { onStopGeneration(activeSessionId) }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(6.dp)) }
                }
            }
        }

        // Collapsible Multi-Turn Quick Prompts Deck (usable at ANY turn!)
        AnimatedVisibility(
            visible = showQuickPromptsDeck,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(0.75.dp, ChampagneGold.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(16.dp))
                        Text("快捷预设提问（多轮随时调用）", style = MaterialTheme.typography.titleSmall, color = ChampagneGold)
                    }
                    IconButton(
                        onClick = { showQuickPromptsDeck = false },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭快捷提问", modifier = Modifier.size(16.dp))
                    }
                }

                // 4 Core Acoustic Studio Tasks
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AiTaskType.entries, key = { it.name }) { task ->
                        Column(
                            modifier = Modifier
                                .width(154.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(RubyAccent.copy(alpha = 0.12f))
                                .border(0.6.dp, RubyAccent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .bouncyClickable(pressedScale = 0.96f) {
                                    showQuickPromptsDeck = false
                                    onRunQuickTask(task, attachCoverVision, attachLibraryCatalog)
                                }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = task.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Extra Follow-Up Quick Prompts
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EXTRA_FOLLOWUP_PROMPTS.forEach { itemPrompt ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                                .clickable {
                                    showQuickPromptsDeck = false
                                    onSendMessage(
                                        itemPrompt.prompt,
                                        attachTrackContext,
                                        attachCoverVision,
                                        attachLibraryCatalog || itemPrompt.forceAttachCatalog
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = itemPrompt.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Collapsible Reasoning Effort Slider Deck (with HIGH / XHIGH / MAX / ULTRA visual effects)
        AnimatedVisibility(
            visible = showReasoningDeck,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                ReasoningEffortSliderCard(
                    currentLevel = aiConfig.reasoningEffort,
                    enabledLevels = aiConfig.enabledReasoningLevels,
                    onSelectLevel = onUpdateReasoningEffort,
                    onToggleExtendedLevel = onToggleExtendedReasoningLevel,
                    onDetentHaptic = onDetentHaptic
                )
            }
        }

        // Bottom Composer Dock
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f))
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Context, Quick Prompts & Reasoning Effort Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Quick Prompts Pill (available in multi-turn conversations too!)
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (showQuickPromptsDeck) ChampagneGold.copy(alpha = 0.24f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .border(
                                width = if (showQuickPromptsDeck) 1.dp else 0.5.dp,
                                color = if (showQuickPromptsDeck) ChampagneGold else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(50)
                            )
                            .bouncyClickable(pressedScale = 0.95f) {
                                showQuickPromptsDeck = !showQuickPromptsDeck
                                if (showQuickPromptsDeck) showReasoningDeck = false
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("ai_quick_prompts_pill")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = "快捷预设提问",
                            tint = ChampagneGold,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "快捷提问",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (showQuickPromptsDeck) ChampagneGold else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Reasoning Effort Slider Pill
                item {
                    val isElevated = aiConfig.reasoningEffort != ReasoningEffortLevel.OFF
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (showReasoningDeck || isElevated) effortColor.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .border(
                                width = if (isElevated) 1.dp else 0.5.dp,
                                color = effortColor.copy(alpha = if (isElevated) 0.7f else 0.3f),
                                shape = RoundedCornerShape(50)
                            )
                            .bouncyClickable(pressedScale = 0.95f) {
                                showReasoningDeck = !showReasoningDeck
                                if (showReasoningDeck) showQuickPromptsDeck = false
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("ai_reasoning_pill")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = "调节思考强度",
                            tint = effortColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "思考: ${aiConfig.reasoningEffort.shortLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = effortColor
                        )
                        Icon(
                            imageVector = if (showReasoningDeck) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                            contentDescription = null,
                            tint = effortColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // DeepSeek Native Web Search Quick Toggle Pill
                if (aiConfig.activeProvider != AiProviderType.GEMINI) {
                    item {
                        val searchOn = aiConfig.enableWebSearch
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (searchOn) EmeraldLossless.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(
                                    width = if (searchOn) 1.dp else 0.5.dp,
                                    color = if (searchOn) EmeraldLossless.copy(alpha = 0.65f)
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(50)
                                )
                                .bouncyClickable(pressedScale = 0.95f) { onToggleWebSearch() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("ai_web_search_pill")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = "DeepSeek 原生联网搜索",
                                tint = if (searchOn) EmeraldLossless else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (searchOn) "联网搜索" else "联网关闭",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (searchOn) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (searchOn) EmeraldLossless else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Interactive Music Catalog & Per-Track Lyrics Picker Pill (remembered across sessions)
                if (musicTracks.isNotEmpty()) {
                    item {
                        val badgeSummary = when {
                            attachLibraryCatalog && effectiveCatalogLyricsCount > 0 ->
                                "已附带曲库 (${effectiveSelectedCatalogTracks.size}首·${effectiveCatalogLyricsCount}词)"
                            attachLibraryCatalog ->
                                "已附带曲库 (${effectiveSelectedCatalogTracks.size}首)"
                            else ->
                                "附带曲库 (${musicTracks.size})"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (attachLibraryCatalog) ChampagneGold.copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(
                                    width = if (attachLibraryCatalog) 1.dp else 0.5.dp,
                                    color = if (attachLibraryCatalog) ChampagneGold.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(50)
                                )
                                .bouncyClickable(pressedScale = 0.95f) { showCatalogPicker = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("ai_attach_catalog_chip")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LibraryMusic,
                                contentDescription = "选择附带曲库清单与歌词",
                                tint = if (attachLibraryCatalog) ChampagneGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = badgeSummary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (attachLibraryCatalog) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (attachLibraryCatalog) ChampagneGold else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = if (attachLibraryCatalog) ChampagneGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                if (currentTrack != null) {
                    item {
                        FilterChip(
                            selected = attachTrackContext,
                            onClick = { attachTrackContext = !attachTrackContext },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = currentTrack.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 110.dp)
                                )
                            },
                            modifier = Modifier.height(30.dp)
                        )
                    }
                    if (aiConfig.enableVisionForCovers && !currentTrack.artworkPath.isNullOrBlank()) {
                        item {
                            FilterChip(
                                selected = attachCoverVision,
                                onClick = { attachCoverVision = !attachCoverVision },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.ImageSearch,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = { Text("识图封面") },
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }
                }
            }

            // Input Row + Send/Stop Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = if (currentTrack != null && attachTrackContext) {
                                "聊聊「${currentTrack.title}」或任何音乐与声学话题..."
                            } else {
                                "输入问题，支持 Markdown 渲染与多轮思考..."
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = effortColor.copy(alpha = 0.7f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_chat_input")
                )

                if (activeRuntime.isGenerating) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(RubyAccent)
                            .bouncyClickable { onStopGeneration(activeSessionId) }
                            .testTag("ai_stop_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = stringResource(R.string.cd_ai_stop),
                            tint = Color.White
                        )
                    }
                } else {
                    val canSend = inputText.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) Brush.linearGradient(listOf(RubyAccent, effortColor))
                                else Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                )
                            )
                            .bouncyClickable(enabled = canSend) {
                                val text = inputText.trim()
                                if (text.isNotEmpty()) {
                                    inputText = ""
                                    onSendMessage(text, attachTrackContext, attachCoverVision, attachLibraryCatalog)
                                }
                            }
                            .testTag("ai_send_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.cd_ai_send),
                            tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Multi-Window Session Manager Sheet
    if (showSessionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSessionsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column {
                    Text("独立对话窗口管理", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "每个对话窗口拥有独立上下文与并发生成队列，切换窗口不会打断正在进行的思考",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        onCreateSession()
                        showSessionsSheet = false
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_sheet_new_session")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("新建独立对话窗口")
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(380.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        val isActive = session.id == activeSessionId
                        val runtime = sessionRuntimeStates[session.id]
                        val isBusy = runtime?.isGenerating == true
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isActive) ChampagneGold.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(
                                    width = if (isActive) 1.dp else 0.5.dp,
                                    color = if (isActive) ChampagneGold.copy(alpha = 0.65f)
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    onSelectSession(session.id)
                                    showSessionsSheet = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row 1: Full-width spacious title so long conversation titles are never crushed
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = RubyAccent
                                    )
                                }
                                Text(
                                    text = session.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isActive) ChampagneGold else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isActive) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(ChampagneGold.copy(alpha = 0.22f))
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "当前",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = ChampagneGold
                                        )
                                    }
                                }
                            }

                            // Row 2: Model metadata on the left + streamlined 3-action bar on the right (Clear button removed)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = buildString {
                                        append(session.modelId)
                                        append(" · 思考 ")
                                        append(ReasoningEffortLevel.fromApiValue(session.reasoningEffort).shortLabel)
                                        if (isBusy) append(" · 生成中")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SessionActionPill(
                                        icon = Icons.Rounded.AutoAwesome,
                                        contentDescription = "AI 重新生成标题",
                                        tint = ChampagneGold,
                                        onClick = { onRegenerateSessionTitle(session.id) },
                                        testTag = "ai_regen_title_${session.id}"
                                    )
                                    SessionActionPill(
                                        icon = Icons.Rounded.Edit,
                                        contentDescription = "重命名对话",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        onClick = { renamingSession = session },
                                        testTag = "ai_rename_session_${session.id}"
                                    )
                                    if (sessions.size > 1) {
                                        SessionActionPill(
                                            icon = Icons.Rounded.DeleteOutline,
                                            contentDescription = "删除窗口",
                                            tint = MaterialTheme.colorScheme.error,
                                            onClick = { onDeleteSession(session.id) },
                                            testTag = "ai_delete_session_${session.id}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Session Dialog
    renamingSession?.let { target ->
        var newTitle by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renamingSession = null },
            title = { Text("重命名对话窗口") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("窗口标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newTitle.isNotBlank()) onRenameSession(target.id, newTitle.trim())
                    renamingSession = null
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingSession = null }) {
                    Text("取消")
                }
            }
        )
    }

    // Remembered Music Catalog & Per-Song Lyrics Picker Modal
    if (showCatalogPicker) {
        AiCatalogPickerSheet(
            musicTracks = musicTracks,
            currentTrackId = currentTrack?.id,
            attachCurrentTrackContext = attachTrackContext,
            initialPrefs = catalogAttachmentPrefs,
            onSaveAndApply = { updatedPrefs ->
                onUpdateCatalogAttachmentPrefs(updatedPrefs)
                showCatalogPicker = false
            },
            onDismiss = { showCatalogPicker = false }
        )
    }

    // Full AI Provider, Model, Streaming, Web Search, Max Tokens & Reasoning Settings Modal
    if (showConfigSheet) {
        AiModelConfigSheet(
            aiConfig = aiConfig,
            onSave = { updated ->
                onUpdateAiConfig(updated)
                showConfigSheet = false
            },
            onDismiss = { showConfigSheet = false }
        )
    }
}

@Composable
private fun SessionActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun EmptyAiWelcomeStage(
    currentTrack: Track?,
    aiConfig: AiConfig,
    onRunQuickTask: (AiTaskType) -> Unit,
    onOpenReasoningDeck: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                RubyAccent.copy(alpha = 0.16f),
                                AuraViolet.copy(alpha = 0.16f),
                                ChampagneGold.copy(alpha = 0.10f)
                            )
                        )
                    )
                    .border(0.75.dp, ChampagneGold.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = ChampagneGold)
                    Text("Auralis 声学对话空间", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    text = "支持多轮上下文对话、多窗口并发互不干扰、完整 Markdown 排版、流式思考过程展示、可调 reasoning_effort 强度与 10 段 AI 均衡器直连。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (currentTrack != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = RubyAccent, modifier = Modifier.size(16.dp))
                        Text(
                            text = "当前关联曲目：${currentTrack.title} · ${currentTrack.artist}",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                OutlinedButton(
                    onClick = onOpenReasoningDeck,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("展开思考强度滑块（当前 ${aiConfig.reasoningEffort.shortLabel}）")
                }
            }
        }

        item {
            Text(
                text = "快捷音乐与声学指令",
                style = MaterialTheme.typography.labelLarge,
                color = ChampagneGold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(AiTaskType.entries, key = { it.name }) { task ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                    .bouncyClickable(pressedScale = 0.97f) { onRunQuickTask(task) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RubyAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = RubyAccent, modifier = Modifier.size(19.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = task.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserMessageBubble(message: AiChatMessageEntity) {
    val badges = remember(message.attachedTrackLabel, message.usedVision) {
        AiAttachmentBadgeCodec.decode(message.attachedTrackLabel, message.usedVision)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 316.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            RubyAccent.copy(alpha = 0.22f),
                            AuraViolet.copy(alpha = 0.20f)
                        )
                    )
                )
                .border(
                    0.75.dp,
                    RubyAccent.copy(alpha = 0.45f),
                    RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (badges.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    badges.forEach { badge ->
                        val (icon, tint) = when (badge.type) {
                            AiAttachmentBadgeType.TRACK -> Icons.Rounded.MusicNote to ChampagneGold
                            AiAttachmentBadgeType.CATALOG -> Icons.Rounded.LibraryMusic to RubyAccent
                            AiAttachmentBadgeType.LYRICS -> Icons.Rounded.Lyrics to EmeraldLossless
                            AiAttachmentBadgeType.VISION -> Icons.Rounded.ImageSearch to AuraViolet
                            AiAttachmentBadgeType.WEB_SEARCH -> Icons.Rounded.Public to EmeraldLossless
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(tint.copy(alpha = 0.18f))
                                .border(0.6.dp, tint.copy(alpha = 0.48f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = badge.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 176.dp)
                            )
                        }
                    }
                }
            }
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantMessageBubble(
    message: AiChatMessageEntity,
    showThinkingByDefault: Boolean,
    onApplySuggestedEq: (EqPreset) -> Unit,
    onApplyAndSaveSuggestedEq: (EqPreset) -> Unit,
    onCreatePlaylistFromAi: (AiSuggestedPlaylist) -> Unit,
    onPlayCuratedTitles: (List<String>) -> Unit,
    onCopied: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var thinkingExpanded by remember(message.id) { mutableStateOf(false) }

    val effortLevel = ReasoningEffortLevel.fromApiValue(message.reasoningEffortUsed ?: "off")
    val effortColor = reasoningAccentColor(effortLevel)

    val cleanedDisplayText = remember(message.content) {
        stripStructuredJsonDirectives(message.content)
    }
    val parsedEq = remember(message.suggestedEqJson) {
        DeepSeekChatClient.parseEqPreset(message.suggestedEqJson)
    }
    val parsedPlaylist = remember(message.suggestedPlaylistJson) {
        DeepSeekChatClient.parseSuggestedPlaylist(message.suggestedPlaylistJson)
    }
    val curatedList = remember(message.curatedTitlesCsv, parsedPlaylist) {
        val fromCsv = message.curatedTitlesCsv
            ?.split("||")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (fromCsv.isNotEmpty()) fromCsv else parsedPlaylist?.trackTitles.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                0.6.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header metadata row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(15.dp))
                Text(
                    text = message.modelUsed ?: "Auralis AI",
                    style = MaterialTheme.typography.labelMedium,
                    color = ChampagneGold
                )
                if (effortLevel != ReasoningEffortLevel.OFF) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(effortColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = effortLevel.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = effortColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(cleanedDisplayText))
                    onCopied("已复制回复内容")
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "复制回复",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // Collapsible Thinking Process Box (with Markdown rendering inside!)
        if (showThinkingByDefault && !message.reasoningContent.isNullOrBlank()) {
            val secStr = if (message.reasoningDurationMs > 0L) {
                String.format(Locale.US, " %.1fs", message.reasoningDurationMs / 1000f)
            } else {
                ""
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(effortColor.copy(alpha = 0.09f))
                    .border(0.6.dp, effortColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .clickable { thinkingExpanded = !thinkingExpanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = effortColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "深度思考过程$secStr",
                            style = MaterialTheme.typography.labelMedium,
                            color = effortColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        imageVector = if (thinkingExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (thinkingExpanded) "收起思考过程" else "展开思考过程",
                        tint = effortColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                AnimatedVisibility(visible = thinkingExpanded) {
                    MarkdownText(
                        markdown = message.reasoningContent,
                        baseColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        baseStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                        accentColor = effortColor
                    )
                }
                if (!thinkingExpanded) {
                    Text(
                        text = message.reasoningContent
                            .replace("[[SEARCH]]", "")
                            .replace("[[FETCH]]", "")
                            .replace("[[RESULT]]", "")
                            .replace("[[TOOL]]", "")
                            .replace("🔍", "")
                            .replace("🌐", "")
                            .replace("📚", "")
                            .replace("📄", "")
                            .replace("🛠️", "")
                            .replace("**", "")
                            .replace("###", "")
                            .replace("> ", "")
                            .replace('\n', ' ')
                            .replace(Regex("\\s{2,}"), " ")
                            .trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Rich Markdown Response Body
        MarkdownText(
            markdown = cleanedDisplayText,
            baseColor = MaterialTheme.colorScheme.onSurface,
            baseStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            accentColor = ChampagneGold
        )

        // Suggested 10-Band EQ Actions: "Apply Only" + "Apply & Save Preset"
        if (parsedEq != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EmeraldLossless.copy(alpha = 0.10f))
                    .border(0.75.dp, EmeraldLossless.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.Equalizer, contentDescription = null, tint = EmeraldLossless, modifier = Modifier.size(16.dp))
                    Text(
                        text = "AI 定制 10 段 EQ · ${parsedEq.name}",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldLossless
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onApplySuggestedEq(parsedEq) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_apply_eq_only")
                    ) {
                        Icon(Icons.Rounded.Equalizer, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("仅应用", maxLines = 1)
                    }
                    Button(
                        onClick = { onApplyAndSaveSuggestedEq(parsedEq) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldLossless,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1.25f)
                            .testTag("ai_apply_and_save_eq")
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("应用并保存预设", maxLines = 1, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Unified AI Curated Playlist / Mentioned Local Tracks Card
        val unifiedPlaylistCard = remember(parsedPlaylist, curatedList) {
            when {
                parsedPlaylist != null -> parsedPlaylist to true
                curatedList.isNotEmpty() -> AiSuggestedPlaylist(
                    name = "文中提及的本地曲目",
                    description = "从本条分析中识别到你曲库内的 ${curatedList.size} 首音乐，可直接连播或存为歌单",
                    trackTitles = curatedList
                ) to false
                else -> null
            }
        }
        if (unifiedPlaylistCard != null) {
            val (cardPlaylist, isExplicitCuration) = unifiedPlaylistCard
            var createdPlaylistState by remember(message.id) { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ChampagneGold.copy(alpha = 0.10f))
                    .border(0.75.dp, ChampagneGold.copy(alpha = 0.38f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = ChampagneGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isExplicitCuration) {
                                "AI 策展歌单 · ${cardPlaylist.name}"
                            } else {
                                "${cardPlaylist.name} (${cardPlaylist.trackTitles.size})"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = ChampagneGold,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (cardPlaylist.description.isNotBlank()) {
                            Text(
                                text = cardPlaylist.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    cardPlaylist.trackTitles.take(8).forEach { title ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .clickable { onPlayCuratedTitles(listOf(title)) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = ChampagneGold,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 150.dp)
                            )
                        }
                    }
                    if (cardPlaylist.trackTitles.size > 8) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ChampagneGold.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "+${cardPlaylist.trackTitles.size - 8} 首",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampagneGold
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onPlayCuratedTitles(cardPlaylist.trackTitles) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_play_playlist_button")
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("立即连播", maxLines = 1)
                    }
                    Button(
                        onClick = {
                            val saveSuggestion = if (isExplicitCuration) {
                                cardPlaylist
                            } else {
                                cardPlaylist.copy(
                                    name = "AI 精选听单 (${cardPlaylist.trackTitles.size}首)",
                                    description = "由 Auralis AI 乐评提及曲目生成"
                                )
                            }
                            onCreatePlaylistFromAi(saveSuggestion)
                            createdPlaylistState = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (createdPlaylistState) EmeraldLossless else RubyAccent,
                            contentColor = if (createdPlaylistState) Color.Black else Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("ai_create_playlist_button")
                    ) {
                        Icon(
                            imageVector = if (createdPlaylistState) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (createdPlaylistState) {
                                "已创建歌单"
                            } else {
                                "一键创建歌单 (${cardPlaylist.trackTitles.size})"
                            },
                            maxLines = 1,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun stripStructuredJsonDirectives(raw: String): String {
    var text = raw
    listOf("PLAYLIST_JSON:", "EQ_JSON:").forEach { prefix ->
        while (true) {
            val idx = text.indexOf(prefix)
            if (idx < 0) break
            val braceStart = text.indexOf('{', idx + prefix.length)
            if (braceStart < 0) {
                val lineEnd = text.indexOf('\n', idx).let { if (it < 0) text.length else it }
                text = text.removeRange(idx, lineEnd)
                continue
            }
            var depth = 0
            var inString = false
            var escaped = false
            var endIdx = -1
            for (i in braceStart until text.length) {
                val ch = text[i]
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
                            endIdx = i + 1
                            break
                        }
                    }
                }
            }
            text = if (endIdx > idx) {
                text.removeRange(idx, endIdx)
            } else {
                text.removeRange(idx, text.length)
            }
        }
    }
    return text.replace(Regex("\n{3,}"), "\n\n").trim()
}

@Composable
private fun LiveStreamingBubble(
    runtime: AiSessionRuntimeState,
    showThinking: Boolean,
    onStop: () -> Unit
) {
    var elapsedMs by remember(runtime.startedAtMs) { mutableLongStateOf(0L) }
    LaunchedEffect(runtime.isGenerating, runtime.startedAtMs) {
        while (runtime.isGenerating) {
            elapsedMs = (System.currentTimeMillis() - runtime.startedAtMs).coerceAtLeast(0L)
            delay(100L)
        }
    }
    val effortColor = reasoningAccentColor(runtime.activeEffort)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                1.dp,
                effortColor.copy(alpha = 0.5f),
                RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                val statusColor = if (runtime.isSearchingWeb) EmeraldLossless else effortColor
                if (runtime.isSearchingWeb) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        tint = EmeraldLossless,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = effortColor
                    )
                }
                Text(
                    text = when {
                        runtime.isSearchingWeb -> {
                            val label = runtime.webSearchStatusLabel.ifBlank { "正在联网检索资料" }
                            "$label (${String.format(Locale.US, "%.1fs", elapsedMs / 1000f)})..."
                        }
                        runtime.isThinkingPhase ->
                            "正在深度思考 (${String.format(Locale.US, "%.1fs", elapsedMs / 1000f)})..."
                        else ->
                            "正在生成回复 (${runtime.activeModelId})..."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onStop, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                Text("停止", color = RubyAccent)
            }
        }

        if (showThinking && runtime.liveReasoning.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(effortColor.copy(alpha = 0.11f))
                    .border(0.6.dp, effortColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "实时思考流 · ${runtime.activeEffort.shortLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = effortColor,
                    fontWeight = FontWeight.Bold
                )
                MarkdownText(
                    markdown = runtime.liveReasoning,
                    baseColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    baseStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    accentColor = effortColor
                )
            }
        }

        if (runtime.liveContent.isNotBlank()) {
            MarkdownText(
                markdown = runtime.liveContent,
                baseColor = MaterialTheme.colorScheme.onSurface,
                baseStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                accentColor = ChampagneGold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErrorDiagnosticCard(
    message: AiChatMessageEntity,
    onFeedbackToModel: () -> Unit,
    onRetryWithEffortOff: () -> Unit,
    onRetrySame: () -> Unit,
    onCopied: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var showRawLog by remember(message.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f))
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "请求返回具体报错",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = message.errorSummary ?: message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            message.httpStatusCode?.let { code ->
                DiagnosticChip("HTTP $code", MaterialTheme.colorScheme.error)
            }
            message.modelUsed?.takeIf { it.isNotBlank() }?.let { model ->
                DiagnosticChip("模型: $model", ChampagneGold)
            }
            message.reasoningEffortUsed?.takeIf { it.isNotBlank() }?.let { eff ->
                DiagnosticChip("reasoning_effort: $eff", AuraViolet)
            }
        }

        if (!message.errorRawDetail.isNullOrBlank() || !message.errorEndpoint.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable { showRawLog = !showRawLog }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (showRawLog) "收起原始错误载荷" else "展开完整报错日志与接口信息",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (showRawLog) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            AnimatedVisibility(visible = showRawLog) {
                val fullLog = buildString {
                    message.errorEndpoint?.takeIf { it.isNotBlank() }?.let { appendLine("Endpoint: $it") }
                    message.httpStatusCode?.let { appendLine("Status: HTTP $it") }
                    message.modelUsed?.let { appendLine("Model: $it") }
                    message.reasoningEffortUsed?.let { appendLine("reasoning_effort: $it") }
                    appendLine()
                    append(message.errorRawDetail.orEmpty())
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = fullLog,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = Color(0xFFFFB4AB)
                    )
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(fullLog))
                            onCopied("已复制完整报错详情")
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制详情")
                    }
                }
            }
        }

        // Action Buttons: Feedback Error to Model + Retry with OFF + Retry
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = onFeedbackToModel,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("ai_feedback_error_button")
            ) {
                Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("反馈报错给模型")
            }

            if (message.reasoningEffortUsed != null && message.reasoningEffortUsed != ReasoningEffortLevel.OFF.apiValue) {
                OutlinedButton(
                    onClick = onRetryWithEffortOff,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("关闭思考重试")
                }
            }

            OutlinedButton(
                onClick = onRetrySame,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("重试")
            }
        }
    }
}

@Composable
private fun DiagnosticChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AiModelConfigSheet(
    aiConfig: AiConfig,
    onSave: (AiConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var provider by remember(aiConfig) { mutableStateOf(aiConfig.activeProvider) }
    var apiKey by remember(aiConfig) { mutableStateOf(aiConfig.deepSeekApiKey) }
    var showKey by remember { mutableStateOf(false) }
    var modelId by remember(aiConfig) { mutableStateOf(aiConfig.deepSeekModelId) }
    var baseUrl by remember(aiConfig) { mutableStateOf(aiConfig.deepSeekBaseUrl) }
    var enableVision by remember(aiConfig) { mutableStateOf(aiConfig.enableVisionForCovers) }
    var enableStreaming by remember(aiConfig) { mutableStateOf(aiConfig.enableStreaming) }
    var showThinking by remember(aiConfig) { mutableStateOf(aiConfig.showThinkingProcess) }
    var autoGenerateTitle by remember(aiConfig) { mutableStateOf(aiConfig.autoGenerateSessionTitle) }
    var enableWebSearch by remember(aiConfig) { mutableStateOf(aiConfig.enableWebSearch) }
    var maxTokensInput by remember(aiConfig) { mutableStateOf(aiConfig.maxOutputTokens.toString()) }
    var customSystemPrompt by remember(aiConfig) { mutableStateOf(aiConfig.customSystemPrompt) }
    val parsedMaxTokens = parseTokenCountInput(maxTokensInput)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("模型与 API 接口配置", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭配置")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProviderType.entries.forEach { p ->
                    FilterChip(
                        selected = provider == p,
                        onClick = { provider = p },
                        label = { Text(p.displayName) },
                        modifier = Modifier.testTag("ai_provider_${p.name.lowercase()}")
                    )
                }
            }

            if (provider == AiProviderType.GEMINI) {
                // Dedicated Google Gemini Info Card (no DeepSeek fields leaked here!)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(AuraViolet.copy(alpha = 0.12f))
                        .border(0.8.dp, AuraViolet.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = Color(0xFFD8B4FE))
                        Text(
                            text = "Google Gemini 接口预留说明",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFD8B4FE),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "为避免共享安装包泄露开发者配额，Google Gemini 原生直连接口在此版本中保持预留状态，暂不发起外部请求。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "如果你希望使用 Gemini 或其他大模型，可切换至「DeepSeek / 兼容 API」并填入你的 OpenAI 兼容网关地址、API Key 与模型 ID（如 deepseek-flash 或兼容网关中的 gemini 模型名）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { provider = AiProviderType.DEEPSEEK },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("切换回 DeepSeek / 兼容 API 配置")
                    }
                }
            } else {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key（仅保存在本机 DataStore）") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "显示或隐藏密钥"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_config_api_key_input")
                )

                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("模型名称 (Model ID，支持手动填写任意兼容模型)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Active recommended preset only (removed retired deepseek-chat & deepseek-reasoner)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = modelId.equals("deepseek-flash", ignoreCase = true),
                        onClick = { modelId = "deepseek-flash" },
                        label = { Text("deepseek-flash（推荐）") }
                    )
                }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL（支持 DeepSeek 及任意 OpenAI 兼容网关）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                ConfigToggleRow(
                    title = "启用 DeepSeek 原生边思考边搜索",
                    subtitle = "官方端点自动使用 Messages API 协议启用 deepseek_web_search / deepseek_read_url 多步检索与引用",
                    checked = enableWebSearch,
                    onCheckedChange = { enableWebSearch = it }
                )
                ConfigToggleRow(
                    title = "启用流式输出 (SSE Streaming)",
                    subtitle = "实时打字机呈现思考流与最终回答",
                    checked = enableStreaming,
                    onCheckedChange = { enableStreaming = it }
                )
                ConfigToggleRow(
                    title = "展示模型思考过程",
                    subtitle = "在消息卡片中折叠展示 reasoning_content / <think>",
                    checked = showThinking,
                    onCheckedChange = { showThinking = it }
                )
                ConfigToggleRow(
                    title = "支持专辑封面识图 (Vision)",
                    subtitle = "允许向支持多模态的模型发送当前专辑封面",
                    checked = enableVision,
                    onCheckedChange = { enableVision = it }
                )
                ConfigToggleRow(
                    title = "新对话自动 AI 生成标题",
                    subtitle = "开启后每个新窗口首次提问将自动提炼标题，也可在窗口管理中手动重新生成",
                    checked = autoGenerateTitle,
                    onCheckedChange = { autoGenerateTitle = it }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Max Output Tokens Configuration (Default 384K / 393,216 + 64K / 128K / 256K / 384K Quick Presets)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("最大输出 Token 数 (max_tokens)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = formatTokenCountLabel(parsedMaxTokens ?: DEFAULT_AI_MAX_OUTPUT_TOKENS),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChampagneGold
                        )
                    }
                    OutlinedTextField(
                        value = maxTokensInput,
                        onValueChange = { maxTokensInput = it },
                        label = { Text("支持直接输入整数或简写（如 64K / 384K，默认 393216）") },
                        singleLine = true,
                        isError = maxTokensInput.isNotBlank() && parsedMaxTokens == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_config_max_tokens_input")
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AI_MAX_TOKEN_PRESETS.forEach { (presetTokens, presetLabel) ->
                            FilterChip(
                                selected = parsedMaxTokens == presetTokens,
                                onClick = { maxTokensInput = presetTokens.toString() },
                                label = { Text(presetLabel) },
                                modifier = Modifier.testTag("ai_max_tokens_preset_${presetLabel.lowercase()}")
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customSystemPrompt,
                    onValueChange = { customSystemPrompt = it },
                    label = { Text("自定义 System Prompt（留空使用 Auralis 声学人格）") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    val resolvedMaxTokens = parsedMaxTokens ?: aiConfig.maxOutputTokens
                    onSave(
                        aiConfig.copy(
                            activeProvider = provider,
                            deepSeekApiKey = apiKey.trim(),
                            deepSeekModelId = modelId.trim().ifBlank { "deepseek-flash" },
                            deepSeekBaseUrl = baseUrl.trim().ifBlank { "https://api.deepseek.com" },
                            enableVisionForCovers = enableVision,
                            enableStreaming = enableStreaming,
                            showThinkingProcess = showThinking,
                            autoGenerateSessionTitle = autoGenerateTitle,
                            enableWebSearch = enableWebSearch,
                            maxOutputTokens = resolvedMaxTokens,
                            customSystemPrompt = customSystemPrompt.trim()
                        )
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_config_save_button")
            ) {
                Text("保存模型配置")
            }
        }
    }
}

@Composable
private fun ConfigToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
