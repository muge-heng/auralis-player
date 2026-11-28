package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.blockUnderlyingTouches
import com.example.ui.theme.AuraViolet
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.EmeraldLossless
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RubyAccent
import com.example.ui.theme.RubyGlow
import com.example.util.AudioDefaultStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom vector/Canvas launch splash that replaces the harsh native Android 12+ splash transition
 * and seamlessly holds until Room DB + DataStore + initial artwork memory cache are hydrated.
 *
 * Uses ObsidianVoid (#09090D) to match values-v31/themes.xml windowSplashScreenBackground with zero flicker.
 */
@Composable
fun AuralisLaunchSplashOverlay(
    isBootReady: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emblemReveal = remember { Animatable(0f) }
    val wordmarkAlpha = remember { Animatable(0f) }
    val exitProgress = remember { Animatable(0f) }
    var minDurationElapsed by remember { mutableStateOf(false) }

    val pulseTransition = rememberInfiniteTransition(label = "splash_acoustic_pulse")
    val wavePhase by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "splash_wave_phase"
    )
    val ringBreath by pulseTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash_ring_breath"
    )

    LaunchedEffect(Unit) {
        launch {
            emblemReveal.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 220f
                )
            )
        }
        launch {
            delay(150L)
            wordmarkAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
            )
        }
        delay(620L)
        minDurationElapsed = true
    }

    LaunchedEffect(isBootReady, minDurationElapsed) {
        if (isBootReady && minDurationElapsed) {
            exitProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
            )
            onFinished()
        }
    }

    val exitT = exitProgress.value.coerceIn(0f, 1f)
    val overlayAlpha = (1f - exitT).coerceIn(0f, 1f)
    val exitScale = 1f + exitT * 0.055f

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = overlayAlpha
                scaleX = exitScale
                scaleY = exitScale
            }
            .background(ObsidianVoid)
            .blockUnderlyingTouches()
            .testTag("auralis_launch_splash"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = size.minDimension
            val center = Offset(size.width * 0.5f, size.height * 0.46f)
            val reveal = emblemReveal.value.coerceIn(0f, 1f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        RubyAccent.copy(alpha = 0.22f * reveal),
                        ChampagneGold.copy(alpha = 0.08f * reveal),
                        Color.Transparent
                    ),
                    center = center,
                    radius = minDim * 0.56f * ringBreath
                ),
                radius = minDim * 0.56f * ringBreath,
                center = center
            )

            val ringRadii = listOf(0.20f, 0.28f, 0.37f)
            ringRadii.forEachIndexed { idx, factor ->
                val radius = minDim * factor * (0.72f + 0.28f * reveal) * (1f + idx * 0.015f * sin(wavePhase + idx))
                val ringAlpha = (0.16f - idx * 0.042f).coerceAtLeast(0.03f) * reveal
                drawCircle(
                    color = if (idx == 0) RubyAccent.copy(alpha = ringAlpha * 1.4f)
                    else ChampagneGold.copy(alpha = ringAlpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = if (idx == 0) 2.2f else 1.4f)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 36.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .size(116.dp)
                    .graphicsLayer {
                        val s = 0.78f + 0.22f * emblemReveal.value
                        scaleX = s
                        scaleY = s
                        alpha = emblemReveal.value.coerceIn(0f, 1f)
                    }
            ) {
                drawAuralisMonogram(
                    wavePhase = wavePhase,
                    reveal = emblemReveal.value.coerceIn(0f, 1f)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "AURALIS",
                color = Color(0xFFF5F5F7),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 7.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = wordmarkAlpha.value
                    translationY = (1f - wordmarkAlpha.value) * 14f
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "私人无损声学唱片馆",
                color = ChampagneGold.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 2.5.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = wordmarkAlpha.value * 0.9f
                    translationY = (1f - wordmarkAlpha.value) * 10f
                }
            )
        }
    }
}

