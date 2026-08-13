package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE peerAddress = :peerAddress ORDER BY timestamp ASC")
    fun getMessagesForPeer(peerAddress: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE text LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMessages(query: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("UPDATE chat_messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: Long, status: String)

    @Query("UPDATE chat_messages SET status = :status WHERE peerAddress = :peerAddress AND timestamp = :timestamp")
    suspend fun updateMessageStatusByTimestamp(peerAddress: String, timestamp: Long, status: String)

    @Query("UPDATE chat_messages SET status = 'READ' WHERE peerAddress = :peerAddress AND isOutgoing = 0 AND status != 'READ'")
    suspend fun markIncomingMessagesAsRead(peerAddress: String)

    @Query("UPDATE chat_messages SET status = 'READ' WHERE peerAddress = :peerAddress AND isOutgoing = 1 AND status != 'READ'")
    suspend fun markOutgoingMessagesAsRead(peerAddress: String)

    @Query("UPDATE chat_peers SET isBlocked = :isBlocked WHERE address = :address")
    suspend fun updatePeerBlockedStatus(address: String, isBlocked: Boolean)

    @Query("UPDATE chat_messages SET reactions = :reactions WHERE id = :id")
    suspend fun updateMessageReactions(id: Long, reactions: String)

    @Query("UPDATE chat_messages SET reactions = :reactions WHERE peerAddress = :peerAddress AND timestamp = :timestamp")
    suspend fun updateMessageReactionsByTimestamp(peerAddress: String, timestamp: Long, reactions: String)

    @Query("SELECT * FROM chat_messages WHERE peerAddress = :peerAddress AND timestamp = :timestamp LIMIT 1")
    suspend fun getMessageByTimestamp(peerAddress: String, timestamp: Long): ChatMessage?

    @Query("SELECT * FROM chat_messages WHERE status = 'SCHEDULED' AND scheduledTime <= :nowTime")
    suspend fun getPendingScheduledMessages(nowTime: Long): List<ChatMessage>

    @Query("UPDATE chat_messages SET status = :status, timestamp = :newTimestamp WHERE id = :id")
    suspend fun updateMessageStatusAndTimestamp(id: Long, status: String, newTimestamp: Long)

    @Query("SELECT * FROM chat_messages WHERE status = 'SCHEDULED' ORDER BY scheduledTime ASC")
    fun getScheduledMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_peers WHERE isArchived = 0 ORDER BY lastSeen DESC")
    fun getActivePeers(): Flow<List<ChatPeer>>

    @Query("SELECT * FROM chat_peers WHERE isArchived = 1 ORDER BY lastSeen DESC")
    fun getArchivedPeers(): Flow<List<ChatPeer>>

    @Query("UPDATE chat_peers SET isArchived = :isArchived WHERE address = :address")
    suspend fun updatePeerArchivedStatus(address: String, isArchived: Boolean)

    @Query("DELETE FROM chat_messages")
    suspend fun clearMessages()

    @Query("SELECT * FROM chat_peers WHERE address = :address")
    suspend fun getPeer(address: String): ChatPeer?

    @Query("SELECT * FROM chat_peers ORDER BY lastSeen DESC")
    fun getAllPeers(): Flow<List<ChatPeer>>

    @Query("SELECT * FROM chat_peers")
    suspend fun getAllPeersList(): List<ChatPeer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeer(peer: ChatPeer)

    @Query("DELETE FROM chat_peers WHERE address = :address")
    suspend fun deletePeer(address: String)
}
