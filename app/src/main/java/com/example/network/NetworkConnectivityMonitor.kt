package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

data class NetworkStatus(
    val isConnected: Boolean = false,
    val isWifi: Boolean = false,
    val isCellular: Boolean = false,
    val networkName: String = "Unknown",
    val localIp: String = "127.0.0.1",
    val subnetPrefix: String = "127.0.0"
)

data class NetworkChangeEvent(
    val previousNetwork: String,
    val currentNetwork: String,
    val currentIp: String,
    val isWifi: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Connectivity listener using ConnectivityManager that notifies users and triggers
 * auto-rediscovery when switching Wi-Fi networks or subnets.
 */
class NetworkConnectivityMonitor(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _networkStatus = MutableStateFlow(getCurrentNetworkStatus())
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val _networkChangeEvents = MutableSharedFlow<NetworkChangeEvent>(extraBufferCapacity = 10)
    val networkChangeEvents: SharedFlow<NetworkChangeEvent> = _networkChangeEvents.asSharedFlow()

    private val _wifiSwitchBannerVisible = MutableStateFlow(false)
    val wifiSwitchBannerVisible: StateFlow<Boolean> = _wifiSwitchBannerVisible.asStateFlow()

    private val _lastNetworkMessage = MutableStateFlow<String?>(null)
    val lastNetworkMessage: StateFlow<String?> = _lastNetworkMessage.asStateFlow()

    private var previousNetworkId: String = ""
    private var isFirstCallback = true

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handleNetworkStateChange("AVAILABLE")
        }

        override fun onLost(network: Network) {
            handleNetworkStateChange("LOST")
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            handleNetworkStateChange("CAPABILITIES_CHANGED")
        }
    }

    init {
        previousNetworkId = getNetworkIdentifier()
        register()
    }

    fun register() {
        try {
            val builder = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            connectivityManager.registerNetworkCallback(builder.build(), networkCallback)
        } catch (e: Exception) {
            try {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
            } catch (ignored: Exception) {}
        }
    }

    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (ignored: Exception) {}
    }

    fun dismissWifiSwitchBanner() {
        _wifiSwitchBannerVisible.value = false
    }

    private fun handleNetworkStateChange(reason: String) {
        val currentStatus = getCurrentNetworkStatus()
        _networkStatus.value = currentStatus

        val currentNetworkId = getNetworkIdentifier()
        if (!isFirstCallback && previousNetworkId != currentNetworkId && currentStatus.isConnected) {
            val prev = previousNetworkId.ifEmpty { "Previous Network" }
            val curr = if (currentStatus.networkName.isNotBlank() && currentStatus.networkName != "Unknown") {
                currentStatus.networkName
            } else {
                "Wi-Fi (${currentStatus.localIp})"
            }

            val event = NetworkChangeEvent(
                previousNetwork = prev,
                currentNetwork = curr,
                currentIp = currentStatus.localIp,
                isWifi = currentStatus.isWifi
            )
            
            _lastNetworkMessage.value = "Switched to $curr. P2P discovery requires peers on the same local subnet (${currentStatus.subnetPrefix}.*)."
            _wifiSwitchBannerVisible.value = true
            
            scope.launch {
                _networkChangeEvents.emit(event)
            }
        }

        isFirstCallback = false
        previousNetworkId = currentNetworkId
    }

    private fun getNetworkIdentifier(): String {
        val ip = getActiveLocalIp()
        val wifiName = getWifiSsid()
        return "$wifiName:$ip"
    }

    fun getCurrentNetworkStatus(): NetworkStatus {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isConnected = capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        val ip = getActiveLocalIp()
        val wifiSsid = getWifiSsid()
        val networkName = when {
            isWifi -> if (wifiSsid.isNotBlank() && wifiSsid != "<unknown ssid>") "Wi-Fi: $wifiSsid" else "Local Wi-Fi"
            isCellular -> "Cellular Network"
            isConnected -> "Ethernet / Other"
            else -> "Offline / No Connection"
        }

        val subnetPrefix = if (ip.contains(".")) {
            ip.substringBeforeLast(".")
        } else {
            "127.0.0"
        }

        return NetworkStatus(
            isConnected = isConnected,
            isWifi = isWifi,
            isCellular = isCellular,
            networkName = networkName,
            localIp = ip,
            subnetPrefix = subnetPrefix
        )
    }

    private fun getWifiSsid(): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = wifiManager?.connectionInfo
            val ssid = info?.ssid?.replace("\"", "") ?: ""
            if (ssid.isNotBlank() && ssid != "<unknown ssid>") ssid else ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun getActiveLocalIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress
                        if (hostAddress != null && !hostAddress.startsWith("127.")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}
        return "127.0.0.1"
    }
}
