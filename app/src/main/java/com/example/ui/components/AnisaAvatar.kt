package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.anisa.AssistantState
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.HologramMagenta
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnisaAvatar(
    state: AssistantState,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "avatar_anim")

    // Continuous rotation for outer orbital rings
    val rotationAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.THINKING -> 1800
                    AssistantState.SPEAKING -> 4000
                    AssistantState.LISTENING -> 3000
                    else -> 8000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Breathing pulse
    val breathingPulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Color theme according to state
    val (primaryColor, secondaryColor, coreColor) = when (state) {
        AssistantState.LISTENING -> Triple(CyanNeon, Color(0xFF00B4D8), Color(0xFF90E0EF))
        AssistantState.THINKING -> Triple(ElectricViolet, HologramMagenta, Color(0xFFE2B4FF))
        AssistantState.SPEAKING -> Triple(HologramMagenta, CyanNeon, Color(0xFFFFFFFF))
        AssistantState.INTERRUPTED -> Triple(AmberGlow, Color(0xFFFF9E00), Color(0xFFFFE3A8))
        AssistantState.EXECUTING -> Triple(Color(0xFF48CAE4), ElectricViolet, Color(0xFFCAF0F8))
        AssistantState.ERROR, AssistantState.OFFLINE -> Triple(CoralError, Color(0xFF9D0208), Color(0xFFFFB3C1))
        else -> Triple(CyanNeon, ElectricViolet, Color(0xFFE0AAFF))
    }

    Box(
        modifier = modifier
            .size(220.dp)
            .testTag("anisa_avatar_presence"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.72f

            // Dynamic scaling factoring in real audio amplitude
            val audioBoost = (amplitude * 0.35f).coerceIn(0f, 0.45f)
            val activeRadius = baseRadius * (breathingPulse + audioBoost)

            // 1. Soft radial background aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.35f + amplitude * 0.25f),
                        secondaryColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = activeRadius * 1.5f
                ),
                radius = activeRadius * 1.5f,
                center = center
            )

            // 2. Outer Orbital Elliptical Ring (forward)
            rotate(rotationAngle, pivot = center) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.65f),
                    radius = activeRadius * 1.05f,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Tiny energy nodes on the ring
                for (i in 0 until 4) {
                    val angleRad = Math.toRadians((i * 90.0))
                    val nodeX = center.x + (activeRadius * 1.05f * cos(angleRad)).toFloat()
                    val nodeY = center.y + (activeRadius * 1.05f * sin(angleRad)).toFloat()
                    drawCircle(
                        color = coreColor,
                        radius = 4.dp.toPx(),
                        center = Offset(nodeX, nodeY)
                    )
                }
            }

            // 3. Second Inner Ring (counter-clockwise)
            rotate(-rotationAngle * 1.4f, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            secondaryColor.copy(alpha = 0.8f),
                            primaryColor.copy(alpha = 0.2f),
                            secondaryColor.copy(alpha = 0.8f)
                        ),
                        center = center
                    ),
                    radius = activeRadius * 0.82f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // 4. Luminous Central Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        coreColor.copy(alpha = 0.85f),
                        primaryColor.copy(alpha = 0.7f),
                        secondaryColor.copy(alpha = 0.2f)
                    ),
                    center = center,
                    radius = activeRadius * 0.55f
                ),
                radius = activeRadius * 0.55f,
                center = center
            )

            // 5. Central Diamond / Star Glint
            val starSize = 8.dp.toPx() * (1f + amplitude)
            drawLine(
                color = Color.White,
                start = Offset(center.x - starSize, center.y),
                end = Offset(center.x + starSize, center.y),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.White,
                start = Offset(center.x, center.y - starSize),
                end = Offset(center.x, center.y + starSize),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
