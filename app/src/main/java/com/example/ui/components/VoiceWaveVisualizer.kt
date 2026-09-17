package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.anisa.AssistantState
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.HologramMagenta
import kotlin.math.sin

@Composable
fun VoiceWaveVisualizer(
    state: AssistantState,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val animatedAmp by animateFloatAsState(
        targetValue = amplitude.coerceIn(0.04f, 1.0f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "amplitude_spring"
    )

    val waveColors = when (state) {
        AssistantState.LISTENING -> listOf(CyanNeon, CyanGlow, Color(0xFF48CAE4))
        AssistantState.SPEAKING -> listOf(HologramMagenta, ElectricViolet, CyanNeon)
        AssistantState.INTERRUPTED -> listOf(AmberGlow, Color(0xFFFFB703), Color(0xFFFF9E00))
        else -> listOf(CyanNeon.copy(alpha = 0.5f), ElectricViolet.copy(alpha = 0.5f))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("voice_waveform_visualizer"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            val barCount = 21
            val totalWidth = size.width
            val barWidth = 4.dp.toPx()
            val totalBarsWidth = barCount * barWidth
            val spacing = (totalWidth - totalBarsWidth) / (barCount + 1)
            val centerY = size.height / 2f
            val maxBarHeight = size.height * 0.85f

            val brush = Brush.verticalGradient(
                colors = waveColors,
                startY = 0f,
                endY = size.height
            )

            for (i in 0 until barCount) {
                val progress = i.toFloat() / (barCount - 1) // 0..1
                // Bell curve multiplier centered in middle
                val bell = sin(progress * Math.PI).toFloat()

                // When user speaks vs Anisa speaks directional emphasis
                val dirOffset = when (state) {
                    AssistantState.LISTENING -> (progress * 0.3f)
                    AssistantState.SPEAKING -> ((1f - progress) * 0.3f)
                    else -> 0f
                }

                val dynamicHeight = (maxBarHeight * bell * (animatedAmp + dirOffset) * 0.9f)
                    .coerceIn(4.dp.toPx(), maxBarHeight)

                val x = spacing + i * (barWidth + spacing)
                val top = centerY - (dynamicHeight / 2f)

                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(x, top),
                    size = Size(barWidth, dynamicHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}
