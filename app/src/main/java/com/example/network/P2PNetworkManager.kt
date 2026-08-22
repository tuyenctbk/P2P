package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.util.P2PExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

data class IncomingMessageEvent(
    val peerIp: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val attachmentType: String = "NONE",
    val attachmentData: String = ""
)

data class StatusUpdateEvent(
    val peerIp: String,
    val timestamp: Long,
    val newStatus: String // DELIVERED, READ
)

data class ReactionEvent(
    val peerIp: String,
    val timestamp: Long,
    val reaction: String,
    val senderName: String
)

data class PeerPresenceInfo(
    val ip: String,
    val name: String,
    val presenceState: String = "ONLINE", // ONLINE, TYPING, OFFLINE
    val lastSeen: Long = System.currentTimeMillis(),
    val batteryLevel: Int = 100
)

class P2PNetworkManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _incomingMessages = MutableSharedFlow<IncomingMessageEvent>()
    val incomingMessages: SharedFlow<IncomingMessageEvent> = _incomingMessages.asSharedFlow()

    private val _statusUpdates = MutableSharedFlow<StatusUpdateEvent>()
    val statusUpdates: SharedFlow<StatusUpdateEvent> = _statusUpdates.asSharedFlow()

    private val _reactionEvents = MutableSharedFlow<ReactionEvent>()
    val reactionEvents: SharedFlow<ReactionEvent> = _reactionEvents.asSharedFlow()

    private val _discoveredPeers = MutableStateFlow<Map<String, String>>(emptyMap()) // IP -> Name
    val discoveredPeers: StateFlow<Map<String, String>> = _discoveredPeers.asStateFlow()

    private val _peerPresence = MutableStateFlow<Map<String, PeerPresenceInfo>>(emptyMap()) // IP -> PresenceInfo
    val peerPresence: StateFlow<Map<String, PeerPresenceInfo>> = _peerPresence.asStateFlow()

    private val _localIp = MutableStateFlow("127.0.0.1")
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    private val _totalBytesSent = MutableStateFlow(0L)
    val totalBytesSent: StateFlow<Long> = _totalBytesSent.asStateFlow()

    private val _totalBytesReceived = MutableStateFlow(0L)
    val totalBytesReceived: StateFlow<Long> = _totalBytesReceived.asStateFlow()

    private val _totalPackets = MutableStateFlow(0L)
    val totalPackets: StateFlow<Long> = _totalPackets.asStateFlow()

    private val _sendSpeedBps = MutableStateFlow(0L)
    val sendSpeedBps: StateFlow<Long> = _sendSpeedBps.asStateFlow()

    private val _recvSpeedBps = MutableStateFlow(0L)
    val recvSpeedBps: StateFlow<Long> = _recvSpeedBps.asStateFlow()

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _isBatteryThrottled = MutableStateFlow(false)
    val isBatteryThrottled: StateFlow<Boolean> = _isBatteryThrottled.asStateFlow()

    private val _blockedPeers = MutableStateFlow<Set<String>>(emptySet())

    val bleDiscoveryManager = BleDiscoveryManager(context)
    val blePeers: StateFlow<Map<String, BleDiscoveredPeer>> = bleDiscoveryManager.blePeers
    val bleFallbackStatus: StateFlow<String> = bleDiscoveryManager.fallbackStatus

    fun setBlockedPeers(peers: Set<String>) {
        _blockedPeers.value = peers
    }

    fun startBleFallbackDiscovery(myName: String) {
        bleDiscoveryManager.startBleAdvertising(myName, _localIp.value)
        bleDiscoveryManager.startBleScanning { ip, name ->
            registerDiscoveredPeer(ip, name, "BLE_NEARBY")
        }
    }

    fun registerDiscoveredPeer(ip: String, name: String, source: String = "MANUAL") {
        if (_blockedPeers.value.contains(ip)) return
        val currentPeers = _discoveredPeers.value.toMutableMap()
        currentPeers[ip] = name
        _discoveredPeers.value = currentPeers

        val presenceMap = _peerPresence.value.toMutableMap()
        presenceMap[ip] = PeerPresenceInfo(
            ip = ip,
            name = name,
            presenceState = "ONLINE",
            lastSeen = System.currentTimeMillis()
        )
        _peerPresence.value = presenceMap
    }

    private var lastBytesSent = 0L
    private var lastBytesReceived = 0L

    private var serverSocket: ServerSocket? = null
    private var isServerRunning = false
    private var udpSocket: DatagramSocket? = null
    private var isUdpRunning = false

    private val PORT = 8888
    private val UDP_PORT = 8889

    init {
        updateLocalIp()
        startServer()
        startUdpDiscovery()
        startSpeedAndBatteryTicker()
    }

    private fun startSpeedAndBatteryTicker() {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val currentSent = _totalBytesSent.value
                val currentRecv = _totalBytesReceived.value

                _sendSpeedBps.value = (currentSent - lastBytesSent).coerceAtLeast(0L)
                _recvSpeedBps.value = (currentRecv - lastBytesReceived).coerceAtLeast(0L)

                lastBytesSent = currentSent
                lastBytesReceived = currentRecv

                checkBatteryState()
            }
        }
    }

    fun checkBatteryState() {
        try {
            val intentFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
            _batteryLevel.value = pct

            val status = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL

            val prefs = context.getSharedPreferences("p2p_prefs", Context.MODE_PRIVATE)
            val forceBatterySaver = prefs.getBoolean("power_saver_enabled", false)

            _isBatteryThrottled.value = (pct <= 20 && !isCharging) || forceBatterySaver
        } catch (e: Exception) {
            Log.e("P2PNetwork", "Error checking battery status", e)
        }
    }

    fun updateLocalIp() {
        val ip = getDeviceIpAddress()
        _localIp.value = ip
    }

    private fun getDeviceIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addrs = Collections.list(networkInterface.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: continue
                        // Check if IPv4
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) {
                            return sAddr
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("P2PNetwork", "Error getting IP", e)
        }
        return "127.0.0.1"
    }

    private fun startServer() {
        if (isServerRunning) return
        scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                isServerRunning = true
                Log.d("P2PNetwork", "TCP Server started on port $PORT")
                while (isServerRunning && serverSocket?.isClosed == false) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        handleClientConnection(clientSocket)
                    } catch (e: Exception) {
                        if (!isServerRunning) break
                        Log.e("P2PNetwork", "Accept error", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("P2PNetwork", "Server start error", e)
                isServerRunning = false
            }
        }
    }

    private fun handleClientConnection(socket: Socket) {
        scope.launch(Dispatchers.IO) {
            val peerIp = socket.inetAddress.hostAddress ?: "unknown"
            if (_blockedPeers.value.contains(peerIp)) {
                Log.i("P2PNetwork", "Rejected connection from blocked peer $peerIp")
                socket.close()
                return@launch
            }
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val line = reader.readLine()
                if (line != null) {
                    val json = JSONObject(line)
                    val type = json.optString("type", "MSG")
                    if (type == "PING") {
                        val writer = PrintWriter(socket.getOutputStream(), true)
                        writer.println("ACK")
                        return@launch
                    }

                    if (type == "READ_RECEIPT") {
                        val timestamp = json.optLong("timestamp", 0L)
                        _statusUpdates.emit(StatusUpdateEvent(peerIp, timestamp, "READ"))
                        val writer = PrintWriter(socket.getOutputStream(), true)
                        writer.println("ACK")
                        return@launch
                    }

                    if (type == "REACTION") {
                        val senderName = json.optString("sender", "Peer")
                        val timestamp = json.optLong("timestamp", 0L)
                        val reaction = json.optString("reaction", "")
                        _reactionEvents.emit(ReactionEvent(peerIp, timestamp, reaction, senderName))
                        val writer = PrintWriter(socket.getOutputStream(), true)
                        writer.println("ACK")
                        return@launch
                    }

                    val senderName = json.optString("sender", "Peer")
                    val text = json.optString("text", "")
                    val timestamp = json.optLong("timestamp", System.currentTimeMillis())
                    val attType = json.optString("attachmentType", "NONE")
                    val attData = json.optString("attachmentData", "")

                    _totalBytesReceived.value += line.length.toLong()
                    _totalPackets.value += 1L

                    // Notify incoming message
                    _incomingMessages.emit(IncomingMessageEvent(peerIp, senderName, text, timestamp, attType, attData))

                    // Acknowledge back with DELIVERED status confirmation
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    writer.println("ACK")
                }
            } catch (e: Exception) {
                Log.e("P2PNetwork", "Error handling client connection from $peerIp", e)
            } finally {
                try {
                    socket.close()
                } catch (ignored: Exception) {}
            }
        }
    }

    suspend fun sendMessage(
        targetIp: String,
        text: String,
        myName: String,
        attachmentType: String = "NONE",
        attachmentData: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        var attempts = 0
        val maxAttempts = 3
        while (attempts < maxAttempts) {
            attempts++
            val socket = Socket()
            try {
                socket.connect(java.net.InetSocketAddress(targetIp, PORT), 2500)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                val json = JSONObject().apply {
                    put("type", "MSG")
                    put("sender", myName)
                    put("text", text)
                    put("timestamp", System.currentTimeMillis())
                    put("attachmentType", attachmentType)
                    put("attachmentData", attachmentData)
                }

                val payload = json.toString()
                writer.println(payload)
                val response = reader.readLine()

                if (response == "ACK") {
                    _totalBytesSent.value += payload.length.toLong()
                    _totalPackets.value += 1L
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w("P2PNetwork", "Attempt $attempts failed for $targetIp, retrying...", e)
                if (attempts >= maxAttempts) {
                    P2PExceptionHandler.recordSocketFailure(
                        throwable = e,
                        targetIp = targetIp,
                        port = PORT,
                        action = "SEND_MESSAGE",
                        payloadSize = text.length + attachmentData.length
                    )
                }
                if (attempts < maxAttempts) {
                    kotlinx.coroutines.delay(300)
                }
            } finally {
                try { socket.close() } catch (ignored: Exception) {}
            }
        }
        false
    }

    suspend fun sendReadReceipt(targetIp: String, myName: String): Boolean = withContext(Dispatchers.IO) {
        if (targetIp == "GROUP" || targetIp == "127.0.0.2") return@withContext true
        val socket = Socket()
        try {
            socket.connect(java.net.InetSocketAddress(targetIp, PORT), 2000)
            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val json = JSONObject().apply {
                put("type", "READ_RECEIPT")
                put("sender", myName)
                put("timestamp", System.currentTimeMillis())
            }
            writer.println(json.toString())
            reader.readLine() // consume ACK so server coroutine finishes
            true
        } catch (e: Exception) {
            false
        } finally {
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    suspend fun sendReaction(
        targetIp: String,
        myName: String,
        msgTimestamp: Long,
        reactionEmoji: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (targetIp == "GROUP" || targetIp == "127.0.0.2") return@withContext true
        val socket = Socket()
        try {
            socket.connect(java.net.InetSocketAddress(targetIp, PORT), 2000)
            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val json = JSONObject().apply {
                put("type", "REACTION")
                put("sender", myName)
                put("timestamp", msgTimestamp)
                put("reaction", reactionEmoji)
            }
            writer.println(json.toString())
            reader.readLine() // consume ACK so server coroutine finishes
            true
        } catch (e: Exception) {
            false
        } finally {
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    suspend fun pingPeer(targetIp: String): Long = withContext(Dispatchers.IO) {
        var attempts = 0
        while (attempts < 2) {
            attempts++
            val startTime = System.currentTimeMillis()
            try {
                val socket = Socket()
                socket.connect(java.net.InetSocketAddress(targetIp, PORT), 1500)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                val json = JSONObject().apply {
                    put("type", "PING")
                    put("timestamp", startTime)
                }
                writer.println(json.toString())
                val response = reader.readLine()
                socket.close()
                if (response == "ACK") {
                    return@withContext System.currentTimeMillis() - startTime
                }
            } catch (e: Exception) {
                if (attempts < 2) kotlinx.coroutines.delay(200)
            }
        }
        -1L
    }

    private fun startUdpDiscovery() {
        if (isUdpRunning) return
        scope.launch(Dispatchers.IO) {
            try {
                udpSocket = DatagramSocket(UDP_PORT, InetAddress.getByName("0.0.0.0")).apply {
                    broadcast = true
                }
                isUdpRunning = true
                val buffer = ByteArray(2048)
                while (isUdpRunning && udpSocket?.isClosed == false) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        udpSocket?.receive(packet)
                        val message = String(packet.data, 0, packet.length).trim()
                        val senderIp = packet.address.hostAddress ?: continue
                        if (senderIp != _localIp.value) {
                            if (message.startsWith("P2P_BEACON:")) {
                                val content = message.removePrefix("P2P_BEACON:")
                                val parts = content.split(":")
                                val peerName = parts.getOrNull(0) ?: "Peer"
                                val state = parts.getOrNull(1) ?: "ONLINE"
                                val battLevel = parts.getOrNull(2)?.toIntOrNull() ?: 100

                                val currentPeers = _discoveredPeers.value.toMutableMap()
                                currentPeers[senderIp] = peerName
                                _discoveredPeers.value = currentPeers

                                val presenceMap = _peerPresence.value.toMutableMap()
                                presenceMap[senderIp] = PeerPresenceInfo(
                                    ip = senderIp,
                                    name = peerName,
                                    presenceState = state,
                                    lastSeen = System.currentTimeMillis(),
                                    batteryLevel = battLevel
                                )
                                _peerPresence.value = presenceMap
                            } else if (message.startsWith("P2P_TYPING:")) {
                                val content = message.removePrefix("P2P_TYPING:")
                                val parts = content.split(":")
                                val peerName = parts.getOrNull(0) ?: "Peer"
                                val isTyping = parts.getOrNull(1)?.toBoolean() ?: false

                                val presenceMap = _peerPresence.value.toMutableMap()
                                val current = presenceMap[senderIp] ?: PeerPresenceInfo(ip = senderIp, name = peerName)
                                presenceMap[senderIp] = current.copy(
                                    presenceState = if (isTyping) "TYPING" else "ONLINE",
                                    lastSeen = System.currentTimeMillis()
                                )
                                _peerPresence.value = presenceMap
                            }
                        }
                    } catch (e: Exception) {
                        if (!isUdpRunning) break
                    }
                }
            } catch (e: Exception) {
                Log.e("P2PNetwork", "UDP Discovery error", e)
                P2PExceptionHandler.recordUdpDiscoveryFailure(e, UDP_PORT, "LISTENER_LOOP")
                isUdpRunning = false
            }
        }
    }

    private var beaconThrottleCount = 0

    fun broadcastPresence(myName: String, state: String = "ONLINE") {
        startBleFallbackDiscovery(myName)
        if (_isBatteryThrottled.value) {
            beaconThrottleCount++
            // In battery throttled mode, only send beacon every 3rd attempt to reduce socket activity
            if (beaconThrottleCount % 3 != 0) {
                return
            }
        }
        scope.launch(Dispatchers.IO) {
            try {
                val socket = DatagramSocket().apply { broadcast = true }
                val message = "P2P_BEACON:$myName:$state:${_batteryLevel.value}"
                val buffer = message.toByteArray()
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(buffer, buffer.size, broadcastAddr, UDP_PORT)
                socket.send(packet)
                socket.close()
            } catch (e: Exception) {
                Log.e("P2PNetwork", "Broadcast error", e)
            }
        }
    }

    fun broadcastTyping(myName: String, isTyping: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                val socket = DatagramSocket().apply { broadcast = true }
                val message = "P2P_TYPING:$myName:$isTyping"
                val buffer = message.toByteArray()
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(buffer, buffer.size, broadcastAddr, UDP_PORT)
                socket.send(packet)
                socket.close()
            } catch (e: Exception) {
                Log.e("P2PNetwork", "Typing broadcast error", e)
            }
        }
    }

    fun stop() {
        isServerRunning = false
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}

        isUdpRunning = false
        try {
            udpSocket?.close()
        } catch (ignored: Exception) {}
    }
}
