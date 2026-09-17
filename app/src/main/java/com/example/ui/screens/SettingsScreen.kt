package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.SettingsEntity
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextWhite
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: SettingsEntity,
    onBack: () -> Unit,
    onSaveSettings: (SettingsEntity) -> Unit,
    onClearAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var speechRate by remember(settings.speechRate) { mutableFloatStateOf(settings.speechRate) }
    var speechPitch by remember(settings.speechPitch) { mutableFloatStateOf(settings.speechPitch) }
    var humorLevel by remember(settings.humorLevel) { mutableFloatStateOf(settings.humorLevel) }
    var sassLevel by remember(settings.sassLevel) { mutableFloatStateOf(settings.sassLevel) }
    var verbosity by remember(settings.verbosity) { mutableFloatStateOf(settings.verbosity) }
    var wakeWordEnabled by remember(settings.wakeWordEnabled) { mutableStateOf(settings.wakeWordEnabled) }
    var hapticFeedback by remember(settings.hapticFeedback) { mutableStateOf(settings.hapticFeedback) }
    var isPcPaired by remember(settings.isPcPaired) { mutableStateOf(settings.isPcPaired) }

    val scrollState = rememberScrollState()

    fun persistChanges() {
        onSaveSettings(
            settings.copy(
                speechRate = speechRate,
                speechPitch = speechPitch,
                humorLevel = humorLevel,
                sassLevel = sassLevel,
                verbosity = verbosity,
                wakeWordEnabled = wakeWordEnabled,
                hapticFeedback = hapticFeedback,
                isPcPaired = isPcPaired
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("settings_screen")
    ) {
        // --- Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextWhite
                )
            }
            Text(
                text = "Assistant Preferences",
                style = MaterialTheme.typography.titleLarge,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Voice Characteristics Section ---
        SettingsSectionHeader(title = "VOICE ENGINE", icon = Icons.Default.RecordVoiceOver)

        SettingsCard {
            Text(
                text = "Speaking Speed: ${String.format("%.2f", speechRate)}x",
                style = MaterialTheme.typography.bodyMedium,
                color = TextWhite
            )
            Slider(
                value = speechRate,
                onValueChange = { speechRate = it; persistChanges() },
                valueRange = 0.8f..1.5f,
                colors = SliderDefaults.colors(
                    thumbColor = CyanNeon,
                    activeTrackColor = CyanNeon,
                    inactiveTrackColor = ObsidianBorder
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Voice Pitch: ${String.format("%.2f", speechPitch)}x",
                style = MaterialTheme.typography.bodyMedium,
                color = TextWhite
            )
            Slider(
                value = speechPitch,
                onValueChange = { speechPitch = it; persistChanges() },
                valueRange = 0.8f..1.4f,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricViolet,
                    activeTrackColor = ElectricViolet,
                    inactiveTrackColor = ObsidianBorder
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Personality & Tone Section ---
        SettingsSectionHeader(title = "PERSONALITY & ATTITUDE", icon = Icons.Default.Tune)

        SettingsCard {
            Text(
                text = "Humor & Wit: ${(humorLevel * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = TextWhite
            )
            Slider(
                value = humorLevel,
                onValueChange = { humorLevel = it; persistChanges() },
                valueRange = 0.0f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = CyanNeon,
                    activeTrackColor = CyanNeon,
                    inactiveTrackColor = ObsidianBorder
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Sassiness & Teasing: ${(sassLevel * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = TextWhite
            )
            Slider(
                value = sassLevel,
                onValueChange = { sassLevel = it; persistChanges() },
                valueRange = 0.0f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFF72585),
                    activeTrackColor = Color(0xFFF72585),
                    inactiveTrackColor = ObsidianBorder
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Answer Verbosity: ${if (verbosity < 0.4f) "Ultra Concise" else if (verbosity > 0.7f) "Elaborate" else "Balanced"}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextWhite
            )
            Slider(
                value = verbosity,
                onValueChange = { verbosity = it; persistChanges() },
                valueRange = 0.0f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00BBF9),
                    activeTrackColor = Color(0xFF00BBF9),
                    inactiveTrackColor = ObsidianBorder
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Interaction & Hardware ---
        SettingsSectionHeader(title = "VOICE & LIVE INTERACTION", icon = Icons.Default.Vibration)

        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Wake Word Detection", color = TextWhite, fontWeight = FontWeight.Medium)
                    Text(text = "Say 'Anisa', 'Hey Anisa', 'আনিসা' or 'अनीसा' to wake her up hands-free", fontSize = 12.sp, color = TextSecondary)
                }
                Switch(
                    checked = wakeWordEnabled,
                    onCheckedChange = { wakeWordEnabled = it; persistChanges() },
                    colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon, checkedTrackColor = CyanNeon.copy(alpha = 0.4f))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Trilingual Voice Engine", color = TextWhite, fontWeight = FontWeight.Medium)
                    Text(text = "Auto-switching between বাংলা (Bengali), English & हिन्दी (Hindi)", fontSize = 12.sp, color = CyanNeon)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Haptic Feedback", color = TextWhite, fontWeight = FontWeight.Medium)
                    Text(text = "Vibrate subtly on state transitions and barge-in", fontSize = 12.sp, color = TextSecondary)
                }
                Switch(
                    checked = hapticFeedback,
                    onCheckedChange = { hapticFeedback = it; persistChanges() },
                    colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon, checkedTrackColor = CyanNeon.copy(alpha = 0.4f))
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Trusted PC Pairing ---
        SettingsSectionHeader(title = "DESKTOP AGENT COMPANION", icon = Icons.Default.Computer)

        SettingsCard {
            Text(
                text = "Pair with your computer to orchestrate actions remotely.",
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = settings.pcDeviceName, color = TextWhite, fontWeight = FontWeight.Bold)
                    Text(text = "Code: ${settings.pcPairingCode}", fontSize = 12.sp, color = CyanNeon)
                }

                Button(
                    onClick = {
                        isPcPaired = !isPcPaired
                        persistChanges()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPcPaired) ElectricViolet else CyanNeon,
                        contentColor = if (isPcPaired) TextWhite else Color(0xFF04131A)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isPcPaired) "Disconnect" else "Pair Agent",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Privacy & Local Storage ---
        SettingsSectionHeader(title = "PRIVACY & STORAGE", icon = Icons.Default.Lock)

        SettingsCard {
            Text(
                text = "All voice conversations, learned preferences, and task decompositions are stored locally in Room database on your phone. Nothing is shared without your intent.",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onClearAllData,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoralError.copy(alpha = 0.2f),
                    contentColor = CoralError
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear All Local Data & Memories", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = CyanNeon,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            content()
        }
    }
}
