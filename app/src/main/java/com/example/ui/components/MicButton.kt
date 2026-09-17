package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.anisa.AssistantState
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.HologramMagenta

@Composable
fun MicButton(
    state: AssistantState,
    isMicActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val (btnGradient, iconColor) = when {
        state == AssistantState.LISTENING -> Pair(
            listOf(CyanNeon, Color(0xFF00B4D8)),
            Color(0xFF04131A)
        )
        state == AssistantState.SPEAKING -> Pair(
            listOf(HologramMagenta, ElectricViolet),
            Color.White
        )
        state == AssistantState.INTERRUPTED -> Pair(
            listOf(AmberGlow, Color(0xFFFF9E00)),
            Color(0xFF261800)
        )
        state == AssistantState.ERROR || state == AssistantState.OFFLINE -> Pair(
            listOf(CoralError, Color(0xFFB00020)),
            Color.White
        )
        else -> Pair(
            listOf(ElectricViolet, Color(0xFF4A00E0)),
            Color.White
        )
    }

    Box(
        modifier = modifier
            .size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring when listening or speaking
        if (state == AssistantState.LISTENING || state == AssistantState.SPEAKING) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .border(2.dp, btnGradient.first().copy(alpha = 0.5f), CircleShape)
            )
        }

        // Central Mic Button
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(btnGradient))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = 32.dp),
                    onClick = onClick
                )
                .testTag("mic_control_button"),
            contentAlignment = Alignment.Center
        ) {
            val icon = when {
                state == AssistantState.SPEAKING -> Icons.Default.Stop
                state == AssistantState.LISTENING -> Icons.Default.Mic
                !isMicActive -> Icons.Default.MicOff
                else -> Icons.Default.Mic
            }

            Icon(
                imageVector = icon,
                contentDescription = if (isMicActive) "Microphone active" else "Microphone muted",
                tint = iconColor,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
