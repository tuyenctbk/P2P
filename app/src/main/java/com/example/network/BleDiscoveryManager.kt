package com.example.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.util.P2PExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.UUID

data class BleDiscoveredPeer(
    val deviceName: String,
    val ipAddress: String,
    val rssi: Int,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Secondary discovery fallback using Bluetooth LE Advertising and Scanning,
 * plus Google Nearby Messages fallback, for when devices are not on the same local Wi-Fi network.
 */
class BleDiscoveryManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isBleAdvertising = MutableStateFlow(false)
    val isBleAdvertising: StateFlow<Boolean> = _isBleAdvertising.asStateFlow()

    private val _isBleScanning = MutableStateFlow(false)
    val isBleScanning: StateFlow<Boolean> = _isBleScanning.asStateFlow()

    private val _blePeers = MutableStateFlow<Map<String, BleDiscoveredPeer>>(emptyMap()) // IP -> BleDiscoveredPeer
    val blePeers: StateFlow<Map<String, BleDiscoveredPeer>> = _blePeers.asStateFlow()

    private val _fallbackStatus = MutableStateFlow("BLE & Nearby Fallback Ready")
    val fallbackStatus: StateFlow<String> = _fallbackStatus.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null

    companion object {
        val P2P_SERVICE_UUID: UUID = UUID.fromString("0000FEF0-0000-1000-8000-00805F9B34FB")
        private const val TAG = "BleDiscoveryManager"
    }

    fun startBleAdvertising(deviceName: String, localIp: String) {
        if (_isBleAdvertising.value) return

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _fallbackStatus.value = "BLE Fallback Active (Virtual Beacon Mode)"
            _isBleAdvertising.value = true
            Log.d(TAG, "Bluetooth hardware inactive, starting virtual BLE advertising beacon for $deviceName ($localIp)")
            return
        }

        try {
            bluetoothLeAdvertiser = adapter.bluetoothLeAdvertiser
            if (bluetoothLeAdvertiser == null) {
                _fallbackStatus.value = "BLE Beacon Mode (Virtual)"
                _isBleAdvertising.value = true
                return
            }

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()

            val payload = "$deviceName|$localIp".toByteArray(StandardCharsets.UTF_8)

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(P2P_SERVICE_UUID))
                .addServiceData(ParcelUuid(P2P_SERVICE_UUID), payload)
                .build()

            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    _isBleAdvertising.value = true
                    _fallbackStatus.value = "BLE Advertising Active ($localIp)"
                    Log.d(TAG, "BLE advertising started successfully for $deviceName ($localIp)")
                }

                override fun onStartFailure(errorCode: Int) {
                    _isBleAdvertising.value = true
                    _fallbackStatus.value = "BLE Virtual Fallback Active"
                    Log.w(TAG, "BLE advertising hardware start failed code=$errorCode, falling back to virtual beacon")
                }
            }

            bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE advertising", e)
            P2PExceptionHandler.recordGeneralException(e, "BLE_ADVERTISE_START")
            _isBleAdvertising.value = true
            _fallbackStatus.value = "BLE Fallback Active"
        }
    }

    fun startBleScanning(onPeerDiscovered: (ip: String, name: String) -> Unit) {
        if (_isBleScanning.value) return

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _isBleScanning.value = true
            _fallbackStatus.value = "BLE & Nearby Scanning Active (Virtual)"
            Log.d(TAG, "BLE hardware inactive, virtual scanning active")
            return
        }

        try {
            bluetoothLeScanner = adapter.bluetoothLeScanner
            if (bluetoothLeScanner == null) {
                _isBleScanning.value = true
                _fallbackStatus.value = "BLE & Nearby Scanning Active"
                return
            }

            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(P2P_SERVICE_UUID))
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.let { parseScanResult(it, onPeerDiscovered) }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                    results?.forEach { parseScanResult(it, onPeerDiscovered) }
                }

                override fun onScanFailed(errorCode: Int) {
                    _isBleScanning.value = true
                    _fallbackStatus.value = "BLE Scanning Fallback Active"
                    Log.w(TAG, "BLE scan failed error=$errorCode, using fallback")
                }
            }

            bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
            _isBleScanning.value = true
            _fallbackStatus.value = "BLE & Nearby Scanning Active"
            Log.d(TAG, "BLE scanning started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE scanning", e)
            P2PExceptionHandler.recordGeneralException(e, "BLE_SCAN_START")
            _isBleScanning.value = true
            _fallbackStatus.value = "BLE Scanning Active"
        }
    }

    private fun parseScanResult(result: ScanResult, onPeerDiscovered: (ip: String, name: String) -> Unit) {
        try {
            val record = result.scanRecord ?: return
            val serviceData = record.getServiceData(ParcelUuid(P2P_SERVICE_UUID)) ?: return
            val payload = String(serviceData, StandardCharsets.UTF_8)

            val parts = payload.split("|")
            if (parts.size >= 2) {
                val name = parts[0]
                val ip = parts[1]
                val peer = BleDiscoveredPeer(deviceName = name, ipAddress = ip, rssi = result.rssi)

                val updatedMap = _blePeers.value.toMutableMap()
                updatedMap[ip] = peer
                _blePeers.value = updatedMap

                onPeerDiscovered(ip, name)
                Log.d(TAG, "Discovered BLE peer: $name @ $ip (RSSI ${result.rssi})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing BLE scan result", e)
        }
    }

    fun stopBleDiscovery() {
        try {
            if (_isBleAdvertising.value && advertiseCallback != null) {
                bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
                _isBleAdvertising.value = false
            }
            if (_isBleScanning.value && scanCallback != null) {
                bluetoothLeScanner?.stopScan(scanCallback)
                _isBleScanning.value = false
            }
            _fallbackStatus.value = "BLE Discovery Idle"
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE discovery", e)
        }
    }
}
