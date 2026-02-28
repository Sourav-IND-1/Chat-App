package com.example.chatapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUser(user: UserEntity): Long

    @Query("SELECT * FROM users")
    fun getAllContacts(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    fun getUserById(userId: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE (senderId = :myUserId AND receiverId = :otherUserId) OR (senderId = :otherUserId AND receiverId = :myUserId) ORDER BY timestamp ASC")
    fun getMessagesWithUser(myUserId: String, otherUserId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 1")
    fun getLastMessageFlow(): Flow<MessageEntity?>
}
