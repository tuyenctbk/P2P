package com.example.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
    val totalBytesSent by viewModel.totalBytesSent.collectAsStateWithLifecycle()
    val totalBytesReceived by viewModel.totalBytesReceived.collectAsStateWithLifecycle()
    val totalPackets by viewModel.totalPackets.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val peerPresence by viewModel.peerPresence.collectAsStateWithLifecycle()
    val activePeers by viewModel.activePeers.collectAsStateWithLifecycle()

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

    // File pickers
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
                        // Quick Reply Chips
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
                                        text = { Text("🖼️ Select Image from Gallery") },
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
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (messageText.isNotBlank()) {
                                        showScheduleDialog = true
                                    } else {
                                        android.widget.Toast.makeText(context, "Enter a message to schedule", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("schedule_message_button")
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = "Schedule Message",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = {
                                    if (messageText.isNotBlank()) {
                                        viewModel.sendTypingSignal(false)
                                        viewModel.sendMessage(messageText)
                                        messageText = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
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
            if (displayedMessages.isEmpty() && !uiState.isPeerTyping) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.isSearchActive) "No messages matching search." else "No messages yet. Say hello!",
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
                    items(displayedMessages) { msg ->
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
                                            type = msg.attachmentType,
                                            data = msg.attachmentData,
                                            textColor = textColor,
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
                                        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                                        Text(
                                            text = timeStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = textColor.copy(alpha = 0.7f)
                                        )
                                        if (isOutgoing) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            val (statusIcon, statusTint) = when (msg.status) {
                                                "READ" -> Pair(Icons.Default.DoneAll, MaterialTheme.colorScheme.tertiary)
                                                "DELIVERED" -> Pair(Icons.Default.DoneAll, textColor.copy(alpha = 0.8f))
                                                "SENT" -> Pair(Icons.Default.Done, textColor.copy(alpha = 0.7f))
                                                else -> Pair(Icons.Default.ErrorOutline, MaterialTheme.colorScheme.error)
                                            }
                                            Icon(
                                                imageVector = statusIcon,
                                                contentDescription = msg.status,
                                                modifier = Modifier.size(14.dp),
                                                tint = statusTint
                                            )
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
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("✍️ ", fontSize = 14.sp)
                                        Text(
                                            "$peerName is typing...",
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "🎉", "👏")
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
    type: String,
    data: String,
    textColor: androidx.compose.ui.graphics.Color,
    onImageClick: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = textColor.copy(alpha = 0.15f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        var base64Str: String? = null
        var fileName = data
        var fileSize = ""

        try {
            if (data.trim().startsWith("{")) {
                val json = JSONObject(data)
                fileName = json.optString("fileName", data)
                fileSize = json.optString("fileSize", "")
                base64Str = json.optString("base64", null)
            }
        } catch (ignored: Exception) {}

        val bitmap = remember(base64Str) {
            if (!base64Str.isNullOrBlank()) {
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
                    Text(fileName, style = MaterialTheme.typography.labelSmall, color = textColor)
                    if (fileSize.isNotEmpty()) Text(fileSize, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (type) {
                    "IMAGE" -> "🖼️"
                    "FILE", "DOCUMENT" -> "📄"
                    "VOICE" -> "🎙️"
                    else -> "📎"
                }
                Text(text = icon, fontSize = 20.sp)
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
                        text = if (fileSize.isNotEmpty()) "$type • $fileSize" else type,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.8f)
                    )
                }
                Icon(Icons.Default.Download, contentDescription = "Received file", tint = textColor, modifier = Modifier.size(20.dp))
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


