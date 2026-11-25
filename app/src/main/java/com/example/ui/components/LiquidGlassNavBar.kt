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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class LiquidGlassNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

/**
 * Apple iOS 26-inspired Floating Liquid Glass 4-Tab Navigation Capsule.
 * Renders a floating translucent pill with specular top rim refraction,
 * adaptive light/dark glass chemistry, and a spring-gliding active glass lens.
 */
@Composable
fun LiquidGlassNavBar(
    items: List<LiquidGlassNavItem>,
    selectedIndex: Int,
    darkTheme: Boolean,
    accentColor: Color,
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

    val glassSurfaceBrush = if (darkTheme) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF282838).copy(alpha = 0.86f),
                Color(0xFF171722).copy(alpha = 0.92f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.90f),
                Color(0xFFF3F3F9).copy(alpha = 0.84f)
            )
        )
    }

    val specularBorderBrush = if (darkTheme) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.20f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.98f),
                Color.Black.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.65f)
            )
        )
    }

    val activeLensBrush = if (darkTheme) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.24f),
                accentColor.copy(alpha = 0.20f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                accentColor.copy(alpha = 0.16f),
                accentColor.copy(alpha = 0.08f)
            )
        )
    }

    val activeLensBorder = if (darkTheme) {
        Color.White.copy(alpha = 0.32f)
    } else {
        accentColor.copy(alpha = 0.28f)
    }

    val activeTint = if (darkTheme) Color.White else accentColor
    val inactiveTint = if (darkTheme) Color.White.copy(alpha = 0.56f) else Color(0xFF4C4C5C)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = if (darkTheme) 24.dp else 18.dp,
                    shape = capsuleShape,
                    ambientColor = if (darkTheme) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.16f),
                    spotColor = if (darkTheme) accentColor.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.14f)
                )
                .clip(capsuleShape)
                .background(glassSurfaceBrush)
                .border(width = 1.dp, brush = specularBorderBrush, shape = capsuleShape)
                .padding(5.dp)
                .testTag("liquid_glass_nav_bar")
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
                        .border(0.85.dp, activeLensBorder, pillShape)
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
                            targetValue = if (selected) 1.08f else 0.96f,
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
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
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
