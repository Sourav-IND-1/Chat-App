package com.example.chatapp.data.repository

import android.content.Context
import android.util.Log
import com.example.chatapp.data.local.GroupDao
import com.example.chatapp.data.local.GroupEntity
import com.example.chatapp.data.local.GroupMessageEntity
import com.example.chatapp.domain.model.Group
import com.example.chatapp.domain.model.GroupMessage
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class GroupRepository(
    private val groupDao: GroupDao,
    private val currentUserId: String,
    private val context: Context? = null
) {
    private val rtdb = RtdbHelper.ref
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // --- Group Operations ---

    suspend fun createGroup(name: String, description: String, profilePhotoUrl: String? = null): String? {
        val groupId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        val members = listOf(currentUserId)

        val groupMap = mapOf(
            "groupId" to groupId,
            "name" to name,
            "description" to description,
            "profilePhotoUrl" to profilePhotoUrl,
            "adminId" to currentUserId,
            "members" to members,
            "createdAt" to createdAt
        )

        return try {
            rtdb.child("groups").child(groupId).setValue(groupMap).await()
            val entity = GroupEntity(
                groupId = groupId,
                name = name,
                description = description,
                profilePhotoUrl = profilePhotoUrl,
                adminId = currentUserId,
                members = members,
                createdAt = createdAt
            )
            withContext(Dispatchers.IO) {
                groupDao.insertGroup(entity)
            }
            groupId
        } catch (e: Exception) {
            _errorMessage.value = "Failed to create group: ${e.message}"
            Log.e("GroupRepository", "createGroup error", e)
            null
        }
    }

    suspend fun joinGroup(groupId: String): Boolean {
        return try {
            val groupSnapshot = rtdb.child("groups").child(groupId).get().await()
            if (!groupSnapshot.exists()) {
                _errorMessage.value = "Group does not exist."
                return false
            }

            val currentMembers = groupSnapshot.child("members").children.mapNotNull { it.getValue(String::class.java) }
            if (currentMembers.contains(currentUserId)) {
                return true // Already a member
            }

            val newMembers = currentMembers + currentUserId
            rtdb.child("groups").child(groupId).child("members").setValue(newMembers).await()
            
            // Fetch the updated group to save locally
            fetchAndCacheGroupInfo(groupId)
            true
        } catch (e: Exception) {
            _errorMessage.value = "Failed to join group: ${e.message}"
            Log.e("GroupRepository", "joinGroup error", e)
            false
        }
    }

    private suspend fun fetchAndCacheGroupInfo(groupId: String) {
        try {
            val snapshot = rtdb.child("groups").child(groupId).get().await()
            if (snapshot.exists()) {
                val groupEntity = GroupEntity(
                    groupId = snapshot.child("groupId").getValue(String::class.java) ?: groupId,
                    name = snapshot.child("name").getValue(String::class.java) ?: "Unknown Group",
                    description = snapshot.child("description").getValue(String::class.java) ?: "",
                    profilePhotoUrl = snapshot.child("profilePhotoUrl").getValue(String::class.java),
                    adminId = snapshot.child("adminId").getValue(String::class.java) ?: "",
                    members = snapshot.child("members").children.mapNotNull { it.getValue(String::class.java) },
                    createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    groupDao.insertGroup(groupEntity)
                }
            }
        } catch (e: Exception) {
            Log.e("GroupRepository", "Failed to cache group $groupId", e)
        }
    }
    
    // Listen to changes in the user's groups
    fun startListeningToUserGroups() {
        // A robust way mapping would be a users_groups node, but let's query the groups node 
        // Or fetch all groups where members is not empty. RTDB querying on arrays is limited,
        // so we can listen to the whole `groups` node and filter locally, or preferably,
        // maintain a `user_groups/{userId}` node. 
        // For simplicity right now, we will query all groups on RTDB to sync ones we belong to.
        rtdb.child("groups").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                CoroutineScope(Dispatchers.IO).launch {
                    val myGroups = mutableListOf<GroupEntity>()
                    for (groupSnapshot in snapshot.children) {
                        val members = groupSnapshot.child("members").children.mapNotNull { it.getValue(String::class.java) }
                        if (members.contains(currentUserId)) {
                            val groupEntity = GroupEntity(
                                groupId = groupSnapshot.child("groupId").getValue(String::class.java) ?: continue,
                                name = groupSnapshot.child("name").getValue(String::class.java) ?: "Unknown Group",
                                description = groupSnapshot.child("description").getValue(String::class.java) ?: "",
                                profilePhotoUrl = groupSnapshot.child("profilePhotoUrl").getValue(String::class.java),
                                adminId = groupSnapshot.child("adminId").getValue(String::class.java) ?: "",
                                members = members,
                                createdAt = groupSnapshot.child("createdAt").getValue(Long::class.java) ?: 0L
                            )
                            myGroups.add(groupEntity)
                        }
                    }
                    if (myGroups.isNotEmpty()) {
                        groupDao.insertGroups(myGroups)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("GroupRepository", "Failed to listen to groups", error.toException())
            }
        })
    }

    fun getMyGroups(): Flow<List<Group>> = groupDao.getAllGroups().map { entities ->
        entities.map { it.toDomainModel() }
    }

    // --- Message Operations ---

    suspend fun sendGroupMessage(groupId: String, senderName: String, content: String) {
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        
        val msgMap = mapOf(
            "messageId" to messageId,
            "groupId" to groupId,
            "senderId" to currentUserId,
            "senderName" to senderName,
            "content" to content,
            "timestamp" to timestamp,
            "isPoll" to false
        )

        try {
            rtdb.child("group_messages").child(groupId).child(messageId).setValue(msgMap).await()
            val entity = GroupMessageEntity(
                messageId = messageId,
                groupId = groupId,
                senderId = currentUserId,
                senderName = senderName,
                content = content,
                timestamp = timestamp,
                isSentByMe = true,
                isPoll = false
            )
            withContext(Dispatchers.IO) {
                groupDao.insertGroupMessage(entity)
            }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to send message: ${e.message}"
            Log.e("GroupRepository", "sendGroupMessage error", e)
        }
    }

    // --- Poll Operations ---

    suspend fun createPoll(groupId: String, senderName: String, question: String, options: List<String>) {
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val pollId = UUID.randomUUID().toString()
        
        val pollMap = mutableMapOf<String, Any>(
            "messageId" to messageId,
            "groupId" to groupId,
            "senderId" to currentUserId,
            "senderName" to senderName,
            "content" to "Created a poll: $question",
            "timestamp" to timestamp,
            "isPoll" to true,
            "pollId" to pollId,
            "pollQuestion" to question
        )
        
        val optionsMap = options.associateWith { 0 }
        pollMap["pollOptions"] = optionsMap // Initial votes are 0

        try {
            rtdb.child("group_messages").child(groupId).child(messageId).setValue(pollMap).await()
            // We'll let the group message listener handle the local DB sync for polls so that 
            // parsing logic is uniform.
        } catch (e: Exception) {
            _errorMessage.value = "Failed to create poll: ${e.message}"
            Log.e("GroupRepository", "createPoll error", e)
        }
    }

    suspend fun voteOnPoll(groupId: String, messageId: String, optionText: String) {
        try {
            // Using a simple transaction or updating directly.
            // For simplicity, we just add the user to the voters list for the option under the poll.
            val pollRef = rtdb.child("group_messages").child(groupId).child(messageId)
            
            // To ensure 1 vote per person, we map userVoters -> optionText
            pollRef.child("voters").child(currentUserId).setValue(optionText).await()
        } catch (e: Exception) {
            _errorMessage.value = "Failed to cast vote: ${e.message}"
            Log.e("GroupRepository", "voteOnPoll error", e)
        }
    }

    // Listeners for group messages
    private val groupListeners = mutableMapOf<String, ValueEventListener>()

    fun startListeningToGroupMessages(groupId: String) {
        if (groupListeners.containsKey(groupId)) return
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                CoroutineScope(Dispatchers.IO).launch {
                    for (msgSnapshot in snapshot.children) {
                        val messageId = msgSnapshot.child("messageId").getValue(String::class.java) ?: continue
                        val senderId = msgSnapshot.child("senderId").getValue(String::class.java) ?: ""
                        
                        val isPoll = msgSnapshot.child("isPoll").getValue(Boolean::class.java) ?: false
                        var pollOptionsJson: String? = null
                        var userVotedOption: String? = null
                        
                        if (isPoll) {
                            val optionsMap = mutableMapOf<String, Int>()
                            // initialize options with 0
                            msgSnapshot.child("pollOptions").children.forEach { optNode ->
                                val text = optNode.key ?: return@forEach
                                optionsMap[text] = 0
                            }
                            
                            // tally votes
                            msgSnapshot.child("voters").children.forEach { voterNode ->
                                val voterId = voterNode.key ?: return@forEach
                                val votedOpt = voterNode.getValue(String::class.java) ?: return@forEach
                                
                                optionsMap[votedOpt] = (optionsMap[votedOpt] ?: 0) + 1
                                if (voterId == currentUserId) {
                                    userVotedOption = votedOpt
                                }
                            }
                            pollOptionsJson = JSONObject(optionsMap as Map<*, *>).toString()
                        }

                        val entity = GroupMessageEntity(
                            messageId = messageId,
                            groupId = groupId,
                            senderId = senderId,
                            senderName = msgSnapshot.child("senderName").getValue(String::class.java) ?: "User",
                            content = msgSnapshot.child("content").getValue(String::class.java) ?: "",
                            timestamp = msgSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L,
                            isSentByMe = senderId == currentUserId,
                            isPoll = isPoll,
                            pollId = msgSnapshot.child("pollId").getValue(String::class.java),
                            pollQuestion = msgSnapshot.child("pollQuestion").getValue(String::class.java),
                            pollOptionsJson = pollOptionsJson,
                            userVotedOption = userVotedOption
                        )
                        groupDao.insertGroupMessage(entity)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        
        rtdb.child("group_messages").child(groupId).addValueEventListener(listener)
        groupListeners[groupId] = listener
    }

    fun stopListeningToGroupMessages(groupId: String) {
        groupListeners.remove(groupId)?.let { listener ->
            rtdb.child("group_messages").child(groupId).removeEventListener(listener)
        }
    }

    fun getMessagesForGroup(groupId: String): Flow<List<GroupMessage>> =
        groupDao.getMessagesForGroup(groupId).map { entities ->
            entities.map { it.toDomainModel() }
        }

    fun clearError() { _errorMessage.value = null }
}

fun GroupEntity.toDomainModel() = Group(
    groupId = groupId, name = name, description = description, profilePhotoUrl = profilePhotoUrl,
    adminId = adminId, members = members, createdAt = createdAt
)

fun GroupMessageEntity.toDomainModel(): GroupMessage {
    val optionsMap = mutableMapOf<String, Int>()
    if (isPoll && !pollOptionsJson.isNullOrEmpty()) {
        try {
            val json = JSONObject(pollOptionsJson)
            json.keys().forEach { key ->
                optionsMap[key] = json.getInt(key)
            }
        } catch (e: Exception) {
            Log.e("GroupMessageEntity", "Failed to parse poll options JSON", e)
        }
    }

    return GroupMessage(
        messageId = messageId, groupId = groupId, senderId = senderId, senderName = senderName,
        content = content, timestamp = timestamp, isSentByMe = isSentByMe,
        mediaUrl = mediaUrl, mediaType = mediaType,
        isPoll = isPoll, pollId = pollId, pollQuestion = pollQuestion, 
        pollOptions = optionsMap, userVotedOption = userVotedOption
    )
}