private fun DrawScope.drawAuralisMonogram(
    wavePhase: Float,
    reveal: Float
) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.5f

    drawRoundRect(
        color = Color(0xFF141720),
        topLeft = Offset(w * 0.04f, h * 0.04f),
        size = Size(w * 0.92f, h * 0.92f),
        cornerRadius = CornerRadius(w * 0.26f, h * 0.26f)
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.14f),
                RubyAccent.copy(alpha = 0.22f),
                ChampagneGold.copy(alpha = 0.16f)
            )
        ),
        topLeft = Offset(w * 0.04f, h * 0.04f),
        size = Size(w * 0.92f, h * 0.92f),
        cornerRadius = CornerRadius(w * 0.26f, h * 0.26f),
        style = Stroke(width = w * 0.016f)
    )

    val strokeW = w * 0.072f
    val aPath = Path().apply {
        moveTo(cx - w * 0.24f, cy + h * 0.24f)
        lineTo(cx, cy - h * 0.26f)
        lineTo(cx + w * 0.24f, cy + h * 0.24f)
    }
    drawPath(
        path = aPath,
        brush = Brush.verticalGradient(colors = listOf(RubyGlow, RubyAccent)),
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    val barWidth = w * 0.046f
    val barSpacing = w * 0.088f
    val barBaseY = cy + h * 0.21f
    val barHeights = listOf(
        h * (0.14f + 0.055f * sin(wavePhase)),
        h * (0.23f + 0.065f * sin(wavePhase + 1.15f)),
        h * (0.15f + 0.055f * sin(wavePhase + 2.3f))
    )
    barHeights.forEachIndexed { idx, bh ->
        val bx = cx + (idx - 1) * barSpacing
        val scaledH = bh * reveal
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(ChampagneGold, RubyGlow),
                startY = barBaseY - scaledH,
                endY = barBaseY
            ),
            start = Offset(bx, barBaseY),
            end = Offset(bx, barBaseY - scaledH),
            strokeWidth = barWidth,
            cap = StrokeCap.Round
        )
    }

    drawArc(
        brush = Brush.sweepGradient(
            colors = listOf(
                Color.Transparent,
                ChampagneGold.copy(alpha = 0.85f * reveal),
                RubyGlow.copy(alpha = 0.55f * reveal),
                Color.Transparent
            )
        ),
        startAngle = -145f,
        sweepAngle = 110f * reveal,
        useCenter = false,
        topLeft = Offset(w * 0.14f, h * 0.14f),
        size = Size(w * 0.72f, h * 0.72f),
        style = Stroke(width = w * 0.022f, cap = StrokeCap.Round)
    )
}

/**
 * Editorial 4-page first-launch onboarding experience:
 * - Page 0: Private Hi-Res Studio & Vinyl Acoustic Deck
 * - Page 1: Live Animated Word-by-Word Lyric Stage & Liquid Glass Preview
 * - Page 2: True Dual-Deck A/B Crossfade & Vocal Bridge Console
 * - Page 3: Dedicated 3-Card Permission Hub with inline per-card authorization buttons (never piled at the bottom)
 */
