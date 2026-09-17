package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anisa.AnisaScreen
import com.example.anisa.AnisaUiState
import com.example.anisa.AssistantState
import com.example.ui.components.AnisaAvatar
import com.example.ui.components.MicButton
import com.example.ui.components.TaskProgressCard
import com.example.ui.components.VoiceWaveVisualizer
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextWhite

@Composable
fun HomeScreen(
    uiState: AnisaUiState,
    onToggleMic: () -> Unit,
    onStopOperation: () -> Unit,
    onPromptSelected: (String) -> Unit,
    onNavigate: (AnisaScreen) -> Unit,
    onCompleteTaskStep: (com.example.data.entity.TaskEntity) -> Unit,
    onCancelTask: (com.example.data.entity.TaskEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // --- Top App Header & Status Bar ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when (uiState.state) {
                                AssistantState.LISTENING -> CyanNeon
                                AssistantState.SPEAKING -> ElectricViolet
                                AssistantState.OFFLINE, AssistantState.ERROR -> CoralError
                                AssistantState.INTERRUPTED -> AmberGlow
                                else -> CyanNeon.copy(alpha = 0.6f)
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ANISA",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = TextWhite,
                    letterSpacing = 2.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!uiState.isNetworkConnected) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CoralError.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = "Offline",
                            tint = CoralError,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "OFFLINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CoralError
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(
                    onClick = { onNavigate(AnisaScreen.MEMORY) },
                    modifier = Modifier.testTag("nav_memory_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "Memories",
                        tint = CyanNeon
                    )
                }

                IconButton(
                    onClick = { onNavigate(AnisaScreen.HISTORY) },
                    modifier = Modifier.testTag("nav_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Chat History",
                        tint = TextSecondary
                    )
                }

                IconButton(
                    onClick = { onNavigate(AnisaScreen.SETTINGS) },
                    modifier = Modifier.testTag("nav_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Active Task Progress Banner (if any) ---
        TaskProgressCard(
            task = uiState.activeTask,
            onCompleteStep = onCompleteTaskStep,
            onCancel = onCancelTask
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Dynamic Central AI Avatar ---
        AnisaAvatar(
            state = uiState.state,
            amplitude = uiState.audioAmplitude
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Status Badge ---
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(ObsidianCard)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = when (uiState.state) {
                    AssistantState.LISTENING -> CyanNeon
                    AssistantState.SPEAKING -> ElectricViolet
                    AssistantState.INTERRUPTED -> AmberGlow
                    AssistantState.ERROR, AssistantState.OFFLINE -> CoralError
                    else -> TextSecondary
                },
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- Live Speech Transcript / Current Utterance Pill ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(ObsidianCard, ObsidianCard.copy(alpha = 0.7f))
                    )
                )
                .border(1.dp, ObsidianBorder.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            val displaySpeech = when {
                uiState.liveTranscript.isNotBlank() -> "\"${uiState.liveTranscript}\""
                uiState.currentUtterance.isNotBlank() -> "\"${uiState.currentUtterance}\""
                else -> "\"Say 'Anisa' or tap the mic to speak.\""
            }

            Text(
                text = displaySpeech,
                style = MaterialTheme.typography.bodyLarge,
                color = if (uiState.liveTranscript.isNotBlank()) CyanNeon else TextWhite,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- Realtime Audio Waveform Visualizer ---
        VoiceWaveVisualizer(
            state = uiState.state,
            amplitude = uiState.audioAmplitude
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Quick Voice Prompts (Horizontal Chips) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val quickPrompts = listOf(
                "Organize my day",
                "Tell me about Iron Man",
                "What's the weather?",
                "I finished the project!",
                "Quick answer please",
                "What do you remember?"
            )
            quickPrompts.forEach { prompt ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianCard)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                        .clickable { onPromptSelected(prompt) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = prompt,
                            fontSize = 12.sp,
                            color = TextWhite,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Bottom Interaction Controls ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visible Cancel/Stop action button when active
            AnimatedVisibility(
                visible = uiState.state == AssistantState.SPEAKING || uiState.state == AssistantState.THINKING || uiState.state == AssistantState.EXECUTING,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(CoralError.copy(alpha = 0.2f))
                        .border(1.dp, CoralError, CircleShape)
                        .clickable { onStopOperation() }
                        .testTag("stop_action_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop operation",
                        tint = CoralError,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Central Interactive 68dp Microphone
            MicButton(
                state = uiState.state,
                isMicActive = uiState.isMicActive,
                onClick = onToggleMic
            )

            // Tasks Navigation Shortcut
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(ObsidianCard)
                    .border(1.dp, ObsidianBorder, CircleShape)
                    .clickable { onNavigate(AnisaScreen.TASKS) }
                    .testTag("nav_tasks_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Tasks",
                    tint = CyanNeon,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
