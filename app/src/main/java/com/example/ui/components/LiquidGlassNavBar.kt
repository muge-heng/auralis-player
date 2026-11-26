package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AuraPalette
import kotlin.math.roundToInt

data class LiquidGlassNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

/**
 * Apple iOS 26-inspired Floating Transparent Liquid Glass 4-Tab Navigation Capsule.
 * Renders a completely transparent floating pill over full-bleed scrolling content
 * with real-time GPU backdrop blur/refraction, specular rim gleam, and a spring-gliding glass lens.
 */
@Composable
fun LiquidGlassNavBar(
    items: List<LiquidGlassNavItem>,
    selectedIndex: Int,
    darkTheme: Boolean,
    accentColor: Color,
    auraPalette: AuraPalette = AuraPalette(),
    backdropState: LiquidGlassBackdropState? = null,
    visualEffectsEnhanced: Boolean = false,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val clampedIndex = selectedIndex.coerceIn(0, items.lastIndex)
    val animatedIndex by animateFloatAsState(
        targetValue = clampedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 340f
        ),
        label = "liquid_glass_tab_indicator"
    )

    val capsuleShape = RoundedCornerShape(32.dp)
    val pillShape = RoundedCornerShape(26.dp)
    val dynamicAuraAccent = Color(auraPalette.primaryHex)

    val activeLensBrush = if (darkTheme) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (visualEffectsEnhanced) 0.28f else 0.22f),
                (if (visualEffectsEnhanced) dynamicAuraAccent else accentColor).copy(
                    alpha = if (visualEffectsEnhanced) 0.28f else 0.18f
                )
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (visualEffectsEnhanced) 0.76f else 0.64f),
                (if (visualEffectsEnhanced) dynamicAuraAccent else accentColor).copy(
                    alpha = if (visualEffectsEnhanced) 0.18f else 0.12f
                )
            )
        )
    }

    val activeLensBorder = if (darkTheme) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (visualEffectsEnhanced) 0.50f else 0.36f),
                (if (visualEffectsEnhanced) dynamicAuraAccent else accentColor).copy(alpha = 0.28f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.96f),
                accentColor.copy(alpha = 0.30f)
            )
        )
    }

    val activeTint = if (darkTheme) Color.White else accentColor
    val inactiveTint = if (darkTheme) Color.White.copy(alpha = 0.66f) else Color(0xFF3A3A4E)
    val labelShadow = if (darkTheme) {
        Shadow(color = Color.Black.copy(alpha = 0.42f), offset = Offset(0f, 1f), blurRadius = 3f)
    } else {
        Shadow(color = Color.White.copy(alpha = 0.75f), offset = Offset(0f, 1f), blurRadius = 3f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassSurface(
            backdropState = backdropState,
            cornerRadius = 32.dp,
            darkTheme = darkTheme,
            accentColor = if (visualEffectsEnhanced) dynamicAuraAccent else accentColor,
            visualEffectsEnhanced = visualEffectsEnhanced,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = if (visualEffectsEnhanced) {
                        if (darkTheme) 24.dp else 18.dp
                    } else {
                        if (darkTheme) 16.dp else 12.dp
                    },
                    shape = capsuleShape,
                    ambientColor = if (darkTheme) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.14f),
                    spotColor = if (darkTheme) {
                        (if (visualEffectsEnhanced) dynamicAuraAccent else accentColor).copy(alpha = 0.30f)
                    } else {
                        Color.Black.copy(alpha = 0.12f)
                    }
                )
                .testTag("liquid_glass_nav_bar")
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
            ) {
                val slotWidth = maxWidth / items.size
                val slotWidthPx = constraints.maxWidth.toFloat() / items.size.toFloat()

                // Spring-gliding Liquid Glass Lens behind selected tab
                Box(
                    modifier = Modifier
                        .offset { IntOffset((animatedIndex * slotWidthPx).roundToInt(), 0) }
                        .width(slotWidth)
                        .fillMaxHeight()
                        .clip(pillShape)
                        .background(activeLensBrush)
                        .border(0.9.dp, activeLensBorder, pillShape)
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { idx, item ->
                        val selected = idx == clampedIndex
                        val tint by animateColorAsState(
                            targetValue = if (selected) activeTint else inactiveTint,
                            animationSpec = tween(220),
                            label = "glass_tab_tint_$idx"
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.09f else 0.96f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "glass_tab_scale_$idx"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(pillShape)
                                .bouncyClickable(pressedScale = 0.92f) {
                                    onSelectIndex(idx)
                                }
                                .testTag(item.testTag),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = tint,
                                modifier = Modifier.size((21f * iconScale).dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    shadow = labelShadow
                                ),
                                color = tint,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
