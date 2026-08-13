package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_peers")
data class ChatPeer(
    @PrimaryKey val address: String,
    val name: String,
    val lastSeen: Long = System.currentTimeMillis(),
    val isConnected: Boolean = false,
    val presenceState: String = "ONLINE", // ONLINE, TYPING, OFFLINE
    val latencyMs: Long = 0L,
    val isBlocked: Boolean = false,
    val isArchived: Boolean = false
)
