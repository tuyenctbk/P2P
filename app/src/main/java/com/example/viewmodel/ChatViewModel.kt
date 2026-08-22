package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ChatMessage
import com.example.data.ChatPeer
import com.example.data.UserPreferencesRepository
import com.example.network.NetworkChangeEvent
import com.example.network.NetworkConnectivityMonitor
import com.example.network.NetworkStatus
import com.example.network.P2PBackgroundSyncScheduler
import com.example.network.P2PNetworkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val myName: String = "User",
    val localIp: String = "127.0.0.1",
    val selectedPeerAddress: String? = null,
    val selectedPeerName: String? = null,
    val isSelectedPeerBlocked: Boolean = false,
    val activePeerConnectionState: String = "CONNECTED", // CONNECTED, RECONNECTING, OFFLINE
    val isPeerTyping: Boolean = false,
    val discoveredPeers: Map<String, String> = emptyMap(),
    val isScanning: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val chatDao = database.chatDao()
    private val p2pManager = P2PNetworkManager(application)
    private val audioNotifier = com.example.util.AudioNotifier(application)
    private val voiceRecorderManager = com.example.util.VoiceRecorderManager(application)
    val firebaseService = com.example.util.FirebaseService(application)
    val userPreferencesRepository = UserPreferencesRepository(application)
    val connectivityMonitor = NetworkConnectivityMonitor(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Persistent Jetpack DataStore Flows
    val themeMode: StateFlow<String> = userPreferencesRepository.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM")

    val appLanguage: StateFlow<String> = userPreferencesRepository.appLanguageFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM")

    val autoPurgeDuration: StateFlow<String> = userPreferencesRepository.autoPurgeDurationFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "OFF")

    val isPowerSaverEnabled: StateFlow<Boolean> = userPreferencesRepository.powerSaverFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isAutoArchiveEnabled: StateFlow<Boolean> = userPreferencesRepository.autoArchiveFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isDiagnosticOverlayVisible: StateFlow<Boolean> = userPreferencesRepository.diagnosticOverlayFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val isBackgroundSyncEnabled: StateFlow<Boolean> = userPreferencesRepository.backgroundSyncEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val lastBackgroundSync: StateFlow<String?> = userPreferencesRepository.lastBackgroundSyncFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Connectivity & Wi-Fi Listener State
    val networkStatus: StateFlow<NetworkStatus> = connectivityMonitor.networkStatus
    val wifiSwitchBannerVisible: StateFlow<Boolean> = connectivityMonitor.wifiSwitchBannerVisible
    val lastNetworkMessage: StateFlow<String?> = connectivityMonitor.lastNetworkMessage

    val isFirebaseActive: StateFlow<Boolean> = firebaseService.isFirebaseActive
    val firebaseEventsLog: StateFlow<List<String>> = firebaseService.recentEventsLog
    val remoteConfigStatus: StateFlow<String> = firebaseService.remoteConfigStatus
    val remoteAccentColor: StateFlow<String> = firebaseService.uiAccentColor

    // App Health & Diagnostics from Crashlytics and Analytics
    val healthSummary: StateFlow<com.example.util.AppHealthSummary> = com.example.util.P2PExceptionHandler.healthSummary
    val recentErrors: StateFlow<List<com.example.util.P2PDiagnosticErrorEvent>> = com.example.util.P2PExceptionHandler.recentErrors
    val messagesSentCount: StateFlow<Int> = firebaseService.messagesSentCount
    val filesSharedCount: StateFlow<Int> = firebaseService.filesSharedCount
    val discoveriesInitiatedCount: StateFlow<Int> = firebaseService.discoveriesInitiatedCount

    // Firebase App Check with reCAPTCHA flows
    val appCheckStatus: StateFlow<String> = firebaseService.appCheckStatus
    val isAppCheckVerified: StateFlow<Boolean> = firebaseService.isAppCheckVerified
    val lastAppCheckToken: StateFlow<String?> = firebaseService.lastAppCheckToken

    // Dynamic Remote Config Feature Flags
    val isVoiceNotesEnabled: StateFlow<Boolean> = firebaseService.isVoiceNotesEnabled
    val isReactionsEnabled: StateFlow<Boolean> = firebaseService.isReactionsEnabled
    val isAutoPurgeEnabled: StateFlow<Boolean> = firebaseService.isAutoPurgeEnabled
    val isQuickReplyEnabled: StateFlow<Boolean> = firebaseService.isQuickReplyEnabled
    val isAppCheckEnforced: StateFlow<Boolean> = firebaseService.isAppCheckEnforced
    val maxAttachmentSizeMb: StateFlow<Long> = firebaseService.maxAttachmentSizeMb
    val remoteBannerAnnouncement: StateFlow<String> = firebaseService.remoteBannerAnnouncement

    // UX Shimmer Loading States
    private val _isDiscoveryLoading = MutableStateFlow(true)
    val isDiscoveryLoading: StateFlow<Boolean> = _isDiscoveryLoading.asStateFlow()

    private val _isMessageHistoryLoading = MutableStateFlow(false)
    val isMessageHistoryLoading: StateFlow<Boolean> = _isMessageHistoryLoading.asStateFlow()

    val isSoundNotificationsEnabled: StateFlow<Boolean> = audioNotifier.isSoundEnabled
    val isHapticEnabled: StateFlow<Boolean> = audioNotifier.isHapticEnabled

    val isVoiceRecording: StateFlow<Boolean> = voiceRecorderManager.isRecording
    val recordingDurationSec: StateFlow<Int> = voiceRecorderManager.recordingDurationSec
    val currentVoiceAmplitudes: StateFlow<List<Float>> = voiceRecorderManager.currentAmplitudes
    val playingVoiceMessageId: StateFlow<Long?> = voiceRecorderManager.playingMessageId
    val voicePlaybackProgress: StateFlow<Float> = voiceRecorderManager.playbackProgress

    val peerPresence: StateFlow<Map<String, com.example.network.PeerPresenceInfo>> = p2pManager.peerPresence

    private val _globalSearchQuery = MutableStateFlow("")
    val globalSearchQuery: StateFlow<String> = _globalSearchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val globalSearchResults: StateFlow<List<ChatMessage>> = _globalSearchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                chatDao.searchMessages(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalBytesSent: StateFlow<Long> = p2pManager.totalBytesSent
    val totalBytesReceived: StateFlow<Long> = p2pManager.totalBytesReceived
    val totalPackets: StateFlow<Long> = p2pManager.totalPackets
    val sendSpeedBps: StateFlow<Long> = p2pManager.sendSpeedBps
    val recvSpeedBps: StateFlow<Long> = p2pManager.recvSpeedBps
    val batteryLevel: StateFlow<Int> = p2pManager.batteryLevel
    val isBatteryThrottled: StateFlow<Boolean> = p2pManager.isBatteryThrottled

    val savedPeers: StateFlow<List<ChatPeer>> = chatDao.getAllPeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePeers: StateFlow<List<ChatPeer>> = chatDao.getActivePeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedPeers: StateFlow<List<ChatPeer>> = chatDao.getArchivedPeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduledMessages: StateFlow<List<ChatMessage>> = chatDao.getScheduledMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentMessages: Flow<List<ChatMessage>> = _uiState
        .flatMapLatest { state ->
            val address = state.selectedPeerAddress
            if (address != null) {
                chatDao.getMessagesForPeer(address)
            } else {
                flowOf(emptyList())
            }
        }

    init {
        // Initialize Firebase Crashlytics custom exception handler and diagnostics
        com.example.util.P2PExceptionHandler.init(application, firebaseService)

        // Load or generate stable display name from DataStore
        viewModelScope.launch {
            val savedName = userPreferencesRepository.myNicknameFlow.first()
            if (savedName.isNullOrBlank()) {
                val generatedName = "User_${(100..999).random()}"
                userPreferencesRepository.setMyNickname(generatedName)
                _uiState.value = _uiState.value.copy(myName = generatedName)
            } else {
                _uiState.value = _uiState.value.copy(myName = savedName)
            }
        }

        // Schedule WorkManager periodic background sync
        viewModelScope.launch {
            val powerSaver = userPreferencesRepository.powerSaverFlow.first()
            P2PBackgroundSyncScheduler.schedulePeriodicSync(application, powerSaver)
        }

        // Listen for Wi-Fi / subnet network switch events
        viewModelScope.launch {
            connectivityMonitor.networkChangeEvents.collect { event ->
                android.util.Log.i("ChatViewModel", "Network switched to: ${event.currentNetwork} (${event.currentIp})")
                _uiState.value = _uiState.value.copy(localIp = event.currentIp)
                p2pManager.broadcastPresence(_uiState.value.myName)
                triggerDiscoveryRefresh()
            }
        }

        // Load blocked peers and keep in sync with P2PNetworkManager
        viewModelScope.launch {
            chatDao.getBlockedPeerAddressesFlow().collect { addresses ->
                p2pManager.setBlockedPeers(addresses.toSet())
            }
        }

        // Initial auto-purge & auto-archive check from persistent DataStore
        viewModelScope.launch {
            val purgeDuration = userPreferencesRepository.autoPurgeDurationFlow.first()
            if (purgeDuration != "OFF") {
                purgeOldMessages(purgeDuration)
            }
            val autoArchive = userPreferencesRepository.autoArchiveFlow.first()
            if (autoArchive) {
                runAutoArchive()
            }
        }

        // Observe byte transfers for daily diagnostic statistics
        viewModelScope.launch {
            var lastSent = 0L
            p2pManager.totalBytesSent.collect { total ->
                if (lastSent > 0 && total > lastSent) {
                    val diff = total - lastSent
                    updateDailyDataStats(diff, isOutgoing = true)
                }
                lastSent = total
            }
        }

        viewModelScope.launch {
            var lastRecv = 0L
            p2pManager.totalBytesReceived.collect { total ->
                if (lastRecv > 0 && total > lastRecv) {
                    val diff = total - lastRecv
                    updateDailyDataStats(diff, isOutgoing = false)
                }
                lastRecv = total
            }
        }

        // Observe local IP
        viewModelScope.launch {
            p2pManager.localIp.collect { ip ->
                _uiState.value = _uiState.value.copy(localIp = ip)
            }
        }

        // Observe status updates (DELIVERED, READ)
        viewModelScope.launch {
            p2pManager.statusUpdates.collect { event ->
                if (event.newStatus == "READ") {
                    chatDao.markOutgoingMessagesAsRead(event.peerIp)
                } else {
                    chatDao.updateMessageStatusByTimestamp(event.peerIp, event.timestamp, event.newStatus)
                }
            }
        }

        // Observe reaction events
        viewModelScope.launch {
            p2pManager.reactionEvents.collect { event ->
                val msg = chatDao.getMessageByTimestamp(event.peerIp, event.timestamp)
                if (msg != null) {
                    val current = msg.reactions
                    val newReactions = if (current.isBlank()) {
                        event.reaction
                    } else if (!current.contains(event.reaction)) {
                        "$current,${event.reaction}"
                    } else {
                        current
                    }
                    chatDao.updateMessageReactions(msg.id, newReactions)
                }
            }
        }

        // Observe discovered peers via UDP
        viewModelScope.launch {
            p2pManager.discoveredPeers.collect { map ->
                _uiState.value = _uiState.value.copy(discoveredPeers = map)
                for ((ip, name) in map) {
                    val existing = chatDao.getPeer(ip)
                    if (existing?.isBlocked != true) {
                        chatDao.insertPeer(
                            ChatPeer(
                                address = ip,
                                name = name,
                                lastSeen = System.currentTimeMillis(),
                                isConnected = true,
                                presenceState = "ONLINE",
                                isBlocked = false
                            )
                        )
                    }
                }
            }
        }

        // Observe presence & typing updates from network manager
        viewModelScope.launch {
            p2pManager.peerPresence.collect { presenceMap ->
                val currentSelected = _uiState.value.selectedPeerAddress
                if (currentSelected != null && currentSelected != "GROUP" && currentSelected != "127.0.0.2") {
                    val info = presenceMap[currentSelected]
                    val isTyping = info?.presenceState == "TYPING"
                    _uiState.value = _uiState.value.copy(
                        isPeerTyping = isTyping,
                        activePeerConnectionState = if (info != null) "CONNECTED" else _uiState.value.activePeerConnectionState
                    )
                }
            }
        }

        // Observe incoming TCP messages
        viewModelScope.launch {
            p2pManager.incomingMessages.collect { event ->
                val existingPeer = chatDao.getPeer(event.peerIp)
                if (existingPeer?.isBlocked == true) {
                    return@collect
                }
                val message = ChatMessage(
                    peerAddress = event.peerIp,
                    senderName = event.senderName,
                    text = event.text,
                    timestamp = event.timestamp,
                    isOutgoing = false,
                    status = "DELIVERED",
                    attachmentType = event.attachmentType,
                    attachmentData = event.attachmentData
                )
                chatDao.insertMessage(message)

                // Log Analytics event for incoming message
                firebaseService.logMessageReceived(
                    type = event.attachmentType.ifEmpty { "TEXT" },
                    isEncrypted = true,
                    hasAttachment = event.attachmentType != "NONE"
                )

                if (event.attachmentType == "IMAGE" || event.attachmentType == "DOCUMENT") {
                    audioNotifier.playFileTransferSound()
                } else {
                    audioNotifier.playMessageSound()
                }

                if (_uiState.value.selectedPeerAddress == event.peerIp) {
                    markSelectedChatAsRead()
                }

                // Also mirror incoming messages into Group Chat
                val groupMessage = ChatMessage(
                    peerAddress = "GROUP",
                    senderName = event.senderName,
                    text = event.text,
                    timestamp = event.timestamp,
                    isOutgoing = false,
                    status = "DELIVERED",
                    attachmentType = event.attachmentType,
                    attachmentData = event.attachmentData
                )
                chatDao.insertMessage(groupMessage)

                // Ensure sender is in saved peers
                chatDao.insertPeer(
                    ChatPeer(
                        address = event.peerIp,
                        name = event.senderName,
                        lastSeen = System.currentTimeMillis(),
                        isConnected = true,
                        presenceState = "ONLINE",
                        isBlocked = false
                    )
                )
            }
        }

        // Heartbeat, scheduled messages & keep-alive monitor loop
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(3000)
                p2pManager.broadcastPresence(_uiState.value.myName, "ONLINE")

                val presenceMap = p2pManager.peerPresence.value
                val peers = chatDao.getAllPeersList()
                val now = System.currentTimeMillis()

                for (peer in peers) {
                    if (peer.address == "127.0.0.2" || peer.address == "GROUP") {
                        chatDao.insertPeer(peer.copy(isConnected = true, presenceState = "ONLINE", lastSeen = now))
                    } else {
                        val info = presenceMap[peer.address]
                        if (info != null && (now - info.lastSeen) < 12000) {
                            chatDao.insertPeer(peer.copy(isConnected = true, presenceState = info.presenceState, lastSeen = info.lastSeen))
                        } else if ((now - peer.lastSeen) > 15000) {
                            chatDao.insertPeer(peer.copy(isConnected = false, presenceState = "OFFLINE"))
                        }
                    }
                }

                // Process scheduled messages that are due
                processPendingScheduledMessages()
            }
        }

        // Initial broadcast & discovery shimmer transition
        broadcastPresence()
        viewModelScope.launch {
            kotlinx.coroutines.delay(1200)
            _isDiscoveryLoading.value = false
        }
    }

    fun updateNickname(newName: String) {
        if (newName.isNotBlank()) {
            val trimmed = newName.trim()
            _uiState.value = _uiState.value.copy(myName = trimmed)
            viewModelScope.launch {
                userPreferencesRepository.setMyNickname(trimmed)
            }
            broadcastPresence()
        }
    }

    fun selectPeer(address: String?, name: String?) {
        _isMessageHistoryLoading.value = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            _isMessageHistoryLoading.value = false
        }
        viewModelScope.launch {
            val peer = if (address != null) chatDao.getPeer(address) else null
            _uiState.value = _uiState.value.copy(
                selectedPeerAddress = address,
                selectedPeerName = name ?: address,
                isSelectedPeerBlocked = peer?.isBlocked == true
            )
            if (address != null) {
                markSelectedChatAsRead()
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(mode)
        }
        firebaseService.logThemeChanged(mode)
    }

    fun setAppLanguage(languageCode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setAppLanguage(languageCode)
        }
    }

    fun markSelectedChatAsRead() {
        val address = _uiState.value.selectedPeerAddress ?: return
        viewModelScope.launch {
            chatDao.markIncomingMessagesAsRead(address)
            p2pManager.sendReadReceipt(address, _uiState.value.myName)
        }
    }

    fun toggleReaction(message: ChatMessage, emoji: String) {
        viewModelScope.launch {
            val current = message.reactions
            val updatedReactions = if (current.isBlank()) {
                emoji
            } else if (current.split(",").contains(emoji)) {
                current.split(",").filter { it != emoji }.joinToString(",")
            } else {
                "$current,$emoji"
            }
            chatDao.updateMessageReactions(message.id, updatedReactions)
            firebaseService.logReactionAdded(emoji)

            if (message.peerAddress != "GROUP" && message.peerAddress != "127.0.0.1") {
                p2pManager.sendReaction(message.peerAddress, _uiState.value.myName, message.timestamp, emoji)
            }
        }
    }

    fun setGlobalSearchQuery(query: String) {
        _globalSearchQuery.value = query
    }

    fun exportTranscriptAsText(messages: List<ChatMessage>, peerName: String): String {
        val sb = StringBuilder()
        sb.appendLine("==========================================")
        sb.appendLine("P2P CONNECT - CHAT TRANSCRIPT")
        sb.appendLine("Peer: $peerName")
        sb.appendLine("Exported At: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        sb.appendLine("==========================================\n")
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        for (msg in messages) {
            val dateStr = sdf.format(java.util.Date(msg.timestamp))
            val sender = if (msg.isOutgoing) "You" else msg.senderName
            val statusText = if (msg.isOutgoing) " [${msg.status}]" else ""
            val attachment = if (msg.attachmentType != "NONE") " <Attachment: ${msg.attachmentType}>" else ""
            val reactions = if (msg.reactions.isNotBlank()) " (Reactions: ${msg.reactions})" else ""
            sb.appendLine("[$dateStr] $sender: ${msg.text}$attachment$reactions$statusText")
        }
        return sb.toString()
    }

    fun exportTranscriptAsJson(messages: List<ChatMessage>, peerName: String): String {
        val jsonArray = org.json.JSONArray()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
        for (msg in messages) {
            val obj = org.json.JSONObject().apply {
                put("id", msg.id)
                put("peerAddress", msg.peerAddress)
                put("senderName", msg.senderName)
                put("text", msg.text)
                put("timestamp", msg.timestamp)
                put("isoTime", sdf.format(java.util.Date(msg.timestamp)))
                put("isOutgoing", msg.isOutgoing)
                put("status", msg.status)
                put("attachmentType", msg.attachmentType)
                put("attachmentData", msg.attachmentData)
                put("reactions", msg.reactions)
            }
            jsonArray.put(obj)
        }
        val root = org.json.JSONObject().apply {
            put("peerName", peerName)
            put("exportedAt", System.currentTimeMillis())
            put("messagesCount", messages.size)
            put("messages", jsonArray)
        }
        return root.toString(2)
    }

    fun toggleBlockSelectedPeer() {
        val address = _uiState.value.selectedPeerAddress ?: return
        if (_uiState.value.isSelectedPeerBlocked) {
            unblockPeer(address)
        } else {
            blockPeer(address)
        }
    }

    fun blockPeer(address: String) {
        viewModelScope.launch {
            chatDao.updatePeerBlockedStatus(address, true)
            val blocked = chatDao.getAllPeersList().filter { it.isBlocked }.map { it.address }.toSet()
            p2pManager.setBlockedPeers(blocked)
            firebaseService.logPeerBlocked(address)
            if (_uiState.value.selectedPeerAddress == address) {
                _uiState.value = _uiState.value.copy(isSelectedPeerBlocked = true)
            }
        }
    }

    fun unblockPeer(address: String) {
        viewModelScope.launch {
            chatDao.updatePeerBlockedStatus(address, false)
            val blocked = chatDao.getAllPeersList().filter { it.isBlocked }.map { it.address }.toSet()
            p2pManager.setBlockedPeers(blocked)
            firebaseService.logEvent("peer_unblocked", mapOf("peer_hash" to address.hashCode().toString()))
            if (_uiState.value.selectedPeerAddress == address) {
                _uiState.value = _uiState.value.copy(isSelectedPeerBlocked = false)
            }
        }
    }

    fun broadcastPresence() {
        p2pManager.broadcastPresence(_uiState.value.myName)
    }

    fun addManualPeer(address: String, name: String) {
        viewModelScope.launch {
            if (address.isNotBlank()) {
                val peerName = if (name.isBlank()) address else name
                chatDao.insertPeer(ChatPeer(address = address.trim(), name = peerName.trim(), lastSeen = System.currentTimeMillis(), isConnected = true))
                firebaseService.logPeerConnected(address.trim(), isManual = true)
                selectPeer(address.trim(), peerName.trim())
            }
        }
    }

    fun sendMessage(text: String) {
        sendAttachment(text, "NONE", "")
    }

    fun sendAttachment(text: String, attachmentType: String, attachmentData: String) {
        val targetIp = _uiState.value.selectedPeerAddress ?: return
        if (text.isBlank() && attachmentType == "NONE") return

        val myName = _uiState.value.myName
        viewModelScope.launch {
            firebaseService.logMessageSent(
                type = if (attachmentType != "NONE") attachmentType else "TEXT",
                isEncrypted = true,
                hasAttachment = attachmentType != "NONE",
                isScheduled = false
            )

            if (targetIp == "GROUP") {
                val outgoingMsg = ChatMessage(
                    peerAddress = "GROUP",
                    senderName = myName,
                    text = text.trim(),
                    timestamp = System.currentTimeMillis(),
                    isOutgoing = true,
                    status = "DELIVERED",
                    attachmentType = attachmentType,
                    attachmentData = attachmentData
                )
                chatDao.insertMessage(outgoingMsg)

                val allPeers = chatDao.getAllPeersList()
                for (peer in allPeers) {
                    if (!peer.isBlocked && peer.address != "GROUP" && peer.address != "127.0.0.2") {
                        p2pManager.sendMessage(peer.address, text.trim(), myName, attachmentType, attachmentData)
                    }
                }
                return@launch
            }

            val peer = chatDao.getPeer(targetIp)
            if (peer?.isBlocked == true) {
                return@launch
            }

            val outgoingMsg = ChatMessage(
                peerAddress = targetIp,
                senderName = myName,
                text = text.trim(),
                timestamp = System.currentTimeMillis(),
                isOutgoing = true,
                status = "SENT",
                attachmentType = attachmentType,
                attachmentData = attachmentData
            )
            val insertedId = chatDao.insertMessage(outgoingMsg)

            if (targetIp == "127.0.0.2") {
                simulateBotReply(if (text.isBlank()) "Sent $attachmentType attachment" else text.trim())
                return@launch
            }

            val success = p2pManager.sendMessage(targetIp, text.trim(), myName, attachmentType, attachmentData)
            val updatedStatus = if (success) "DELIVERED" else "FAILED"
            chatDao.updateMessageStatus(insertedId, updatedStatus)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleSearch() {
        val active = !_uiState.value.isSearchActive
        _uiState.value = _uiState.value.copy(isSearchActive = active, searchQuery = if (!active) "" else _uiState.value.searchQuery)
    }

    private fun simulateBotReply(userText: String) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            val botReplies = listOf(
                "Hello! Received your message: '$userText'. P2P connection is secure and working!",
                "That's awesome! How can I help you on this peer-to-peer network?",
                "Connection stable over local socket. Standing by for more chat!",
                "Got it! Decentralized chat is super fast and private."
            )
            val replyText = botReplies.random()
            val botMsg = ChatMessage(
                peerAddress = "127.0.0.2",
                senderName = "Peer Bot",
                text = replyText,
                timestamp = System.currentTimeMillis(),
                isOutgoing = false,
                status = "DELIVERED"
            )
            chatDao.insertMessage(botMsg)
        }
    }

    fun startSimulationChat() {
        viewModelScope.launch {
            val botIp = "127.0.0.2"
            val botName = "Peer Bot (Simulation)"
            chatDao.insertPeer(ChatPeer(address = botIp, name = botName, lastSeen = System.currentTimeMillis(), isConnected = true, latencyMs = 4L))
            selectPeer(botIp, botName)
        }
    }

    fun sendTypingSignal(isTyping: Boolean) {
        val selectedAddress = _uiState.value.selectedPeerAddress ?: return
        p2pManager.broadcastTyping(_uiState.value.myName, isTyping)
        if (selectedAddress == "127.0.0.2") {
            viewModelScope.launch {
                if (isTyping) {
                    _uiState.value = _uiState.value.copy(isPeerTyping = true)
                } else {
                    kotlinx.coroutines.delay(1000)
                    _uiState.value = _uiState.value.copy(isPeerTyping = false)
                }
            }
        }
    }

    fun sendAttachmentFromUri(context: Context, uri: android.net.Uri, isImage: Boolean) {
        viewModelScope.launch {
            try {
                var fileName = "file_${System.currentTimeMillis()}"
                var fileSizeFormatted = "Unknown size"
                var mimeType = if (isImage) "image/png" else "application/octet-stream"

                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (nameIndex != -1) {
                            val name = it.getString(nameIndex)
                            if (!name.isNullOrBlank()) fileName = name
                        }
                        if (sizeIndex != -1) {
                            val sizeBytes = it.getLong(sizeIndex)
                            fileSizeFormatted = when {
                                sizeBytes < 1024 -> "$sizeBytes B"
                                sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
                                else -> String.format("%.1f MB", sizeBytes / (1024f * 1024f))
                            }
                        }
                    }
                }

                val typeFromResolver = context.contentResolver.getType(uri)
                if (!typeFromResolver.isNullOrBlank()) {
                    mimeType = typeFromResolver
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() } ?: byteArrayOf()
                val base64Data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

                val metaJson = org.json.JSONObject().apply {
                    put("fileName", fileName)
                    put("fileSize", fileSizeFormatted)
                    put("mimeType", mimeType)
                    put("base64", base64Data)
                }.toString()

                val attType = if (isImage || mimeType.startsWith("image/")) "IMAGE" else "DOCUMENT"
                firebaseService.logFileShared(
                    fileName = fileName,
                    fileSizeBytes = bytes.size.toLong(),
                    mimeType = mimeType,
                    isSuccess = true,
                    transferMode = "P2P_DIRECT"
                )
                sendAttachment(text = "", attachmentType = attType, attachmentData = metaJson)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error processing attachment Uri", e)
                com.example.util.P2PExceptionHandler.recordGeneralException(e, "ATTACHMENT_PROCESSING")
            }
        }
    }

    fun sendImageBitmap(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            try {
                val outputStream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
                val bytes = outputStream.toByteArray()
                val base64Data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val fileName = "camera_${System.currentTimeMillis()}.jpg"
                val fileSizeFormatted = String.format("%.1f KB", bytes.size / 1024f)
                val metaJson = org.json.JSONObject().apply {
                    put("fileName", fileName)
                    put("fileSize", fileSizeFormatted)
                    put("mimeType", "image/jpeg")
                    put("base64", base64Data)
                }.toString()
                firebaseService.logFileShared(
                    fileName = fileName,
                    fileSizeBytes = bytes.size.toLong(),
                    mimeType = "image/jpeg",
                    isSuccess = true,
                    transferMode = "P2P_DIRECT"
                )
                sendAttachment(text = "", attachmentType = "IMAGE", attachmentData = metaJson)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error sending camera bitmap", e)
                com.example.util.P2PExceptionHandler.recordGeneralException(e, "CAMERA_ATTACHMENT")
            }
        }
    }

    fun reconnectPeer(address: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(activePeerConnectionState = "RECONNECTING")
            kotlinx.coroutines.delay(600)
            val peerName = _uiState.value.selectedPeerName ?: address
            val latency = if (address == "127.0.0.2") 4L else p2pManager.pingPeer(address)
            val isConnected = latency >= 0
            val presenceState = if (isConnected) "ONLINE" else "OFFLINE"
            chatDao.insertPeer(
                ChatPeer(
                    address = address,
                    name = peerName,
                    lastSeen = System.currentTimeMillis(),
                    isConnected = isConnected,
                    presenceState = presenceState,
                    latencyMs = if (latency >= 0) latency else 0L
                )
            )
            _uiState.value = _uiState.value.copy(
                activePeerConnectionState = if (isConnected) "CONNECTED" else "OFFLINE"
            )
        }
    }

    fun pingSelectedPeer() {
        val targetIp = _uiState.value.selectedPeerAddress ?: return
        reconnectPeer(targetIp)
    }

    fun scheduleMessage(
        text: String,
        scheduledTime: Long
    ) {
        val target = _uiState.value.selectedPeerAddress ?: return
        scheduleMessage(target, text, scheduledTime)
    }

    fun scheduleMessage(
        targetAddress: String,
        text: String,
        scheduledTime: Long,
        attachmentType: String = "NONE",
        attachmentData: String = ""
    ) {
        if (text.isBlank() && attachmentType == "NONE") return
        val myName = _uiState.value.myName
        viewModelScope.launch {
            val msg = ChatMessage(
                peerAddress = targetAddress,
                senderName = myName,
                text = text.trim(),
                timestamp = scheduledTime,
                isOutgoing = true,
                status = "SCHEDULED",
                attachmentType = attachmentType,
                attachmentData = attachmentData,
                scheduledTime = scheduledTime
            )
            chatDao.insertMessage(msg)
        }
    }

    private suspend fun processPendingScheduledMessages() {
        val now = System.currentTimeMillis()
        val pending = chatDao.getPendingScheduledMessages(now)
        val myName = _uiState.value.myName

        for (msg in pending) {
            val peerAddress = msg.peerAddress
            if (peerAddress == "127.0.0.2") {
                chatDao.updateMessageStatusAndTimestamp(msg.id, "DELIVERED", System.currentTimeMillis())
                simulateBotReply("Received scheduled message: '${msg.text}'")
            } else if (peerAddress == "GROUP") {
                val allPeers = chatDao.getAllPeersList()
                var anySent = false
                for (peer in allPeers) {
                    if (!peer.isBlocked && peer.address != "GROUP" && peer.address != "127.0.0.2") {
                        val ok = p2pManager.sendMessage(peer.address, msg.text, myName, msg.attachmentType, msg.attachmentData)
                        if (ok) anySent = true
                    }
                }
                chatDao.updateMessageStatusAndTimestamp(msg.id, if (anySent) "DELIVERED" else "SENT", System.currentTimeMillis())
            } else {
                // Check if peer is reachable
                val peer = chatDao.getPeer(peerAddress)
                val isDiscovered = p2pManager.discoveredPeers.value.containsKey(peerAddress)
                if (peer?.isConnected == true || isDiscovered) {
                    val success = p2pManager.sendMessage(peerAddress, msg.text, myName, msg.attachmentType, msg.attachmentData)
                    if (success) {
                        chatDao.updateMessageStatusAndTimestamp(msg.id, "DELIVERED", System.currentTimeMillis())
                    }
                }
            }
        }
    }

    fun archivePeer(address: String) {
        viewModelScope.launch {
            chatDao.updatePeerArchivedStatus(address, true)
            if (_uiState.value.selectedPeerAddress == address) {
                _uiState.value = _uiState.value.copy(selectedPeerAddress = null, selectedPeerName = null)
            }
        }
    }

    fun unarchivePeer(address: String) {
        viewModelScope.launch {
            chatDao.updatePeerArchivedStatus(address, false)
        }
    }

    fun toggleDiagnosticOverlay() {
        val current = isDiagnosticOverlayVisible.value
        viewModelScope.launch {
            userPreferencesRepository.setDiagnosticOverlayVisible(!current)
        }
    }

    fun setSoundNotificationsEnabled(enabled: Boolean) {
        audioNotifier.setSoundEnabled(enabled)
        viewModelScope.launch {
            userPreferencesRepository.setSoundEnabled(enabled)
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        audioNotifier.setHapticEnabled(enabled)
        viewModelScope.launch {
            userPreferencesRepository.setHapticEnabled(enabled)
        }
    }

    fun setPowerSaverEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setPowerSaverEnabled(enabled)
            P2PBackgroundSyncScheduler.schedulePeriodicSync(getApplication(), enabled)
        }
        p2pManager.checkBatteryState()
    }

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBackgroundSyncEnabled(enabled)
            if (enabled) {
                val powerSaver = isPowerSaverEnabled.value
                P2PBackgroundSyncScheduler.schedulePeriodicSync(getApplication(), powerSaver)
            } else {
                P2PBackgroundSyncScheduler.cancelBackgroundSync(getApplication())
            }
        }
    }

    fun triggerImmediateBackgroundSync() {
        P2PBackgroundSyncScheduler.triggerOneTimeSync(getApplication())
    }

    fun dismissWifiSwitchBanner() {
        connectivityMonitor.dismissWifiSwitchBanner()
    }

    fun clearHistory() {
        viewModelScope.launch {
            chatDao.clearMessages()
        }
    }

    fun setAutoArchiveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoArchiveEnabled(enabled)
        }
        if (enabled) {
            runAutoArchive()
        }
    }

    fun runAutoArchive() {
        viewModelScope.launch {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            val peers = chatDao.getAllPeersList()
            for (peer in peers) {
                if (peer.lastSeen < thirtyDaysAgo && !peer.isArchived) {
                    chatDao.updatePeerArchivedStatus(peer.address, true)
                }
            }
        }
    }

    private fun updateDailyDataStats(bytes: Long, isOutgoing: Boolean) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val todayStr = sdf.format(java.util.Date())
        val prefs = getApplication<Application>().getSharedPreferences("p2p_prefs", Context.MODE_PRIVATE)
        val key = if (isOutgoing) "data_sent_$todayStr" else "data_recv_$todayStr"
        val current = prefs.getLong(key, 0L)
        prefs.edit().putLong(key, current + bytes).apply()
    }

    fun getDailyDataStats(): List<Pair<String, Pair<Long, Long>>> {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val labelSdf = java.text.SimpleDateFormat("E", java.util.Locale.getDefault())
        val prefs = getApplication<Application>().getSharedPreferences("p2p_prefs", Context.MODE_PRIVATE)
        val calendar = java.util.Calendar.getInstance()
        val stats = mutableListOf<Pair<String, Pair<Long, Long>>>()

        for (i in 0 until 7) {
            val date = calendar.time
            val dateStr = sdf.format(date)
            val label = labelSdf.format(date)
            
            val sent = prefs.getLong("data_sent_$dateStr", 0L)
            val recv = prefs.getLong("data_recv_$dateStr", 0L)
            stats.add(Pair(label, Pair(sent, recv)))
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        
        val allZero = stats.all { it.second.first == 0L && it.second.second == 0L }
        if (allZero) {
            val tempCalendar = java.util.Calendar.getInstance()
            val mockValues = listOf(
                Pair(1500000L, 2300000L),
                Pair(4500000L, 8200000L),
                Pair(3100000L, 4100000L),
                Pair(6200000L, 9500000L),
                Pair(1200000L, 1800000L),
                Pair(8000000L, 11000000L),
                Pair(2500000L, 3500000L)
            )
            for (i in 0 until 7) {
                val dateStr = sdf.format(tempCalendar.time)
                val (mSent, mRecv) = mockValues[i]
                prefs.edit()
                    .putLong("data_sent_$dateStr", mSent)
                    .putLong("data_recv_$dateStr", mRecv)
                    .apply()
                tempCalendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            stats.clear()
            val recCalendar = java.util.Calendar.getInstance()
            for (i in 0 until 7) {
                val date = recCalendar.time
                val dateStr = sdf.format(date)
                val label = labelSdf.format(date)
                val sent = prefs.getLong("data_sent_$dateStr", 0L)
                val recv = prefs.getLong("data_recv_$dateStr", 0L)
                stats.add(Pair(label, Pair(sent, recv)))
                recCalendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
        }
        
        return stats.reversed()
    }

    // --- Voice Recording & Audio Playback ---

    fun startVoiceRecording(): Boolean {
        return voiceRecorderManager.startRecording()
    }

    fun cancelVoiceRecording() {
        voiceRecorderManager.cancelRecording()
    }

    fun sendVoiceRecording() {
        viewModelScope.launch {
            val clipResult = voiceRecorderManager.stopRecording()
            if (clipResult != null) {
                val jsonMeta = org.json.JSONObject().apply {
                    put("durationSec", clipResult.durationSec)
                    put("base64", clipResult.base64Audio)
                    put("fileName", clipResult.fileName)
                    put("fileSize", clipResult.fileSize)
                    put("mimeType", "audio/mp4")
                }.toString()
                firebaseService.logVoiceClipRecorded(
                    durationSec = clipResult.durationSec,
                    sizeKb = clipResult.fileSize.replace(" KB", "").toFloatOrNull() ?: 0f
                )
                sendAttachment(text = "", attachmentType = "VOICE", attachmentData = jsonMeta)
            }
        }
    }

    fun togglePlayVoice(messageId: Long, base64Audio: String) {
        firebaseService.logVoiceClipPlayed(0)
        voiceRecorderManager.playVoiceClip(messageId, base64Audio)
    }

    fun stopVoicePlayback() {
        voiceRecorderManager.stopPlayback()
    }

    // --- Auto-Purge Old Messages Settings & Operations ---

    fun setAutoPurgeDuration(duration: String) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoPurgeDuration(duration)
        }
        if (duration != "OFF") {
            viewModelScope.launch {
                purgeOldMessages(duration)
            }
        }
    }

    suspend fun purgeOldMessages(durationKey: String = autoPurgeDuration.value): Int {
        val durationMillis = when (durationKey) {
            "24_HOURS" -> 24L * 60 * 60 * 1000
            "7_DAYS" -> 7L * 24 * 60 * 60 * 1000
            "30_DAYS" -> 30L * 24 * 60 * 60 * 1000
            "90_DAYS" -> 90L * 24 * 60 * 60 * 1000
            else -> return 0
        }
        val cutoff = System.currentTimeMillis() - durationMillis
        val deletedCount = chatDao.purgeMessagesOlderThan(cutoff)
        if (deletedCount > 0) {
            firebaseService.logMessagesPurged(deletedCount, durationKey)
        }
        return deletedCount
    }

    suspend fun getPurgeCandidateCount(durationKey: String = autoPurgeDuration.value): Int {
        val durationMillis = when (durationKey) {
            "24_HOURS" -> 24L * 60 * 60 * 1000
            "7_DAYS" -> 7L * 24 * 60 * 60 * 1000
            "30_DAYS" -> 30L * 24 * 60 * 60 * 1000
            "90_DAYS" -> 90L * 24 * 60 * 60 * 1000
            else -> return 0
        }
        val cutoff = System.currentTimeMillis() - durationMillis
        return chatDao.countMessagesOlderThan(cutoff)
    }

    // --- Firebase Free Services Diagnostic & Configuration ---

    fun testFirebaseDiagnostics() {
        firebaseService.sendTestDiagnosticCrashlyticsLog()
    }

    fun refreshRemoteConfig() {
        firebaseService.fetchRemoteConfig()
    }

    fun logCustomScreenView(screenName: String) {
        firebaseService.logScreenView(screenName)
    }

    fun verifyAppCheck(forceRefresh: Boolean = false, onComplete: (com.example.util.AppCheckVerificationResult) -> Unit = {}) {
        firebaseService.verifyTrafficIntegrity(forceRefresh, onComplete)
    }

    fun updateRemoteFeatureFlag(key: String, enabled: Boolean) {
        firebaseService.updateRemoteFeatureFlag(key, enabled)
    }

    fun updateRemoteConfigLong(key: String, value: Long) {
        firebaseService.updateRemoteConfigLong(key, value)
    }

    fun updateRemoteBanner(bannerText: String) {
        firebaseService.updateRemoteBanner(bannerText)
    }

    fun updateRemoteAccentColor(colorHex: String) {
        firebaseService.updateRemoteAccentColor(colorHex)
    }

    fun triggerTestSocketError(targetIp: String = "192.168.1.254") {
        com.example.util.P2PExceptionHandler.triggerTestSocketError(targetIp)
    }

    fun triggerTestBackgroundSyncError() {
        com.example.util.P2PExceptionHandler.triggerTestBackgroundSyncError()
    }

    fun clearDiagnosticLogs() {
        com.example.util.P2PExceptionHandler.clearDiagnosticLogs()
    }

    val bleFallbackStatus: StateFlow<String> = p2pManager.bleFallbackStatus
    val blePeers: StateFlow<Map<String, com.example.network.BleDiscoveredPeer>> = p2pManager.blePeers

    fun connectToPeerFromQrScan(ip: String, name: String) {
        viewModelScope.launch {
            p2pManager.registerDiscoveredPeer(ip, name, "QR_HANDSHAKE")
            val pingMs = p2pManager.pingPeer(ip)
            if (pingMs >= 0) {
                p2pManager.sendMessage(
                    targetIp = ip,
                    myName = _uiState.value.myName,
                    text = "🤝 Secure P2P Handshake established via QR Scan!"
                )
            }
            selectPeer(ip, name)
        }
    }
    fun triggerDiscoveryRefresh() {
        _isDiscoveryLoading.value = true
        firebaseService.logPeerDiscoveryInitiated(
            triggerSource = "MANUAL_SCAN",
            subnetPrefix = networkStatus.value.subnetPrefix,
            broadcastPort = 8889,
            knownPeersCount = uiState.value.discoveredPeers.size
        )
        broadcastPresence()
        viewModelScope.launch {
            kotlinx.coroutines.delay(900)
            _isDiscoveryLoading.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecorderManager.stopPlayback()
        audioNotifier.release()
        p2pManager.stop()
    }
}
