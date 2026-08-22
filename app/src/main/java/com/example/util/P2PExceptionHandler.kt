package com.example.util

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

data class P2PDiagnosticErrorEvent(
    val id: String,
    val timestamp: Long,
    val formattedTime: String,
    val category: String, // SOCKET, SYNC, UDP, CRYPTO, GENERAL
    val message: String,
    val exceptionClass: String,
    val contextDetails: Map<String, String>,
    val isFatal: Boolean = false
)

data class AppHealthSummary(
    val healthScore: Int = 98,
    val totalSessions: Int = 1,
    val sessionStartTime: Long = System.currentTimeMillis(),
    val totalSocketFailures: Int = 0,
    val totalSyncFailures: Int = 0,
    val totalNonFatalErrors: Int = 0,
    val totalMessagesSent: Int = 0,
    val totalFilesShared: Int = 0,
    val totalDiscoveriesInitiated: Int = 0
)

/**
 * Centralized exception handler and health diagnostics collector.
 * Captures and logs non-fatal events to Firebase Crashlytics whenever
 * a P2P socket connection fails or a background sync job encounters an issue.
 */
object P2PExceptionHandler {
    private const val TAG = "P2PExceptionHandler"
    
    private var appContext: Context? = null
    private var firebaseService: FirebaseService? = null
    private val crashlytics: FirebaseCrashlytics? by lazy {
        try {
            FirebaseCrashlytics.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Crashlytics not available in current environment", e)
            null
        }
    }

    private val sessionCount = AtomicInteger(1)
    private val socketErrorCount = AtomicInteger(0)
    private val syncErrorCount = AtomicInteger(0)
    private val totalErrorCount = AtomicInteger(0)
    private val sessionStartTime = System.currentTimeMillis()

    private val _recentErrors = MutableStateFlow<List<P2PDiagnosticErrorEvent>>(emptyList())
    val recentErrors: StateFlow<List<P2PDiagnosticErrorEvent>> = _recentErrors.asStateFlow()

    private val _healthSummary = MutableStateFlow(AppHealthSummary())
    val healthSummary: StateFlow<AppHealthSummary> = _healthSummary.asStateFlow()

    fun init(context: Context, service: FirebaseService? = null) {
        appContext = context.applicationContext
        firebaseService = service
        
        // Read or increment session count in shared preferences
        try {
            val prefs = context.getSharedPreferences("app_health_metrics", Context.MODE_PRIVATE)
            val current = prefs.getInt("session_count", 0) + 1
            prefs.edit().putInt("session_count", current).apply()
            sessionCount.set(current)
            updateHealthSummary()
        } catch (e: Exception) {
            Log.w(TAG, "Error updating session count", e)
        }

        installUncaughtExceptionHandler()
    }

