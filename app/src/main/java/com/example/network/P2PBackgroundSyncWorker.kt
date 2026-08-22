package com.example.network

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.UserPreferencesRepository
import com.example.util.P2PExceptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * WorkManager background sync worker that keeps P2P discovery alive,
 * executes pending scheduled messages, and performs automated maintenance
 * while optimizing battery consumption.
 */
class P2PBackgroundSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "P2PBackgroundSyncWorker"
        const val PERIODIC_WORK_NAME = "p2p_periodic_background_sync"
        const val ONE_TIME_WORK_NAME = "p2p_onetime_background_sync"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "P2PBackgroundSyncWorker executing background maintenance and sync cycle...")
        try {
            val userPrefs = UserPreferencesRepository(appContext)
            val isSyncEnabled = userPrefs.backgroundSyncEnabledFlow.first()
            if (!isSyncEnabled) {
                Log.i(TAG, "Background sync disabled by user preference. Skipping.")
                return Result.success()
            }

            val database = AppDatabase.getDatabase(appContext)
            val chatDao = database.chatDao()

            // 1. Broadcast lightweight background presence UDP pulse
            val nickname = userPrefs.myNicknameFlow.first() ?: "User"
            broadcastBackgroundPresencePulse(nickname)

            // 2. Process pending scheduled messages
            val now = System.currentTimeMillis()
            val pendingScheduled = chatDao.getPendingScheduledMessages(now)
            if (pendingScheduled.isNotEmpty()) {
                Log.i(TAG, "Background sync: Processing ${pendingScheduled.size} pending scheduled messages")
                val activePeers = chatDao.getAllPeersList().filter { !it.isBlocked }
                val p2pManager = P2PNetworkManager(appContext)
                
                for (msg in pendingScheduled) {
                    if (msg.peerAddress == "127.0.0.2") {
                        chatDao.updateMessageStatusAndTimestamp(msg.id, "DELIVERED", System.currentTimeMillis())
                    } else if (msg.peerAddress == "GROUP") {
                        var anySent = false
                        for (peer in activePeers) {
                            if (peer.address != "GROUP" && peer.address != "127.0.0.2") {
                                val ok = p2pManager.sendMessage(peer.address, msg.text, nickname, msg.attachmentType, msg.attachmentData)
                                if (ok) anySent = true
                            }
                        }
                        chatDao.updateMessageStatusAndTimestamp(msg.id, if (anySent) "DELIVERED" else "SENT", System.currentTimeMillis())
                    } else {
                        val sent = p2pManager.sendMessage(msg.peerAddress, msg.text, nickname, msg.attachmentType, msg.attachmentData)
                        if (sent) {
                            chatDao.updateMessageStatusAndTimestamp(msg.id, "DELIVERED", System.currentTimeMillis())
                        }
                    }
                }
                p2pManager.stop()
            }

            // 3. Automated auto-purge retention maintenance
            val purgeSetting = userPrefs.autoPurgeDurationFlow.first()
            if (purgeSetting != "OFF") {
                val durationMillis = when (purgeSetting) {
                    "24_HOURS" -> 24L * 60 * 60 * 1000
                    "7_DAYS" -> 7L * 24 * 60 * 60 * 1000
                    "30_DAYS" -> 30L * 24 * 60 * 60 * 1000
                    "90_DAYS" -> 90L * 24 * 60 * 60 * 1000
                    else -> 0L
                }
                if (durationMillis > 0L) {
                    val cutoff = System.currentTimeMillis() - durationMillis
                    val purgedCount = chatDao.purgeMessagesOlderThan(cutoff)
                    if (purgedCount > 0) {
                        Log.i(TAG, "Background sync purged $purgedCount old messages based on policy $purgeSetting")
                    }
                }
            }

            // 4. Automated auto-archive for stale peers
            val isAutoArchive = userPrefs.autoArchiveFlow.first()
            if (isAutoArchive) {
                val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                val peers = chatDao.getAllPeersList()
                for (peer in peers) {
                    if (peer.lastSeen < thirtyDaysAgo && !peer.isArchived) {
                        chatDao.updatePeerArchivedStatus(peer.address, true)
                    }
                }
            }

            // 5. Update timestamp of last successful background sync
            val timestampStr = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date())
            userPrefs.updateLastBackgroundSync(timestampStr)

            Log.i(TAG, "P2PBackgroundSyncWorker completed successfully at $timestampStr")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in P2PBackgroundSyncWorker", e)
            P2PExceptionHandler.recordBackgroundSyncFailure(
                throwable = e,
                workerTag = TAG,
                syncPhase = "WORKER_CYCLE"
            )
            return Result.retry()
        }
    }

    private fun broadcastBackgroundPresencePulse(myName: String) {
        try {
            val socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = 1500
            val json = org.json.JSONObject().apply {
                put("type", "PRESENCE")
                put("senderName", myName)
                put("presenceState", "ONLINE")
                put("isBackgroundSync", true)
                put("timestamp", System.currentTimeMillis())
            }.toString()

            val bytes = json.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(
                bytes,
                bytes.size,
                InetAddress.getByName("255.255.255.255"),
                8889
            )
            socket.send(packet)
            socket.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed sending background presence UDP packet: ${e.message}")
        }
    }
}

/**
 * Helper object to configure, schedule, and query WorkManager for P2P background persistence.
 */
object P2PBackgroundSyncScheduler {

    fun schedulePeriodicSync(context: Context, powerSaver: Boolean = false) {
        val constraintsBuilder = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)

        if (powerSaver) {
            constraintsBuilder.setRequiresBatteryNotLow(true)
        }

        val periodicRequest = PeriodicWorkRequestBuilder<P2PBackgroundSyncWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraintsBuilder.build())
            .addTag(P2PBackgroundSyncWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            P2PBackgroundSyncWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    fun triggerOneTimeSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeRequest = OneTimeWorkRequestBuilder<P2PBackgroundSyncWorker>()
            .setConstraints(constraints)
            .addTag(P2PBackgroundSyncWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            P2PBackgroundSyncWorker.ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )
    }

    fun cancelBackgroundSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(P2PBackgroundSyncWorker.PERIODIC_WORK_NAME)
    }

    fun getWorkInfoFlow(context: Context): Flow<List<WorkInfo>> {
        return WorkManager.getInstance(context).getWorkInfosByTagFlow(P2PBackgroundSyncWorker.TAG)
    }
}
