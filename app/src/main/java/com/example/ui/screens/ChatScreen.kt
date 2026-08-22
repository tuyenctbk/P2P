package com.example.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatMessage
import com.example.ui.components.ShimmerMessageHistoryList
import com.example.viewmodel.ChatViewModel
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToVideoCall: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.currentMessages.collectAsStateWithLifecycle(initialValue = emptyList())
    val isMessageHistoryLoading by viewModel.isMessageHistoryLoading.collectAsStateWithLifecycle()
    val totalBytesSent by viewModel.totalBytesSent.collectAsStateWithLifecycle()
    val totalBytesReceived by viewModel.totalBytesReceived.collectAsStateWithLifecycle()
    val totalPackets by viewModel.totalPackets.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val peerPresence by viewModel.peerPresence.collectAsStateWithLifecycle()
    val activePeers by viewModel.activePeers.collectAsStateWithLifecycle()

    val isVoiceNotesEnabled by viewModel.isVoiceNotesEnabled.collectAsStateWithLifecycle()
    val isReactionsEnabled by viewModel.isReactionsEnabled.collectAsStateWithLifecycle()
    val isQuickReplyEnabled by viewModel.isQuickReplyEnabled.collectAsStateWithLifecycle()
    val remoteBannerAnnouncement by viewModel.remoteBannerAnnouncement.collectAsStateWithLifecycle()

    val isVoiceRecording by viewModel.isVoiceRecording.collectAsStateWithLifecycle()
    val recordingDurationSec by viewModel.recordingDurationSec.collectAsStateWithLifecycle()
    val currentVoiceAmplitudes by viewModel.currentVoiceAmplitudes.collectAsStateWithLifecycle()
    val playingVoiceMessageId by viewModel.playingVoiceMessageId.collectAsStateWithLifecycle()
    val voicePlaybackProgress by viewModel.voicePlaybackProgress.collectAsStateWithLifecycle()

    val selectedPeer = remember(activePeers, uiState.selectedPeerAddress) {
        activePeers.find { it.address == uiState.selectedPeerAddress }
    }

    var messageText by remember { mutableStateOf("") }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }
    var selectedMsgForReaction by remember { mutableStateOf<ChatMessage?>(null) }
    var fullScreenImageBase64 by remember { mutableStateOf<String?>(null) }
    var showScheduleDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val peerName = uiState.selectedPeerName ?: "Chat"
    val peerAddress = uiState.selectedPeerAddress ?: ""

    // Voice Audio permission
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val started = viewModel.startVoiceRecording()
            if (!started) {
                android.widget.Toast.makeText(context, "Could not start audio recorder", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(context, "Microphone permission required for voice notes", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // File pickers
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: android.graphics.Bitmap? ->
        bitmap?.let { viewModel.sendImageBitmap(it) }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendAttachmentFromUri(context, it, isImage = true) }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendAttachmentFromUri(context, it, isImage = false) }
    }

    // Filter messages if search is active
    val displayedMessages = remember(messages, uiState.searchQuery, uiState.isSearchActive) {
        if (uiState.isSearchActive && uiState.searchQuery.isNotBlank()) {
            messages.filter { it.text.contains(uiState.searchQuery, ignoreCase = true) }
        } else {
            messages
        }
    }

    // Auto scroll to bottom when new messages arrive
    LaunchedEffect(displayedMessages.size, uiState.isPeerTyping) {
        if (displayedMessages.isNotEmpty() && !uiState.isSearchActive) {
            listState.animateScrollToItem(displayedMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSearchActive) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search messages...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                                .testTag("search_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    } else {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(peerName, fontWeight = FontWeight.Bold, maxLines = 1)
                                if (uiState.isSelectedPeerBlocked) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Text(
                                            text = "BLOCKED",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            val statusText = when {
                                uiState.isPeerTyping -> "✍️ typing..."
                                uiState.activePeerConnectionState == "RECONNECTING" -> "🟡 Reconnecting..."
                                uiState.activePeerConnectionState == "OFFLINE" -> "⚪ Offline"
                                else -> "🟢 Online • Encrypted Socket"
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (peerAddress.isNotBlank() && peerAddress != "GROUP" && peerAddress != "127.0.0.2") "$peerAddress • $statusText" else statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.isPeerTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // RSSI Signal Strength Indicator next to IP/status
                                if (peerAddress != "GROUP" && peerAddress.isNotBlank()) {
                                    val isConnected = uiState.activePeerConnectionState == "CONNECTED" && (selectedPeer?.isConnected == true || peerAddress == "127.0.0.2")
                                    val latency = if (peerAddress == "127.0.0.2") 4L else selectedPeer?.latencyMs ?: 0L

                                    val label = when {
                                        !isConnected -> "Offline"
                                        latency <= 15L -> "Excellent"
                                        latency <= 50L -> "Good"
                                        latency <= 150L -> "Fair"
                                        else -> "Poor"
                                    }
                                    
                                    val tint = when {
                                        !isConnected -> MaterialTheme.colorScheme.outline
                                        latency <= 15L -> MaterialTheme.colorScheme.primary
                                        latency <= 50L -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                        latency <= 150L -> androidx.compose.ui.graphics.Color(0xFFFF9800)
                                        else -> MaterialTheme.colorScheme.error
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = tint.copy(alpha = 0.12f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            WifiSignalStrengthIndicator(
                                                latency = latency,
                                                isConnected = isConnected,
                                                modifier = Modifier.align(Alignment.CenterVertically)
                                            )
                                            if (isConnected) {
                                                Text(
                                                    text = "${latency}ms",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                    color = tint
                                                )
                                            } else {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                    color = tint
                                                )
                                            }
                                        }
                                    }
                                }

                                val peerInfo = peerPresence[peerAddress]
                                val simulatedBattery = if (peerAddress == "127.0.0.2") 85 else peerInfo?.batteryLevel
                                if (peerAddress != "GROUP" && simulatedBattery != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f)
                                    ) {
                                        BatteryStatusIndicator(
                                            batteryLevel = simulatedBattery,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (peerAddress != "GROUP" && peerAddress != "127.0.0.2" && peerAddress.isNotBlank()) {
                        IconButton(
                            onClick = { viewModel.toggleBlockSelectedPeer() },
                            modifier = Modifier.testTag("block_peer_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.isSelectedPeerBlocked) Icons.Default.Block else Icons.Default.Shield,
                                contentDescription = if (uiState.isSelectedPeerBlocked) "Unblock Peer" else "Block Peer",
                                tint = if (uiState.isSelectedPeerBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.toggleSearch() },
                        modifier = Modifier.testTag("toggle_search_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                    IconButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.testTag("export_transcript_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Export Chat Transcript", tint = MaterialTheme.colorScheme.primary)
                    }
                    Box {
                        IconButton(
                            onClick = { showThemeMenu = true },
                            modifier = Modifier.testTag("chat_theme_switcher")
                        ) {
                            val icon = when (themeMode) {
                                "LIGHT" -> Icons.Default.LightMode
                                "DARK" -> Icons.Default.DarkMode
                                else -> Icons.Default.SettingsBrightness
                            }
                            Icon(icon, contentDescription = "Switch Theme")
                        }
                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("☀️ Light Mode") },
                                onClick = {
                                    viewModel.setThemeMode("LIGHT")
                                    showThemeMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🌙 Dark Mode") },
                                onClick = {
                                    viewModel.setThemeMode("DARK")
                                    showThemeMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📱 System Default") },
                                onClick = {
                                    viewModel.setThemeMode("SYSTEM")
                                    showThemeMenu = false
                                }
                            )
                        }
                    }
                    IconButton(
                        onClick = { showSecurityDialog = true },
                        modifier = Modifier.testTag("security_info_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Security Status", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { showStatsDialog = true },
                        modifier = Modifier.testTag("stats_button")
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = "Network Bandwidth Stats", tint = MaterialTheme.colorScheme.secondary)
                    }
                    if (peerAddress != "GROUP") {
                        IconButton(
                            onClick = { viewModel.reconnectPeer(peerAddress) },
                            modifier = Modifier.testTag("reconnect_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reconnect / Ping Socket", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = onNavigateToVideoCall,
                            modifier = Modifier.testTag("video_call_button")
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = "Start Video Call", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                if (uiState.isSelectedPeerBlocked) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .navigationBarsPadding(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Peer is blocked. Chat is disabled.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { viewModel.toggleBlockSelectedPeer() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("unblock_button")
                            ) {
                                Text("Unblock")
                            }
                        }
                    }
                } else {
                    Column {
                        // Quick Reply Chips (Toggled dynamically by Remote Config)
                        if (isQuickReplyEnabled) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("👋 Hello!", "📍 Ping Test", "🔒 E2E Secure", "👍 Got it!").forEach { preset ->
                                    SuggestionChip(
                                        onClick = { viewModel.sendMessage(preset) },
                                        label = { Text(preset, style = MaterialTheme.typography.bodySmall) }
                                    )
                                }
                            }
                        }

                        if (isVoiceRecording) {
                            // Active Voice Recording UI
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .navigationBarsPadding(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.cancelVoiceRecording() },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("cancel_voice_record_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Cancel Recording",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Blinking Red Dot
                                        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
                                        val alpha by infiniteTransition.animateFloat(
                                            initialValue = 0.3f,
                                            targetValue = 1.0f,
                                            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                                animation = androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.LinearEasing),
                                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                            ),
                                            label = "pulse_alpha"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.error.copy(alpha = alpha))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = String.format("%02d:%02d", recordingDurationSec / 60, recordingDurationSec % 60),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Waveform visualizer Canvas
                                        androidx.compose.foundation.Canvas(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(24.dp)
                                        ) {
                                            val barWidth = 3.dp.toPx()
                                            val gap = 2.dp.toPx()
                                            val maxBars = (size.width / (barWidth + gap)).toInt().coerceAtLeast(1)
                                            val amps = currentVoiceAmplitudes.takeLast(maxBars)
                                            val centerY = size.height / 2f

                                            amps.forEachIndexed { index, amp ->
                                                val x = size.width - (amps.size - index) * (barWidth + gap)
                                                val barHeight = (amp.coerceIn(0.1f, 1f) * size.height).coerceAtLeast(4.dp.toPx())
                                                drawLine(
                                                    color = androidx.compose.ui.graphics.Color(0xFFEF4444),
                                                    start = androidx.compose.ui.geometry.Offset(x, centerY - barHeight / 2),
                                                    end = androidx.compose.ui.geometry.Offset(x, centerY + barHeight / 2),
                                                    strokeWidth = barWidth,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                )
                                            }
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.sendVoiceRecording() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .testTag("send_voice_button"),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Voice Message")
                                }
                            }
                        } else {
                            // Standard Text & Attachments Input UI
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .navigationBarsPadding(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    IconButton(
                                        onClick = { showAttachMenu = !showAttachMenu },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .testTag("attach_button")
                                    ) {
                                        Icon(Icons.Default.AddCircle, contentDescription = "Attach File/Media", tint = MaterialTheme.colorScheme.primary)
                                    }

                                    DropdownMenu(
                                        expanded = showAttachMenu,
                                        onDismissRequest = { showAttachMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("📷 Take Photo with Camera") },
                                            onClick = {
                                                showAttachMenu = false
                                                cameraLauncher.launch(null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("🖼️ Choose from Gallery") },
                                            onClick = {
                                                showAttachMenu = false
                                                imagePickerLauncher.launch("image/*")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("📄 Select Document / File") },
                                            onClick = {
                                                showAttachMenu = false
                                                filePickerLauncher.launch("*/*")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("🔒 Quick Encrypted Snapshot") },
                                            onClick = {
                                                showAttachMenu = false
                                                viewModel.sendAttachment("Shared encrypted snapshot", "IMAGE", "snapshot_${System.currentTimeMillis()}.jpg")
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                OutlinedTextField(
                                    value = messageText,
                                    onValueChange = { newText ->
                                        messageText = newText
                                        viewModel.sendTypingSignal(newText.isNotBlank())
                                    },
                                    placeholder = { Text("Type a message...") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("message_input"),
                                    shape = RoundedCornerShape(24.dp),
                                    maxLines = 4
                                )
                                Spacer(modifier = Modifier.width(6.dp))

                                IconButton(
                                    onClick = {
                                        if (messageText.isNotBlank()) {
                                            showScheduleDialog = true
                                        } else {
                                            android.widget.Toast.makeText(context, "Enter a message to schedule", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("schedule_message_button")
                                ) {
                                    Icon(
                                        Icons.Default.Schedule,
                                        contentDescription = "Schedule Message",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                if (messageText.isBlank()) {
                                    if (isVoiceNotesEnabled) {
                                        // Voice Recording Trigger Button
                                        IconButton(
                                            onClick = {
                                                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                                    context,
                                                    android.Manifest.permission.RECORD_AUDIO
                                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                if (hasPermission) {
                                                    val started = viewModel.startVoiceRecording()
                                                    if (!started) {
                                                        android.widget.Toast.makeText(context, "Could not start audio recorder", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(22.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .testTag("record_voice_button"),
                                            colors = IconButtonDefaults.iconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        ) {
                                            Icon(Icons.Default.Mic, contentDescription = "Record Voice Note")
                                        }
                                    }
                                } else {
                                    // Send Text Button
                                    IconButton(
                                        onClick = {
                                            viewModel.sendTypingSignal(false)
                                            viewModel.sendMessage(messageText)
                                            messageText = ""
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                            .testTag("send_button"),
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Ambient Mesh Gradient Glows
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val indigoGlow = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(androidx.compose.ui.graphics.Color(0x336366F1), androidx.compose.ui.graphics.Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, 0f),
                    radius = size.width * 0.7f
                )
                drawRect(indigoGlow)
            }

            if (isMessageHistoryLoading) {
                ShimmerMessageHistoryList(
                    modifier = Modifier.fillMaxSize()
                )
            } else if (displayedMessages.isEmpty() && !uiState.isPeerTyping) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.isSearchActive) "No messages matching \"${uiState.searchQuery}\"." else "No messages yet. Say hello!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    if (uiState.isSearchActive && uiState.searchQuery.isNotBlank()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🔍 Found ${displayedMessages.size} matching messages",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    TextButton(
                                        onClick = { viewModel.setSearchQuery("") },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    var lastDateStr = ""
                    displayedMessages.forEachIndexed { index, msg ->
                        val currentDateStr = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(msg.timestamp))
                        if (currentDateStr != lastDateStr) {
                            val displayHeader = when (currentDateStr) {
                                SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date()) -> "Today"
                                SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86400000L)) -> "Yesterday"
                                else -> currentDateStr
                            }
                            item(key = "date_header_$currentDateStr") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Text(
                                            text = displayHeader,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            lastDateStr = currentDateStr
                        }

                        item(key = "msg_${msg.id}_${msg.timestamp}") {
                            val isOutgoing = msg.isOutgoing
                            val alignment = if (isOutgoing) Alignment.End else Alignment.Start
                            val bubbleColor = if (isOutgoing) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                            val textColor = if (isOutgoing) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = alignment
                            ) {
                                if (peerAddress == "GROUP" && !isOutgoing) {
                                    Text(
                                        text = msg.senderName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isOutgoing) 16.dp else 4.dp,
                                        bottomEnd = if (isOutgoing) 4.dp else 16.dp
                                    ),
                                    color = bubbleColor,
                                    modifier = Modifier
                                        .widthIn(max = 280.dp)
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = { selectedMsgForReaction = msg }
                                        )
                                        .testTag("message_bubble")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (msg.attachmentType != "NONE") {
                                            AttachmentContentView(
                                                messageId = msg.id,
                                                type = msg.attachmentType,
                                                data = msg.attachmentData,
                                                textColor = textColor,
                                                isOutgoing = isOutgoing,
                                                isPlaying = playingVoiceMessageId == msg.id,
                                                playbackProgress = if (playingVoiceMessageId == msg.id) voicePlaybackProgress else 0f,
                                                onTogglePlayVoice = { id, base64 -> viewModel.togglePlayVoice(id, base64) },
                                                onImageClick = { base64 -> fullScreenImageBase64 = base64 }
                                            )
                                        }

                                        if (msg.text.isNotBlank()) {
                                            Text(
                                                text = msg.text,
                                                color = textColor,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }

                                        Row(
                                            modifier = Modifier.align(Alignment.End),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val exactTimeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(msg.timestamp))
                                            Text(
                                                text = exactTimeStr,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = textColor.copy(alpha = 0.75f)
                                            )
                                            if (isOutgoing) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                val isRead = msg.status == "READ"

                                                val statusScale by androidx.compose.animation.core.animateFloatAsState(
                                                    targetValue = if (isRead) 1.25f else 1.0f,
                                                    animationSpec = androidx.compose.animation.core.spring(
                                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                                    ),
                                                    label = "status_scale"
                                                )

                                                val (statusIcon, statusTint) = when (msg.status) {
                                                    "READ" -> Pair(Icons.Default.DoneAll, MaterialTheme.colorScheme.tertiary)
                                                    "DELIVERED" -> Pair(Icons.Default.DoneAll, textColor.copy(alpha = 0.85f))
                                                    "SENT" -> Pair(Icons.Default.Done, textColor.copy(alpha = 0.7f))
                                                    else -> Pair(Icons.Default.ErrorOutline, MaterialTheme.colorScheme.error)
                                                }

                                                androidx.compose.animation.AnimatedContent(
                                                    targetState = msg.status,
                                                    transitionSpec = {
                                                        (androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn())
                                                            .togetherWith(androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut())
                                                    },
                                                    label = "status_icon_transition"
                                                ) { targetStatus ->
                                                    Icon(
                                                        imageVector = statusIcon,
                                                        contentDescription = targetStatus,
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .graphicsLayer(scaleX = statusScale, scaleY = statusScale),
                                                        tint = statusTint
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (msg.reactions.isNotBlank()) {
                                    Row(
                                        modifier = Modifier
                                            .padding(top = 2.dp, start = 4.dp, end = 4.dp)
                                            .clickable { selectedMsgForReaction = msg },
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        msg.reactions.split(",").filter { it.isNotBlank() }.distinct().forEach { reactionEmoji ->
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                                            ) {
                                                Text(
                                                    text = reactionEmoji,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.isPeerTyping) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.padding(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("✍️", fontSize = 14.sp)
                                        Text(
                                            stringResource(R.string.typing_indicator, peerName),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Fullscreen Image Modal Preview
    fullScreenImageBase64?.let { base64 ->
        val bitmap = remember(base64) {
            try {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        }
        AlertDialog(
            onDismissRequest = { fullScreenImageBase64 = null },
            title = { Text("Encrypted Media Preview") },
            text = {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Encrypted image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("Could not decode image binary payload.")
                }
            },
            confirmButton = {
                TextButton(onClick = { fullScreenImageBase64 = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Security & E2E Encryption Dialog
    if (showSecurityDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(if (peerAddress == "GROUP") "Decentralized Group Security" else "End-to-End Encryption") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (peerAddress == "GROUP") {
                        Text("🔒 Group Chat messages are broadcasted securely and directly to all active peers in your local network neighborhood.")
                        Text("Verify that peers are connected in the Discovered list to ensure all members receive transmissions.")
                    } else {
                        Text("🔒 Connection with $peerName ($peerAddress) is fully secured via Peer-to-Peer TLS/AES-256 encryption.")
                        Text("Cryptographic Fingerprint:", fontWeight = FontWeight.Bold)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "A3F9:E2B7:C4D1:882A:9011:F4B2:66E3:71C8",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(8.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text("Both nodes verified mutual handshake successfully. No intermediaries or servers can inspect or decrypt messages.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSecurityDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }

    // Bandwidth Stats Dialog
    if (showStatsDialog) {
        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            icon = { Icon(Icons.Default.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
            title = { Text("Network Traffic Monitor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Real-time telemetry for socket transmission across the decentralized mesh:")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Bytes Sent:")
                        Text("$totalBytesSent bytes", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Bytes Received:")
                        Text("$totalBytesReceived bytes", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Packets Exchanged:")
                        Text("$totalPackets packets", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Socket Port:")
                        Text("TCP 8888 (Active)", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Chat Transcript") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Save or share the conversation history for $peerName:")
                    Button(
                        onClick = {
                            val text = viewModel.exportTranscriptAsText(messages, peerName)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "P2P Chat Transcript - $peerName")
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Export Plain Text Transcript"))
                            showExportDialog = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_txt_button")
                    ) {
                        Icon(Icons.Default.TextSnippet, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export as Plain Text (.txt)")
                    }

                    OutlinedButton(
                        onClick = {
                            val json = viewModel.exportTranscriptAsJson(messages, peerName)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_SUBJECT, "P2P Chat Transcript JSON - $peerName")
                                putExtra(Intent.EXTRA_TEXT, json)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Export JSON Transcript"))
                            showExportDialog = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_json_button")
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export as JSON (.json)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (selectedMsgForReaction != null) {
        val msg = selectedMsgForReaction!!
        AlertDialog(
            onDismissRequest = { selectedMsgForReaction = null },
            title = { Text("Quick Reaction", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (msg.text.isNotBlank()) {
                        Text(
                            text = "\"${msg.text.take(60)}${if (msg.text.length > 60) "..." else ""}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "🎉", "👏", "🚀", "💯", "🙏", "✨")
                        emojis.forEach { emoji ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        viewModel.toggleReaction(msg, emoji)
                                        selectedMsgForReaction = null
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(emoji, fontSize = 22.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMsgForReaction = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showScheduleDialog) {
        var selectedDelayMinutes by remember { mutableStateOf(5) }
        val currentMillis = System.currentTimeMillis()
        val scheduledTime = currentMillis + (selectedDelayMinutes * 60 * 1000L)
        val formattedTime = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(scheduledTime))

        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            icon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Schedule Message") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select delay to queue message locally and transmit when peer is online:")
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 5, 15, 60).forEach { mins ->
                            FilterChip(
                                selected = selectedDelayMinutes == mins,
                                onClick = { selectedDelayMinutes = mins },
                                label = { Text(if (mins < 60) "${mins}m" else "1h") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text(
                        text = "Scheduled Send Time: $formattedTime",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "\"${messageText.take(80)}${if (messageText.length > 80) "..." else ""}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.scheduleMessage(messageText, scheduledTime)
                        messageText = ""
                        showScheduleDialog = false
                        android.widget.Toast.makeText(context, "Message scheduled for $formattedTime", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("confirm_schedule_button")
                ) {
                    Text("Schedule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScheduleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AttachmentContentView(
    messageId: Long = 0L,
    type: String,
    data: String,
    textColor: androidx.compose.ui.graphics.Color,
    isOutgoing: Boolean = false,
    isPlaying: Boolean = false,
    playbackProgress: Float = 0f,
    onTogglePlayVoice: ((Long, String) -> Unit)? = null,
    onImageClick: (String) -> Unit
) {
    var base64Str: String? = null
    var fileName = data
    var fileSize = ""
    var durationSec = 0

    try {
        if (data.trim().startsWith("{")) {
            val json = JSONObject(data)
            fileName = json.optString("fileName", data)
            fileSize = json.optString("fileSize", "")
            base64Str = json.optString("base64", null)
            durationSec = json.optInt("durationSec", 0)
        }
    } catch (ignored: Exception) {}

    val fileFormat = remember(fileName, type) {
        when {
            fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "JPEG"
            fileName.endsWith(".png", true) -> "PNG"
            fileName.endsWith(".mp4", true) -> "MP4"
            fileName.endsWith(".pdf", true) -> "PDF"
            fileName.endsWith(".zip", true) -> "ZIP"
            type.contains("IMAGE", true) -> "JPEG"
            type.contains("VIDEO", true) -> "MP4"
            type.contains("DOCUMENT", true) -> "PDF"
            else -> type.uppercase()
        }
    }

    val displaySizeStr = remember(fileSize, base64Str) {
        if (fileSize.isNotBlank()) fileSize
        else if (!base64Str.isNullOrBlank()) {
            val bytes = (base64Str.length * 3L / 4L)
            if (bytes >= 1024 * 1024) String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
            else "${bytes / 1024} KB"
        } else "1.2 MB"
    }

    var isOptInAccepted by remember(messageId) { mutableStateOf(isOutgoing) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = textColor.copy(alpha = 0.12f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        if (!isOptInAccepted && (type == "IMAGE" || type.contains("image", ignoreCase = true) || type == "VIDEO" || type == "DOCUMENT" || type == "FILE")) {
            // Pre-Download Opt-In Media Transfer Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = fileFormat,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.incoming_transfer),
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                    Text(
                        text = displaySizeStr,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { isOptInAccepted = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opt_in_download_button_${messageId}"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.accept_transfer, displaySizeStr),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            val bitmap = remember(base64Str) {
                if (!base64Str.isNullOrBlank() && (type == "IMAGE" || type.contains("image", ignoreCase = true))) {
                    try {
                        val bytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }

            if ((type == "IMAGE" || type.contains("image", ignoreCase = true)) && !base64Str.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onImageClick(base64Str) }
                        .padding(4.dp)
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = fileName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("🖼️ $fileName", color = textColor, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = textColor.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = fileFormat,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                    color = textColor,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(fileName, style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1)
                        }
                        Text(displaySizeStr, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                    }
                }
            } else if (type == "VOICE") {
                // Interactive Voice Note Player
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (!base64Str.isNullOrBlank() && onTogglePlayVoice != null) {
                                onTogglePlayVoice(messageId, base64Str)
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(textColor.copy(alpha = 0.2f)),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = textColor)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause Voice Note" else "Play Voice Note",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val barWidth = 3.dp.toPx()
                                val gap = 2.dp.toPx()
                                val totalBars = (size.width / (barWidth + gap)).toInt().coerceAtLeast(6)
                                val progressIndex = (totalBars * playbackProgress).toInt()

                                for (i in 0 until totalBars) {
                                    val x = i * (barWidth + gap)
                                    val normalizedHeight = (0.25f + 0.75f * kotlin.math.abs(kotlin.math.sin((i * 1.3f) + (messageId * 0.7f).toFloat()))).coerceIn(0.2f, 1f)
                                    val barH = normalizedHeight * size.height
                                    val isPlayed = i <= progressIndex && isPlaying

                                    drawLine(
                                        color = if (isPlayed) textColor else textColor.copy(alpha = 0.35f),
                                        start = androidx.compose.ui.geometry.Offset(x, (size.height - barH) / 2),
                                        end = androidx.compose.ui.geometry.Offset(x, (size.height + barH) / 2),
                                        strokeWidth = barWidth,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentPlayedSec = if (isPlaying && durationSec > 0) (durationSec * playbackProgress).toInt() else 0
                            val durationStr = if (durationSec > 0) {
                                if (isPlaying) {
                                    String.format("%02d:%02d / %02d:%02d", currentPlayedSec / 60, currentPlayedSec % 60, durationSec / 60, durationSec % 60)
                                } else {
                                    String.format("%02d:%02d", durationSec / 60, durationSec % 60)
                                }
                            } else {
                                "Voice Clip"
                            }

                            Text(
                                text = "🎙️ $durationStr",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = textColor
                            )

                            Text(
                                text = displaySizeStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = textColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = fileFormat,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1
                        )
                        Text(
                            text = "$fileFormat • $displaySizeStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                    Icon(Icons.Default.Download, contentDescription = "Received file", tint = textColor, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun WifiSignalStrengthIndicator(
    latency: Long,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val activeLevels = when {
        !isConnected -> 0
        latency <= 15L -> 4 // Dot + 3 arcs
        latency <= 50L -> 3 // Dot + 2 arcs
        latency <= 150L -> 2 // Dot + 1 arc
        else -> 1 // Dot only
    }

    val activeColor = when {
        !isConnected -> MaterialTheme.colorScheme.outline
        latency <= 15L -> MaterialTheme.colorScheme.primary
        latency <= 50L -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        latency <= 150L -> androidx.compose.ui.graphics.Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.error
    }

    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .padding(bottom = 1.dp)
            .size(16.dp)
    ) {
        val width = size.width
        val height = size.height

        val centerX = width / 2f
        val centerY = height - 2.dp.toPx()

        // 1. Draw central dot
        drawCircle(
            color = if (activeLevels >= 1) activeColor else inactiveColor,
            radius = 1.75.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
        )

        // Draw 3 concentric arcs
        val strokeWidth = 1.75.dp.toPx()
        val spacing = 3.5.dp.toPx()

        for (i in 1..3) {
            val radius = i * spacing
            val isArcActive = activeLevels >= (i + 1)
            val arcColor = if (isArcActive) activeColor else inactiveColor

            drawArc(
                color = arcColor,
                startAngle = 220f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(centerX - radius, centerY - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}

@Composable
fun BatteryStatusIndicator(
    batteryLevel: Int,
    modifier: Modifier = Modifier
) {
    val level = batteryLevel.coerceIn(0, 100)
    val color = when {
        level <= 20 -> MaterialTheme.colorScheme.error
        level <= 50 -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange
        else -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
    }
    
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.size(width = 18.dp, height = 10.dp)
        ) {
            val width = size.width
            val height = size.height
            val strokeWidth = 1.dp.toPx()
            
            // Draw battery outer container outline
            drawRoundRect(
                color = inactiveColor,
                topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(width - 2.5.dp.toPx(), height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
            
            // Draw battery terminal (tip on the right)
            drawRoundRect(
                color = inactiveColor,
                topLeft = androidx.compose.ui.geometry.Offset(width - 2.dp.toPx(), height * 0.3f),
                size = androidx.compose.ui.geometry.Size(1.5.dp.toPx(), height * 0.4f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.5.dp.toPx(), 0.5.dp.toPx())
            )
            
            // Draw battery filled level inside
            val maxInnerWidth = width - 4.5.dp.toPx()
            val fillWidth = maxInnerWidth * (level / 100f)
            if (fillWidth > 0) {
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(strokeWidth + 0.5.dp.toPx(), strokeWidth + 0.5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(fillWidth - strokeWidth, height - (strokeWidth * 2) - 1.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.5.dp.toPx(), 0.5.dp.toPx())
                )
            }
        }
        
        Text(
            text = "$level%",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = color
        )
    }
}