@Composable
fun AuralisOnboardingScreen(
    audioPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    audioDefaultStatus: AudioDefaultStatus,
    onRequestAudioPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onDefaultPlayerFeedback: (String) -> Unit,
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalPages = 4
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { totalPages })
    val scope = rememberCoroutineScope()
    var showDefaultPlayerDialog by remember { mutableStateOf(false) }

    if (showDefaultPlayerDialog) {
        DefaultAudioPlayerGuideDialog(
            status = audioDefaultStatus,
            onDismiss = { showDefaultPlayerDialog = false },
            onActionFeedback = onDefaultPlayerFeedback
        )
    }

    // Page-adaptive ambient aurora mood colors
    val primaryGlow by animateColorAsState(
        targetValue = when (pagerState.currentPage) {
            0 -> RubyAccent
            1 -> Color(0xFF4F6EF7)
            2 -> Color(0xFFD93860)
            else -> EmeraldLossless
        },
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "onboarding_primary_glow"
    )
    val secondaryGlow by animateColorAsState(
        targetValue = when (pagerState.currentPage) {
            0 -> ChampagneGold
            1 -> AuraViolet
            2 -> ChampagneGold
            else -> RubyAccent
        },
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "onboarding_secondary_glow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .blockUnderlyingTouches()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("auralis_onboarding_screen")
    ) {
        // Multi-layer editorial atmospheric aurora canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryGlow.copy(alpha = 0.24f), Color.Transparent),
                    center = Offset(w * 0.82f, h * 0.16f),
                    radius = size.minDimension * 0.78f
                ),
                radius = size.minDimension * 0.78f,
                center = Offset(w * 0.82f, h * 0.16f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondaryGlow.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(w * 0.12f, h * 0.74f),
                    radius = size.minDimension * 0.68f
                ),
                radius = size.minDimension * 0.68f,
                center = Offset(w * 0.12f, h * 0.74f)
            )
            // Subtle architectural studio hairline grid accent
            drawLine(
                color = Color.White.copy(alpha = 0.04f),
                start = Offset(0f, h * 0.56f),
                end = Offset(w, h * 0.56f),
                strokeWidth = 1f
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .align(Alignment.Center)
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            // Top segmented hairline progress tracks
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(totalPages) { idx ->
                    val fillFraction by animateFloatAsState(
                        targetValue = when {
                            idx < pagerState.currentPage -> 1f
                            idx == pagerState.currentPage -> 1f
                            else -> 0f
                        },
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        label = "seg_progress_$idx"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable { scope.launch { pagerState.animateScrollToPage(idx) } }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fillFraction)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(
                                    if (idx == pagerState.currentPage) {
                                        Brush.horizontalGradient(listOf(RubyAccent, ChampagneGold))
                                    } else {
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.62f),
                                                Color.White.copy(alpha = 0.62f)
                                            )
                                        )
                                    }
                                )
                        )
                    }
                }
            }

            // Editorial brand header + step counter + subtle skip pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(primaryGlow)
                    )
                    Text(
                        text = "AURALIS",
                        color = Color(0xFFF5F5F7),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                    Text(
                        text = "0${pagerState.currentPage + 1} / 0$totalPages",
                        color = Color(0xFF8C8CA4),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                        .clickable { onFinishOnboarding() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("onboarding_top_skip")
                ) {
                    Text(
                        text = "跳过",
                        color = Color(0xFFD5D5E2),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Horizontal Pager with subtle parallax depth
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    .coerceIn(-1f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 1f - abs(pageOffset) * 0.35f
                            translationX = pageOffset * 28f
                        }
                ) {
                    when (page) {
                        0 -> OnboardingStudioShowcasePage()
                        1 -> OnboardingLyricStageShowcasePage()
                        2 -> OnboardingDualDeckShowcasePage()
                        else -> OnboardingPermissionsHubPage(
                            audioPermissionGranted = audioPermissionGranted,
                            notificationPermissionGranted = notificationPermissionGranted,
                            audioDefaultStatus = audioDefaultStatus,
                            onRequestAudioPermission = onRequestAudioPermission,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onOpenDefaultPlayerGuide = { showDefaultPlayerDialog = true }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Clean Bottom Navigation Bar (Back + Single Primary CTA — permission buttons live INSIDE cards on Page 4!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF161722))
                            .border(0.75.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }
                            .testTag("onboarding_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "上一页",
                            tint = Color(0xFFE6E6F0),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                val isLastPage = pagerState.currentPage == totalPages - 1
                Button(
                    onClick = {
                        if (!isLastPage) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            onFinishOnboarding()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLastPage && audioPermissionGranted) EmeraldLossless else RubyAccent,
                        contentColor = if (isLastPage && audioPermissionGranted) Color(0xFF061B12) else Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag(if (isLastPage) "onboarding_enter_button" else "onboarding_next_button")
                ) {
                    Text(
                        text = when {
                            !isLastPage && pagerState.currentPage == 0 -> "探索逐字歌词舞台"
                            !isLastPage && pagerState.currentPage == 1 -> "了解双路智能过渡"
                            !isLastPage -> "前往权限配置"
                            audioPermissionGranted -> "进入 Auralis"
                            else -> "暂不授权，直接进入"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingStudioShowcasePage() {
    val infinite = rememberInfiniteTransition(label = "ob_studio")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Restart),
        label = "ob_studio_phase"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        // Editorial Vinyl + Floating Glass Telemetry Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(238.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF151622), Color(0xFF0E0F17))
                    )
                )
                .border(0.8.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
                .padding(18.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val discCenter = Offset(size.width * 0.72f, size.height * 0.48f)
                val maxR = size.minDimension * 0.56f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(RubyAccent.copy(alpha = 0.34f), Color.Transparent),
                        center = discCenter,
                        radius = maxR * 1.15f
                    ),
                    radius = maxR * 1.15f,
                    center = discCenter
                )
                listOf(0.96f, 0.80f, 0.65f, 0.50f, 0.36f).forEachIndexed { i, f ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f + i * 0.025f),
                        radius = maxR * f,
                        center = discCenter,
                        style = Stroke(width = 1.5f)
                    )
                }
                val sweepAngle = (phase * 180f / PI.toFloat()) % 360f
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(Color.Transparent, ChampagneGold, RubyAccent, Color.Transparent),
                        center = discCenter
                    ),
                    startAngle = sweepAngle,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(discCenter.x - maxR * 0.82f, discCenter.y - maxR * 0.82f),
                    size = Size(maxR * 1.64f, maxR * 1.64f),
                    style = Stroke(width = 3.2f, cap = StrokeCap.Round)
                )
                drawCircle(
                    brush = Brush.linearGradient(listOf(RubyGlow, RubyAccent)),
                    radius = maxR * 0.27f,
                    center = discCenter
                )
                drawCircle(
                    color = ObsidianVoid,
                    radius = maxR * 0.075f,
                    center = discCenter
                )

                // Live 10-band studio spectrum along bottom-left
                val barCount = 10
                val startX = size.width * 0.04f
                val baseY = size.height * 0.88f
                val spacing = size.width * 0.044f
                for (i in 0 until barCount) {
                    val hNorm = 0.25f + 0.75f * (0.5f + 0.5f * sin(phase * 1.7f + i * 0.65f))
                    val bh = size.height * 0.24f * hNorm
                    val bx = startX + i * spacing
                    drawLine(
                        brush = Brush.verticalGradient(
                            colors = listOf(ChampagneGold, RubyAccent),
                            startY = baseY - bh,
                            endY = baseY
                        ),
                        start = Offset(bx, baseY),
                        end = Offset(bx, baseY - bh),
                        strokeWidth = 6.5f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Floating Frosted Acoustic Spec Pill (top-left asymmetric anchor)
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1B1C2A).copy(alpha = 0.86f))
                    .border(0.7.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = ChampagneGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "HI-RES MASTER",
                        color = ChampagneGold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "FLAC · 24-bit / 192kHz",
                    color = Color(0xFFF2F2F8),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "10 段硬件级均衡器 · 动态声场",
                    color = Color(0xFF9C9CB2),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "PRIVATE ACOUSTIC LIBRARY",
            color = ChampagneGold,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = buildAnnotatedString {
                append("为本地无损而生的\n")
                withStyle(SpanStyle(color = RubyGlow)) { append("私人声学唱片馆") }
            },
            color = Color(0xFFF5F5F7),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "从 FLAC、WAV 到 Hi-Res 母带，原生解析高规格音频、内嵌封面与元数据。搭配独立普通音频区与智能排序，让音乐珍藏始终井然有序。",
            color = Color(0xFFA4A4B8),
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OnboardingSpecPill("无损 / Hi-Res 分级", ChampagneGold)
            OnboardingSpecPill("10 段硬件 EQ", RubyGlow)
            OnboardingSpecPill("普通音频隔离区", EmeraldLossless)
            OnboardingSpecPill("动态智能排序", Color(0xFFA78BFA))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingLyricStageShowcasePage() {
    val infinite = rememberInfiniteTransition(label = "ob_lyrics")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "ob_lyric_sweep"
    )
    val breath by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "ob_dot_breath"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        // Miniature Live Liquid-Glass Lyric Stage Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(238.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF121A36), Color(0xFF17132B), Color(0xFF0E101A))
                    )
                )
                .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Dimmed previous line (depth blur vibe)
                Text(
                    text = "把星子放入眸",
                    color = Color.White.copy(alpha = 0.24f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // Live 3-dot breathing interlude indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { idx ->
                        val local = (sweep * 3f - idx).coerceIn(0f, 1f)
                        val locked = local >= 0.99f
                        val scale = if (locked) 1f else 0.78f + 0.22f * (0.5f + 0.5f * sin(breath + idx * 0.9f))
                        val alpha = 0.28f + 0.72f * local
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                }
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }

                // Active word-by-word sweeping lyric line
                val activeText = "在无损声场中自由呼吸"
                val litChars = (activeText.length * sweep).toInt().coerceIn(1, activeText.length)
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White)) {
                            append(activeText.substring(0, litChars))
                        }
                        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.34f))) {
                            append(activeText.substring(litChars))
                        }
                    },
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Dimmed upcoming line + Liquid Glass badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "每一帧光影皆随乐而动",
                        color = Color.White.copy(alpha = 0.30f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(0.6.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Liquid Glass",
                            color = Color(0xFFD6E0FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "WORD-BY-WORD STAGE",
            color = Color(0xFF93B4FF),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = buildAnnotatedString {
                append("液态玻璃美学与\n")
                withStyle(SpanStyle(color = Color(0xFF93B4FF))) { append("逐字歌词沉浸舞台") }
            },
            color = Color(0xFFF5F5F7),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "支持多源在线聚合与本地 .qrc/.yrc/.krc/.lrc/.ttml 逐字歌词，配备弹性波浪切行、前尾奏呼吸点、沉浸全屏及可跟手滑选的 Liquid Glass 悬浮底栏。",
            color = Color(0xFFA4A4B8),
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OnboardingSpecPill("逐字扫光与弹性波浪", Color(0xFF93B4FF))
            OnboardingSpecPill("间奏动态呼吸点", ChampagneGold)
            OnboardingSpecPill("底栏跟手滑选", RubyGlow)
            OnboardingSpecPill("歌词刻录导出", EmeraldLossless)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingDualDeckShowcasePage() {
    val infinite = rememberInfiniteTransition(label = "ob_dualdeck")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "ob_deck_phase"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        // Dual-Deck A/B Crossfade & Vocal Bridge Console
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(238.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1A1420), Color(0xFF10111A))
                    )
                )
                .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeckBadgeChip("DECK A · 渐弱收尾", RubyGlow)
                    DeckBadgeChip("DECK B · 预解码接管", ChampagneGold)
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(124.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val midY = h * 0.5f

                    // Subtle center bridge guide line
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(w * 0.5f, h * 0.08f),
                        end = Offset(w * 0.5f, h * 0.92f),
                        strokeWidth = 1.5f
                    )

                    val steps = 60
                    val pathA = Path()
                    val pathB = Path()
                    for (i in 0..steps) {
                        val t = i / steps.toFloat()
                        val x = t * w
                        val envA = cos(t * PI * 0.5).toFloat().coerceIn(0f, 1f)
                        val envB = sin(t * PI * 0.5).toFloat().coerceIn(0f, 1f)
                        val yA = midY + sin(t * 12f - phase) * h * 0.34f * envA
                        val yB = midY + cos(t * 12f - phase) * h * 0.34f * envB
                        if (i == 0) {
                            pathA.moveTo(x, yA)
                            pathB.moveTo(x, yB)
                        } else {
                            pathA.lineTo(x, yA)
                            pathB.lineTo(x, yB)
                        }
                    }
                    drawPath(
                        path = pathA,
                        brush = Brush.horizontalGradient(listOf(RubyAccent, RubyGlow.copy(alpha = 0.08f))),
                        style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = pathB,
                        brush = Brush.horizontalGradient(listOf(ChampagneGold.copy(alpha = 0.08f), ChampagneGold)),
                        style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                    )

                    // Equal-power intersection glowing node
                    val nodePulse = 0.85f + 0.22f * sin(phase * 1.4f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.85f), RubyGlow.copy(alpha = 0.25f), Color.Transparent),
                            center = Offset(w * 0.5f, midY),
                            radius = 24f * nodePulse
                        ),
                        radius = 24f * nodePulse,
                        center = Offset(w * 0.5f, midY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 5.5f,
                        center = Offset(w * 0.5f, midY)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "人声接唱 · 跳过尾奏与下首前奏",
                        color = Color(0xFFE6E6F2),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "0ms 顿挫",
                        color = EmeraldLossless,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "DUAL-DECK ENGINE & STUDIO AI",
            color = RubyGlow,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = buildAnnotatedString {
                append("真双路热切换过渡\n")
                withStyle(SpanStyle(color = ChampagneGold)) { append("与懂音乐的 AI 伴侣") }
            },
            color = Color(0xFFF5F5F7),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "内置 Deck A/B 双路引擎，提前预解码下首曲目实现零卡顿等功率叠化与「人声接唱」；随时唤起 AI 乐评与调音建议，或在应用内自由编辑曲目信息与封面。",
            color = Color(0xFFA4A4B8),
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OnboardingSpecPill("等功率交叉淡化", RubyGlow)
            OnboardingSpecPill("人声接唱模式", ChampagneGold)
            OnboardingSpecPill("曲目信息与封面编辑", EmeraldLossless)
            OnboardingSpecPill("AI 乐评建单", Color(0xFFA78BFA))
        }
    }
}

