package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.anisa.AnisaScreen
import com.example.anisa.AnisaViewModel
import com.example.data.entity.MemoryCategory
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianDark

class MainActivity : ComponentActivity() {

    private val viewModel: AnisaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                // Request permissions
                val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }.toTypedArray()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { /* Permissions result processed */ }

                LaunchedEffect(Unit) {
                    val needsAudio = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED

                    if (needsAudio) {
                        permissionLauncher.launch(permissionsToRequest)
                    }
                }

                val uiState by viewModel.uiState.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ObsidianDark)
                            .padding(innerPadding)
                    ) {
                        Crossfade(
                            targetState = uiState.currentScreen,
                            label = "screen_transition"
                        ) { screen ->
                            when (screen) {
                                AnisaScreen.HOME -> HomeScreen(
                                    uiState = uiState,
                                    onToggleMic = { viewModel.toggleMic() },
                                    onStopOperation = { viewModel.stopCurrentOperation() },
                                    onPromptSelected = { prompt -> viewModel.handleUserSpeech(prompt) },
                                    onNavigate = { dest -> viewModel.selectScreen(dest) },
                                    onCompleteTaskStep = { task -> viewModel.completeTaskStep(task) },
                                    onCancelTask = { task -> viewModel.cancelTask(task) }
                                )

                                AnisaScreen.HISTORY -> HistoryScreen(
                                    conversations = uiState.recentConversations,
                                    onBack = { viewModel.selectScreen(AnisaScreen.HOME) },
                                    onSpeakAgain = { text -> viewModel.voiceEngine.speak(text) },
                                    onDeleteConversation = { id -> viewModel.deleteConversation(id) },
                                    onClearAll = { viewModel.clearAllConversations() }
                                )

                                AnisaScreen.TASKS -> {
                                    val tasks by viewModel.repository.allTasks.collectAsState(initial = emptyList())
                                    TasksScreen(
                                        tasks = tasks,
                                        onBack = { viewModel.selectScreen(AnisaScreen.HOME) },
                                        onCompleteStep = { task -> viewModel.completeTaskStep(task) },
                                        onCancelTask = { task -> viewModel.cancelTask(task) },
                                        onDeleteTask = { id -> viewModel.deleteTask(id) },
                                        onCreateSampleTask = {
                                            viewModel.handleUserSpeech("Break down the project workflow for this week")
                                        }
                                    )
                                }

                                AnisaScreen.MEMORY -> MemoryScreen(
                                    memories = uiState.memories,
                                    onBack = { viewModel.selectScreen(AnisaScreen.HOME) },
                                    onSaveMemory = { key, value, cat -> viewModel.saveMemory(key, value, cat) },
                                    onDeleteMemory = { id -> viewModel.deleteMemory(id) },
                                    onClearAll = { viewModel.clearAllMemories() }
                                )

                                AnisaScreen.SETTINGS -> SettingsScreen(
                                    settings = uiState.settings,
                                    onBack = { viewModel.selectScreen(AnisaScreen.HOME) },
                                    onSaveSettings = { newSet -> viewModel.updateSettings(newSet) },
                                    onClearAllData = {
                                        viewModel.clearAllConversations()
                                        viewModel.clearAllMemories()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.voiceEngine.stopListening()
    }
}
