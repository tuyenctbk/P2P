package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerAddress: String,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isOutgoing: Boolean,
    val status: String = "SENT", // SENT, DELIVERED, READ, FAILED
    val attachmentType: String = "NONE", // NONE, IMAGE, FILE, VOICE
    val attachmentData: String = "", // file name or duration
    val reactions: String = "", // e.g. "👍,❤️" or JSON string
    val scheduledTime: Long = 0L // Timestamp when scheduled message should be transmitted
)

