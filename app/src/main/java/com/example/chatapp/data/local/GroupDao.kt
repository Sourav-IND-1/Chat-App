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
}
