package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.QrCodeView
import com.example.ui.QrScannerDialog
import com.example.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ChatViewModel,
    onNavigateToChat: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activePeers by viewModel.activePeers.collectAsStateWithLifecycle()
    val archivedPeers by viewModel.archivedPeers.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val searchQuery by viewModel.globalSearchQuery.collectAsStateWithLifecycle()
    val globalSearchResults by viewModel.globalSearchResults.collectAsStateWithLifecycle()

    val isOverlayVisible by viewModel.isDiagnosticOverlayVisible.collectAsStateWithLifecycle()
    val sendSpeedBps by viewModel.sendSpeedBps.collectAsStateWithLifecycle()
    val recvSpeedBps by viewModel.recvSpeedBps.collectAsStateWithLifecycle()
    val totalBytesSent by viewModel.totalBytesSent.collectAsStateWithLifecycle()
    val totalBytesReceived by viewModel.totalBytesReceived.collectAsStateWithLifecycle()
    val totalPackets by viewModel.totalPackets.collectAsStateWithLifecycle()
    val batteryLevel by viewModel.batteryLevel.collectAsStateWithLifecycle()
    val isBatteryThrottled by viewModel.isBatteryThrottled.collectAsStateWithLifecycle()

    var showConnectTabsDialog by remember { mutableStateOf(false) }
    var showHowToDialog by remember { mutableStateOf(false) }
    var showEditNicknameDialog by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }
    var nicknameInput by remember { mutableStateOf(uiState.myName) }
    var connectTabSelected by remember { mutableIntStateOf(0) } // 0: Share Mine, 1: Connect to Peer
    var peerTabSelected by remember { mutableIntStateOf(0) } // 0: Active, 1: Archived

    val currentPeersList = if (peerTabSelected == 0) activePeers else archivedPeers

    val filteredPeers = remember(currentPeersList, searchQuery) {
        if (searchQuery.isBlank()) currentPeersList else currentPeersList.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.address.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppLogoFavicon()
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("P2P Connect", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    // Pairing Guide / Help Button
                    IconButton(
                        onClick = { showHowToDialog = true },
                        modifier = Modifier.testTag("help_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Show Pairing Guide",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Connect QR Code Dialog Trigger
                    IconButton(
                        onClick = { showConnectTabsDialog = true },
                        modifier = Modifier.testTag("connect_tabs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "Connect & Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Full Settings Screen gear button
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    connectTabSelected = 1
                    showConnectTabsDialog = true
                },
                modifier = Modifier.testTag("connect_fab"),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Connect Peer")
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
                    colors = listOf(androidx.compose.ui.graphics.Color(0x4D6366F1), androidx.compose.ui.graphics.Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(0f, 0f),
                    radius = size.width * 0.8f
                )
                val cyanGlow = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(androidx.compose.ui.graphics.Color(0x3306B6D4), androidx.compose.ui.graphics.Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    radius = size.width * 0.9f
                )
                drawRect(indigoGlow)
                drawRect(cyanGlow)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isOverlayVisible) {
                    com.example.ui.components.DiagnosticOverlayCard(
                        sendSpeedBps = sendSpeedBps,
                        recvSpeedBps = recvSpeedBps,
                        totalBytesSent = totalBytesSent,
                        totalBytesReceived = totalBytesReceived,
                        totalPackets = totalPackets,
                        batteryLevel = batteryLevel,
                        isBatteryThrottled = isBatteryThrottled,
                        onClose = { viewModel.toggleDiagnosticOverlay() }
                    )
                }

                // Local Profile Premium Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.myName.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "@${uiState.myName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "IP Address: ${uiState.localIp}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = { 
                                connectTabSelected = 0
                                showConnectTabsDialog = true 
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Show My QR Code",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Search Field Across Conversations
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setGlobalSearchQuery(it) },
                    placeholder = { Text("Search peers & message history...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.setGlobalSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("global_search_input"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                if (searchQuery.isNotBlank()) {
                    Text(
                        text = "Message History Matches (${globalSearchResults.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (globalSearchResults.isEmpty()) {
                        Text(
                            text = "No messages matching '$searchQuery'",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(globalSearchResults) { msg ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectPeer(msg.peerAddress, if (msg.peerAddress == "GROUP") "Public Group Chat" else msg.senderName)
                                            viewModel.setSearchQuery(searchQuery)
                                            viewModel.toggleSearch()
                                            onNavigateToChat()
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = if (msg.peerAddress == "GROUP") "Group Chat (${msg.senderName})" else msg.senderName,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            val timeStr = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp))
                                            Text(
                                                text = timeStr,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Peer Group Chat Card
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.selectPeer("GROUP", "Public Group Chat")
                            onNavigateToChat()
                        }
                        .testTag("group_chat_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🌍 Public Group Chat",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Broadcast messages to all connected peers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Quick Simulation Card
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.startSimulationChat()
                            onNavigateToChat()
                        }
                        .testTag("simulation_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Chat with Peer Bot (Simulation)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Test messaging instantly without a second device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TabRow(
                    selectedTabIndex = peerTabSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = peerTabSelected == 0,
                        onClick = { peerTabSelected = 0 },
                        text = { Text("Active Peers (${activePeers.size})") },
                        modifier = Modifier.testTag("tab_active_peers")
                    )
                    Tab(
                        selected = peerTabSelected == 1,
                        onClick = { peerTabSelected = 1 },
                        text = { Text("Archived (${archivedPeers.size})") },
                        modifier = Modifier.testTag("tab_archived_peers")
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (filteredPeers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No peers match '$searchQuery'" else if (peerTabSelected == 1) "No archived conversations" else "No active peers found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = if (peerTabSelected == 1) "Archived chats will appear here" else "Tap connect button above to share or connect",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredPeers) { peer ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectPeer(peer.address, peer.name)
                                        onNavigateToChat()
                                    }
                                    .testTag("peer_item_${peer.address}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = peer.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = peer.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val presenceColor = when (peer.presenceState) {
                                                "TYPING" -> MaterialTheme.colorScheme.primary
                                                "ONLINE" -> androidx.compose.ui.graphics.Color(0xFF10B981)
                                                else -> if (peer.isConnected) androidx.compose.ui.graphics.Color(0xFF10B981) else androidx.compose.ui.graphics.Color.Gray
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(presenceColor)
                                            )
                                        }
                                        val statusLabel = when {
                                            peer.isBlocked -> "🚫 Blocked • ${peer.address}"
                                            peer.presenceState == "TYPING" -> "✍️ typing..."
                                            peer.presenceState == "ONLINE" -> "Online • ${peer.address}"
                                            else -> if (peer.isConnected) "Online • ${peer.address}" else "Offline • ${peer.address}"
                                        }
                                        Text(
                                            text = statusLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (peer.isBlocked) MaterialTheme.colorScheme.error else if (peer.presenceState == "TYPING") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Archive / Unarchive Button
                                    IconButton(
                                        onClick = {
                                            if (peer.isArchived) {
                                                viewModel.unarchivePeer(peer.address)
                                            } else {
                                                viewModel.archivePeer(peer.address)
                                            }
                                        },
                                        modifier = Modifier.testTag("archive_peer_${peer.address}")
                                    ) {
                                        Icon(
                                            imageVector = if (peer.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                            contentDescription = if (peer.isArchived) "Unarchive Peer" else "Archive Peer",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Icon(
                                        Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = "Chat",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Connect Tabs Dialog (Tab 1: Share Mine, Tab 2: Connect to Peer)
    if (showConnectTabsDialog) {
        val context = LocalContext.current
        val qrData = "p2pconnect://add?ip=${uiState.localIp}&name=${uiState.myName}"

        AlertDialog(
            onDismissRequest = { showConnectTabsDialog = false },
            title = {
                TabRow(selectedTabIndex = connectTabSelected) {
                    Tab(
                        selected = connectTabSelected == 0,
                        onClick = { connectTabSelected = 0 },
                        text = { Text("Share Mine") },
                        modifier = Modifier.testTag("tab_share_mine")
                    )
                    Tab(
                        selected = connectTabSelected == 1,
                        onClick = { connectTabSelected = 1 },
                        text = { Text("Connect to Peer") },
                        modifier = Modifier.testTag("tab_connect_peer")
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (connectTabSelected == 0) {
                        // Share Mine Tab
                        Text(
                            text = "Let nearby peers scan this code or use your IP.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            QrCodeView(
                                data = qrData,
                                modifier = Modifier.size(160.dp),
                                qrColor = MaterialTheme.colorScheme.onSurface,
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                        Text(
                            text = "${uiState.myName} (${uiState.localIp})",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("P2P Profile QR", qrData)
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "Profile link copied!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("copy_profile_link_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy Profile Link")
                        }
                    } else {
                        // Connect to Peer Tab
                        OutlinedButton(
                            onClick = {
                                showConnectTabsDialog = false
                                showScannerDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_qr_button")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan QR via Camera / Image")
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { input ->
                                val parsed = parsePastedConnectionInfo(input)
                                if (parsed != null) {
                                    manualIp = parsed.first
                                    manualName = parsed.second
                                } else {
                                    manualIp = input
                                }
                            },
                            label = { Text("Peer IP Address or Code") },
                            placeholder = { Text("e.g. 192.168.1.5 or Name@IP") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ip_input")
                        )
                        OutlinedTextField(
                            value = manualName,
                            onValueChange = { manualName = it },
                            label = { Text("Peer Name (Optional)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("name_input")
                        )
                        Button(
                            onClick = {
                                if (manualIp.isNotBlank()) {
                                    viewModel.addManualPeer(manualIp, manualName)
                                    showConnectTabsDialog = false
                                    manualIp = ""
                                    manualName = ""
                                    onNavigateToChat()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_connect_button")
                        ) {
                            Text("Connect")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showConnectTabsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Edit Nickname Dialog (triggered by top right settings button)
    if (showEditNicknameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNicknameDialog = false },
            title = { Text("Edit / Create Nickname") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nicknameInput,
                        onValueChange = { nicknameInput = it },
                        label = { Text("Your Nickname") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_nickname_input")
                    )
                    TextButton(
                        onClick = {
                            showEditNicknameDialog = false
                            onNavigateToSettings()
                        },
                        modifier = Modifier.testTag("open_full_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Full Settings & Data Management")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nicknameInput.isNotBlank()) {
                            viewModel.updateNickname(nicknameInput)
                            showEditNicknameDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_nickname_dialog_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNicknameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showScannerDialog) {
        QrScannerDialog(
            onDismiss = { showScannerDialog = false },
            onPeerScanned = { ip, name ->
                viewModel.addManualPeer(ip, name)
                onNavigateToChat()
            }
        )
    }

    if (showHowToDialog) {
        QrScannerHowToDialog(
            onDismiss = { showHowToDialog = false }
        )
    }
}

@Composable
fun QrScannerHowToDialog(onDismiss: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(0) }
    
    val steps = listOf(
        Triple(
            Icons.Default.QrCode,
            "1. Share Your QR Code",
            "Open 'My QR Code' on your screen. Your peer will point their device's camera at it, or you can tap 'Copy Profile Link' to send your address code directly via standard messengers."
        ),
        Triple(
            Icons.Default.QrCodeScanner,
            "2. Scan Peer's Profile QR",
            "Tap 'Scan QR' in the pairing section and align their QR code within the highlighted viewfinder. You can also import their QR code image directly from your photo gallery if they sent a screenshot!"
        ),
        Triple(
            Icons.Default.CheckCircle,
            "3. Connect & Chat Securely",
            "Once matched, the decentralized engine establishes a secure socket connection directly over your local Wi-Fi or cellular network. No central servers are involved!"
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("How to Pair Peers", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            val (icon, title, desc) = steps[currentStep]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Animated indicator dot row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.forEachIndexed { index, _ ->
                        val isCurrent = currentStep == index
                        Box(
                            modifier = Modifier
                                .size(if (isCurrent) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentStep < steps.size - 1) {
                        currentStep++
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(if (currentStep < steps.size - 1) "Next" else "Got It")
            }
        },
        dismissButton = {
            if (currentStep > 0) {
                TextButton(onClick = { currentStep-- }) {
                    Text("Back")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Skip")
                }
            }
        }
    )
}

private fun parsePastedConnectionInfo(input: String): Pair<String, String>? {
    val trimmed = input.trim()
    
    // Check if it's name@ip format
    if (trimmed.contains("@")) {
        val parts = trimmed.split("@")
        if (parts.size == 2) {
            val name = parts[0].trim()
            val ip = parts[1].trim()
            if (ip.contains(".")) {
                return Pair(ip, name)
            }
        }
    }
    
    // Check for multi-line format from standard share intent
    if (trimmed.contains("\n")) {
        var ip = ""
        var name = ""
        val lines = trimmed.lines()
        for (line in lines) {
            val lower = line.lowercase()
            if (lower.contains("code:")) {
                val code = line.substringAfter("code:", "").trim()
                if (code.contains("@")) {
                    val parts = code.split("@")
                    if (parts.size == 2) {
                        return Pair(parts[1].trim(), parts[0].trim())
                    }
                }
            } else if (lower.contains("ip:")) {
                ip = line.substringAfter("ip:", "").trim()
            } else if (lower.contains("name:")) {
                name = line.substringAfter("name:", "").trim()
            }
        }
        if (ip.isNotBlank() && ip.contains(".")) {
            return Pair(ip, name)
        }
    }
    
    return null
}

@Composable
fun AppLogoFavicon(
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val outlineColor = MaterialTheme.colorScheme.outline
    
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .size(24.dp)
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        
        // 1. Draw outer ring path
        drawCircle(
            color = primaryColor.copy(alpha = 0.25f),
            radius = w * 0.45f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
        )
        
        // 2. Draw connection mesh lines
        val strokeW = 1.dp.toPx()
        // Define node positions
        val x1 = cx - w * 0.22f
        val y1 = cy - h * 0.22f
        
        val x2 = cx + w * 0.22f
        val y2 = cy + h * 0.22f
        
        val x3 = cx + w * 0.22f
        val y3 = cy - h * 0.22f
        
        val x4 = cx - w * 0.22f
        val y4 = cy + h * 0.22f
        
        // Mesh lines
        drawLine(color = outlineColor.copy(alpha = 0.4f), start = androidx.compose.ui.geometry.Offset(x1, y1), end = androidx.compose.ui.geometry.Offset(x2, y2), strokeWidth = strokeW)
        drawLine(color = outlineColor.copy(alpha = 0.4f), start = androidx.compose.ui.geometry.Offset(x3, y3), end = androidx.compose.ui.geometry.Offset(x4, y4), strokeWidth = strokeW)
        drawLine(color = outlineColor.copy(alpha = 0.4f), start = androidx.compose.ui.geometry.Offset(x1, y1), end = androidx.compose.ui.geometry.Offset(x3, y3), strokeWidth = strokeW)
        drawLine(color = outlineColor.copy(alpha = 0.4f), start = androidx.compose.ui.geometry.Offset(x1, y1), end = androidx.compose.ui.geometry.Offset(x4, y4), strokeWidth = strokeW)
        drawLine(color = outlineColor.copy(alpha = 0.4f), start = androidx.compose.ui.geometry.Offset(x2, y2), end = androidx.compose.ui.geometry.Offset(x3, y3), strokeWidth = strokeW)
        drawLine(color = outlineColor.copy(alpha = 0.4f), start = androidx.compose.ui.geometry.Offset(x2, y2), end = androidx.compose.ui.geometry.Offset(x4, y4), strokeWidth = strokeW)

        // 3. Draw nodes
        // Indigo node
        drawCircle(color = primaryColor, radius = 2.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x1, y1))
        // Cyan node
        drawCircle(color = androidx.compose.ui.graphics.Color(0xFF0891B2), radius = 2.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x2, y2))
        // Pink node
        drawCircle(color = androidx.compose.ui.graphics.Color(0xFFEC4899), radius = 2.2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x3, y3))
        // Green node
        drawCircle(color = androidx.compose.ui.graphics.Color(0xFF10B981), radius = 2.2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x4, y4))
        
        // 4. Draw central shield/connection highlight
        drawCircle(
            color = primaryColor,
            radius = 3.5.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(cx, cy)
        )
        drawCircle(
            color = androidx.compose.ui.graphics.Color(0xFF22D3EE),
            radius = 1.75.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(cx, cy)
        )
    }
}