@Composable
private fun OnboardingPermissionsHubPage(
    audioPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    audioDefaultStatus: AudioDefaultStatus,
    onRequestAudioPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenDefaultPlayerGuide: () -> Unit
) {
    val readyCount = listOf(
        audioPermissionGranted,
        notificationPermissionGranted,
        audioDefaultStatus is AudioDefaultStatus.AlreadyAuralis
    ).count { it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PERMISSIONS & PRIVACY",
                color = EmeraldLossless,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(EmeraldLossless.copy(alpha = 0.14f))
                    .border(0.6.dp, EmeraldLossless.copy(alpha = 0.36f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "已配置 $readyCount / 3 项",
                    color = EmeraldLossless,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = buildAnnotatedString {
                append("按需逐项授权\n")
                withStyle(SpanStyle(color = EmeraldLossless)) { append("每一项都清晰可控") }
            },
            color = Color(0xFFF5F5F7),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "你可以分别点按下方每张卡片右侧按钮按需开启，也可全部跳过直接进入，后续随时通过系统文件选择器零权限导入或前往「工坊」补开。",
            color = Color(0xFFA4A4B8),
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OnboardingPermissionActionCard(
                icon = Icons.Rounded.AudioFile,
                accent = RubyAccent,
                title = "读取本地音频",
                tag = "推荐",
                description = "授权后可随时在曲库中按你的筛选规则手动扫描无损音频，不会自动扫描，文件仅本地读取。",
                actionLabel = "去授权",
                grantedLabel = "已授权",
                isGranted = audioPermissionGranted,
                onAction = onRequestAudioPermission,
                buttonTestTag = "onboarding_perm_audio_btn"
            )

            OnboardingPermissionActionCard(
                icon = Icons.Rounded.NotificationsActive,
                accent = ChampagneGold,
                title = "媒体播放通知",
                tag = "推荐",
                description = "在锁屏与通知栏呈现实时封面、进度与切歌控制，保障后台连续播放稳定运行。",
                actionLabel = "去开启",
                grantedLabel = "已开启",
                isGranted = notificationPermissionGranted,
                onAction = onRequestNotificationPermission,
                buttonTestTag = "onboarding_perm_notification_btn"
            )

            val isAlreadyDefaultAudio = audioDefaultStatus is AudioDefaultStatus.AlreadyAuralis
            OnboardingPermissionActionCard(
                icon = Icons.Rounded.OpenInNew,
                accent = Color(0xFF93B4FF),
                title = "默认音频播放器",
                tag = "可选",
                description = when (audioDefaultStatus) {
                    AudioDefaultStatus.AlreadyAuralis -> "Auralis 已是系统默认音频播放器，在文件管理器点按音频即可直接播放。"
                    is AudioDefaultStatus.LockedByOther -> "当前默认由「${audioDefaultStatus.appLabel}」接管，点右侧查看切换指引与系统应用入口。"
                    AudioDefaultStatus.NoDefaultLocked -> "唤起系统音频打开方式（选择 Auralis 始终）或按本机品牌路径前往应用设置。"
                },
                actionLabel = "去设置",
                grantedLabel = "已是默认",
                isGranted = isAlreadyDefaultAudio,
                onAction = onOpenDefaultPlayerGuide,
                buttonTestTag = "onboarding_perm_default_app_btn"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Concise privacy assurance bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF13151F))
                .border(0.6.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = EmeraldLossless,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "纯本地离线优先架构，无账号体系、无行为追踪，不上传任何音频文件。",
                color = Color(0xFF9C9CB2),
                style = MaterialTheme.typography.labelSmall,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun OnboardingPermissionActionCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    tag: String,
    description: String,
    actionLabel: String,
    grantedLabel: String,
    isGranted: Boolean,
    onAction: () -> Unit,
    buttonTestTag: String
) {
    val borderColor by animateColorAsState(
        targetValue = if (isGranted) EmeraldLossless.copy(alpha = 0.48f) else Color.White.copy(alpha = 0.10f),
        animationSpec = tween(260),
        label = "perm_card_border"
    )
    val cardBg by animateColorAsState(
        targetValue = if (isGranted) Color(0xFF101D1A) else Color(0xFF141520),
        animationSpec = tween(260),
        label = "perm_card_bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(0.85.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background((if (isGranted) EmeraldLossless else accent).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) EmeraldLossless else accent,
                modifier = Modifier.size(21.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    color = Color(0xFFF5F5F7),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = tag,
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = description,
                color = Color(0xFF9EA0B4),
                style = MaterialTheme.typography.labelSmall,
                lineHeight = 16.sp
            )
        }

        // Dedicated inline per-card authorization button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isGranted) EmeraldLossless.copy(alpha = 0.18f)
                    else accent.copy(alpha = 0.22f)
                )
                .border(
                    width = 0.8.dp,
                    color = if (isGranted) EmeraldLossless.copy(alpha = 0.55f) else accent.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onAction)
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .testTag(buttonTestTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isGranted) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = EmeraldLossless,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = if (isGranted) grantedLabel else actionLabel,
                color = if (isGranted) EmeraldLossless else Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OnboardingSpecPill(
    label: String,
    accent: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.12f))
            .border(0.65.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            text = label,
            color = Color(0xFFEAEAF4),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DeckBadgeChip(
    label: String,
    accent: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.15f))
            .border(0.6.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            text = label,
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Celebratory one-shot welcome animation played right after completing first-launch onboarding,
 * expanding a harmonic ruby/champagne acoustic ring before revealing the main library.
 */
@Composable
fun FirstLaunchWelcomeOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bloom = remember { Animatable(0f) }
    val fadeOut = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        bloom.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
        )
        delay(260L)
        fadeOut.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
        )
        onFinished()
    }

    val t = bloom.value.coerceIn(0f, 1f)
    val alphaOut = (1f - fadeOut.value).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = alphaOut
                val s = 1f + fadeOut.value * 0.06f
                scaleX = s
                scaleY = s
            }
            .background(ObsidianVoid)
            .blockUnderlyingTouches()
            .testTag("first_launch_welcome_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.48f)
            val maxR = size.maxDimension * 0.55f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        RubyAccent.copy(alpha = 0.32f * (1f - t * 0.35f)),
                        ChampagneGold.copy(alpha = 0.16f * t),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR * (0.28f + 0.72f * t)
                ),
                radius = maxR * (0.28f + 0.72f * t),
                center = center
            )

            listOf(0f, 0.22f).forEachIndexed { idx, delayOffset ->
                val p = ((t - delayOffset) / (1f - delayOffset)).coerceIn(0f, 1f)
                if (p > 0f) {
                    drawCircle(
                        color = (if (idx == 0) RubyGlow else ChampagneGold)
                            .copy(alpha = (1f - p) * 0.55f),
                        radius = size.minDimension * (0.14f + 0.58f * p),
                        center = center,
                        style = Stroke(width = 3.2f * (1f - p * 0.5f))
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                alpha = t
                translationY = (1f - t) * 20f
            }
        ) {
            Canvas(modifier = Modifier.size(88.dp)) {
                drawAuralisMonogram(wavePhase = t * 4f, reveal = t)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "欢迎进入 Auralis",
                color = Color(0xFFF5F5F7),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "你的私人无损声场已就绪",
                color = ChampagneGold.copy(alpha = 0.90f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
