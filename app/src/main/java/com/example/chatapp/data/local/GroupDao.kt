package com.example.chatapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGroup(group: GroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGroups(groups: List<GroupEntity>)

    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE groupId = :groupId LIMIT 1")
    fun getGroupById(groupId: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE groupId = :groupId LIMIT 1")
    fun getGroupByIdFlow(groupId: String): Flow<GroupEntity?>

    @Query("DELETE FROM groups WHERE groupId = :groupId")
    fun deleteGroup(groupId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGroupMessage(message: GroupMessageEntity): Long

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun getMessagesForGroup(groupId: String): Flow<List<GroupMessageEntity>>

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY timestamp DESC LIMIT 1")
    fun getLatestMessageForGroup(groupId: String): Flow<GroupMessageEntity?>

    @Query("DELETE FROM group_messages WHERE groupId = :groupId")
    fun clearMessagesForGroup(groupId: String)

    @Query("SELECT * FROM group_messages WHERE messageId = :messageId LIMIT 1")
    fun getGroupMessageById(messageId: String): GroupMessageEntity?

    @Query("DELETE FROM group_messages WHERE messageId = :messageId")
    fun deleteGroupMessage(messageId: String)

    @Query("SELECT COUNT(*) FROM group_messages WHERE groupId = :groupId AND isReadByMe = 0 AND isSentByMe = 0")
    fun getUnreadCountForGroup(groupId: String): Flow<Int>

    @Query("UPDATE group_messages SET isReadByMe = 1, readByCount = :readByCount WHERE messageId = :messageId")
    fun markMessageReadLocally(messageId: String, readByCount: Int)

    // --- Join Requests ---
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertJoinRequest(request: GroupJoinRequestEntity)

    @Query("SELECT * FROM group_join_requests WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun getJoinRequestsForGroup(groupId: String): Flow<List<GroupJoinRequestEntity>>

    @Query("DELETE FROM group_join_requests WHERE requestId = :requestId")
    fun deleteJoinRequest(requestId: String)
}
