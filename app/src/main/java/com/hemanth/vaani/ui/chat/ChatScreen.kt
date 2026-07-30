package com.hemanth.vaani.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hemanth.vaani.data.AppLanguage
import com.hemanth.vaani.llm.ModelState
import com.hemanth.vaani.voice.VoiceInputState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onBack: () -> Unit, viewModel: ChatViewModel = viewModel()) {
    val modelState by viewModel.modelState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val language by viewModel.language.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val preferGpu by viewModel.preferGpu.collectAsState()
    val voiceInputState by viewModel.voiceInputState.collectAsState()
    val speakRepliesEnabled by viewModel.speakRepliesEnabled.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val continuousModeEnabled by viewModel.continuousModeEnabled.collectAsState()

    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPermission = granted }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Vaani Chat") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { viewModel.setContinuousModeEnabled(!continuousModeEnabled) }) {
                    Icon(
                        Icons.Filled.Loop,
                        contentDescription = "Toggle hands-free conversation",
                        tint = if (continuousModeEnabled) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                IconButton(onClick = { viewModel.setSpeakRepliesEnabled(!speakRepliesEnabled) }) {
                    Icon(
                        if (speakRepliesEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                        contentDescription = "Toggle spoken replies"
                    )
                }
                LanguagePicker(current = language, onSelect = viewModel::setLanguage)
            }
        )

        when (modelState) {
            is ModelState.Ready -> ChatBody(
                messages = messages,
                isGenerating = isGenerating,
                isSpeaking = isSpeaking,
                continuousModeEnabled = continuousModeEnabled,
                voiceInputState = voiceInputState,
                hasMicPermission = hasMicPermission,
                onSend = viewModel::sendMessage,
                onMicClick = {
                    if (!hasMicPermission) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else if (voiceInputState == VoiceInputState.Idle) {
                        viewModel.startVoiceInput()
                    } else {
                        viewModel.stopVoiceInput()
                    }
                }
            )
            else -> ModelSetupCard(
                state = modelState,
                preferGpu = preferGpu,
                onPreferGpuChange = viewModel::setPreferGpu,
                onDownloadClick = viewModel::downloadAndInitialize
            )
        }
    }
}

@Composable
private fun LanguagePicker(current: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(current.displayName)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.entries.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.displayName) },
                    onClick = { onSelect(lang); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ModelSetupCard(
    state: ModelState,
    preferGpu: Boolean,
    onPreferGpuChange: (Boolean) -> Unit,
    onDownloadClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "On-device model",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use GPU acceleration", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            Switch(checked = preferGpu, onCheckedChange = onPreferGpuChange)
        }
        Text(
            "If replies come out garbled or empty, turn this off -- a few phones' " +
                "GPU drivers misbehave with on-device LLMs. CPU is slower but reliable.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        when (state) {
            ModelState.NotDownloaded, is ModelState.Error -> {
                Text(
                    "Gemma 4 E2B (~3 GB) will download once and run fully offline afterwards.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state is ModelState.Error) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDownloadClick) { Text("Download & start") }
            }
            is ModelState.Downloading -> {
                val pct = (state.progress * 100).roundToInt()
                Text("Downloading model... $pct%", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${mbOf(state.bytesDownloaded)} / ${mbOf(state.totalBytes)} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ModelState.Verifying, ModelState.Downloaded -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Finishing up...", style = MaterialTheme.typography.bodyMedium)
            }
            ModelState.InitializingEngine -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Loading model into memory (first load can take ~10s)...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            ModelState.Ready -> Unit // handled by caller
        }
    }
}

private fun mbOf(bytes: Long): String =
    if (bytes <= 0) "?" else (bytes / 1_000_000).toString()

@Composable
private fun ChatBody(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    isSpeaking: Boolean,
    continuousModeEnabled: Boolean,
    voiceInputState: VoiceInputState,
    hasMicPermission: Boolean,
    onSend: (String) -> Unit,
    onMicClick: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isListening = voiceInputState is VoiceInputState.Listening ||
        voiceInputState is VoiceInputState.Partial
    // The mic can never be active while Vaani is speaking -- this is what
    // stops it from hearing its own voice, not any acoustic detection.
    val micBlocked = isGenerating || isSpeaking

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { message -> MessageBubble(message) }
        }

        if (continuousModeEnabled) {
            Text(
                when {
                    isSpeaking -> "Speaking..."
                    isListening -> "Listening..."
                    isGenerating -> "Thinking..."
                    else -> "Hands-free mode on"
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (voiceInputState is VoiceInputState.Partial) {
            Text(
                voiceInputState.text,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (voiceInputState is VoiceInputState.Error) {
            Text(
                voiceInputState.message,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Vaani anything...") },
                enabled = !micBlocked && !isListening
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onMicClick, enabled = !micBlocked) {
                Icon(
                    if (isListening) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = if (hasMicPermission) "Voice input" else "Grant microphone permission",
                    tint = if (isListening) MaterialTheme.colorScheme.error else LocalContentColor.current
                )
            }
            IconButton(
                onClick = {
                    onSend(input)
                    input = ""
                },
                enabled = !micBlocked && !isListening && input.isNotBlank()
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val alignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (message.isFromUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Card(colors = CardDefaults.cardColors(containerColor = color)) {
            Text(
                text = message.text.ifBlank { "..." },
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