    private fun installUncaughtExceptionHandler() {
        try {
            val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    recordUncaughtException(thread, throwable)
                } catch (ignored: Exception) {}
                originalHandler?.uncaughtException(thread, throwable)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed installing custom uncaught exception handler", e)
        }
    }

    /**
     * Logs a non-fatal event to Firebase Crashlytics when a P2P socket connection fails
     */
    fun recordSocketFailure(
        throwable: Throwable,
        targetIp: String,
        port: Int = 8888,
        action: String = "TCP_CONNECT",
        payloadSize: Int = 0
    ) {
        val total = socketErrorCount.incrementAndGet()
        totalErrorCount.incrementAndGet()

        val details = mapOf(
            "target_ip" to targetIp,
            "target_port" to port.toString(),
            "p2p_action" to action,
            "payload_size_bytes" to payloadSize.toString(),
            "total_socket_errors" to total.toString()
        )

        try {
            crashlytics?.apply {
                setCustomKey("failure_category", "P2P_SOCKET")
                setCustomKey("p2p_target_ip", targetIp)
                setCustomKey("p2p_port", port)
                setCustomKey("p2p_action", action)
                setCustomKey("payload_size", payloadSize)
                setCustomKey("is_non_fatal", true)
                log("P2P Socket failure during [$action] to $targetIp:$port -> ${throwable.message}")
                recordException(throwable)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Crashlytics socket failure logging warning", e)
        }

        firebaseService?.logEvent("p2p_socket_failed", mapOf(
            "target_ip_hash" to targetIp.hashCode().toString(),
            "action" to action,
            "error_class" to throwable.javaClass.simpleName
        ))

        addDiagnosticEvent(
            category = "SOCKET",
            message = "Socket [$action] to $targetIp failed: ${throwable.message ?: "Connection refused / timeout"}",
            throwable = throwable,
            details = details
        )
        updateHealthSummary()
    }

    /**
     * Logs a non-fatal event to Firebase Crashlytics when a WorkManager background sync encounters an issue
     */
    fun recordBackgroundSyncFailure(
        throwable: Throwable,
        workerTag: String = "P2PBackgroundSyncWorker",
        syncPhase: String = "EXECUTION",
        isPowerSaver: Boolean = false
    ) {
        val total = syncErrorCount.incrementAndGet()
        totalErrorCount.incrementAndGet()

        val details = mapOf(
            "worker_tag" to workerTag,
            "sync_phase" to syncPhase,
            "is_power_saver" to isPowerSaver.toString(),
            "total_sync_errors" to total.toString()
        )

        try {
            crashlytics?.apply {
                setCustomKey("failure_category", "BACKGROUND_SYNC")
                setCustomKey("worker_tag", workerTag)
                setCustomKey("sync_phase", syncPhase)
                setCustomKey("is_power_saver", isPowerSaver)
                setCustomKey("is_non_fatal", true)
                log("WorkManager background sync failed in [$syncPhase] -> ${throwable.message}")
                recordException(throwable)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Crashlytics background sync logging warning", e)
        }

        firebaseService?.logEvent("background_sync_failed", mapOf(
            "worker_tag" to workerTag,
            "sync_phase" to syncPhase,
            "error_class" to throwable.javaClass.simpleName
        ))

        addDiagnosticEvent(
            category = "SYNC",
            message = "Background sync [$syncPhase] encountered issue: ${throwable.message ?: "Sync interrupted"}",
            throwable = throwable,
            details = details
        )
        updateHealthSummary()
    }

    /**
     * Logs non-fatal UDP discovery or broadcast errors
     */
    fun recordUdpDiscoveryFailure(throwable: Throwable, port: Int = 8889, reason: String = "BEACON_BROADCAST") {
        totalErrorCount.incrementAndGet()
        val details = mapOf(
            "udp_port" to port.toString(),
            "discovery_reason" to reason
        )

        try {
            crashlytics?.apply {
                setCustomKey("failure_category", "UDP_DISCOVERY")
                setCustomKey("udp_port", port)
                setCustomKey("discovery_reason", reason)
                log("UDP Discovery warning in [$reason] on port $port -> ${throwable.message}")
                recordException(throwable)
            }
        } catch (ignored: Exception) {}

        addDiagnosticEvent(
            category = "UDP",
            message = "UDP Discovery ($reason) failed on port $port: ${throwable.message ?: "Socket error"}",
            throwable = throwable,
            details = details
        )
        updateHealthSummary()
    }

    /**
     * Logs general non-fatal events with custom attributes
     */
    fun recordGeneralException(throwable: Throwable, contextTag: String, attributes: Map<String, String> = emptyMap()) {
        totalErrorCount.incrementAndGet()
        try {
            crashlytics?.apply {
                setCustomKey("failure_category", contextTag)
                attributes.forEach { (k, v) -> setCustomKey(k, v) }
                log("Non-fatal exception in [$contextTag] -> ${throwable.message}")
                recordException(throwable)
            }
        } catch (ignored: Exception) {}

        addDiagnosticEvent(
            category = contextTag,
            message = "${throwable.javaClass.simpleName} in $contextTag: ${throwable.message ?: "Unknown error"}",
            throwable = throwable,
            details = attributes
        )
        updateHealthSummary()
    }

    private fun recordUncaughtException(thread: Thread, throwable: Throwable) {
        try {
            crashlytics?.apply {
                setCustomKey("crash_thread", thread.name)
                setCustomKey("is_fatal", true)
                log("CRITICAL: Uncaught exception in thread [${thread.name}]: ${throwable.message}")
                recordException(throwable)
            }
        } catch (ignored: Exception) {}
    }

    private fun addDiagnosticEvent(
        category: String,
        message: String,
        throwable: Throwable,
        details: Map<String, String>,
        isFatal: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val formattedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))
        val event = P2PDiagnosticErrorEvent(
            id = "err_${now}_${(100..999).random()}",
            timestamp = now,
            formattedTime = formattedTime,
            category = category,
            message = message,
            exceptionClass = throwable.javaClass.name,
            contextDetails = details,
            isFatal = isFatal
        )

        val currentList = _recentErrors.value.toMutableList()
        currentList.add(0, event)
        if (currentList.size > 50) {
            currentList.removeAt(currentList.size - 1)
        }
        _recentErrors.value = currentList
    }

    private fun updateHealthSummary() {
        val totalErrors = totalErrorCount.get()
        // Calculate dynamic health score from 100 down based on error frequency
        val score = (100 - (totalErrors * 2)).coerceIn(65, 100)

        _healthSummary.value = AppHealthSummary(
            healthScore = score,
            totalSessions = sessionCount.get(),
            sessionStartTime = sessionStartTime,
            totalSocketFailures = socketErrorCount.get(),
            totalSyncFailures = syncErrorCount.get(),
            totalNonFatalErrors = totalErrors
        )
    }

    fun triggerTestSocketError(targetIp: String = "192.168.1.254") {
        val testEx = java.net.ConnectException("Diagnostic Test: Connection refused by target peer $targetIp:8888")
        recordSocketFailure(testEx, targetIp = targetIp, action = "DIAGNOSTIC_SIMULATION", payloadSize = 128)
    }

    fun triggerTestBackgroundSyncError() {
        val testEx = java.io.IOException("Diagnostic Test: Network timeout during background scheduled messages sync")
        recordBackgroundSyncFailure(testEx, syncPhase = "SIMULATED_DISPATCH", isPowerSaver = true)
    }

    fun clearDiagnosticLogs() {
        _recentErrors.value = emptyList()
        socketErrorCount.set(0)
        syncErrorCount.set(0)
        totalErrorCount.set(0)
        updateHealthSummary()
    }
}
