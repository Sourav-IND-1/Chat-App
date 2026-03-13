package com.example.chatapp.data.repository

import android.content.Context
import android.util.Log
import com.example.chatapp.data.crypto.KeyManager
import com.example.chatapp.data.local.ChatDao
import com.example.chatapp.data.local.GroupDao
import com.example.chatapp.data.local.GroupEntity
import com.example.chatapp.data.local.GroupMessageEntity
import com.example.chatapp.domain.model.Group
import com.example.chatapp.domain.model.GroupMessage
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

    private var chatDao: ChatDao? = null

    init {
        context?.let {
            chatDao = com.example.chatapp.data.local.AppDatabase.getDatabase(it).chatDao()
            try {
                com.google.firebase.database.FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            } catch (e: Exception) {
                // Ignore if already set
            }
        }
    }

    // --- Group Operations ---

    suspend fun createGroup(name: String, description: String, profilePhotoUrl: String? = null): String? {
        val groupId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        val members = listOf(currentUserId)
        val groupMasterKey = KeyManager.generateGroupMasterKey()

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
                groupMasterKey = groupMasterKey,
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

            val adminId = groupSnapshot.child("adminId").getValue(String::class.java)
            if (adminId == null) {
                _errorMessage.value = "Group has no admin."
                return false
            }

            // Route the join request through E2EE DM to the Admin
            val payload = org.json.JSONObject().apply {
                put("type", "JOIN_REQUEST")
                put("groupId", groupId)
                put("requesterId", currentUserId)
            }.toString()

            val chatRepo = ChatRepository(
                chatDao = com.example.chatapp.data.local.AppDatabase.getDatabase(context!!).chatDao(),
                currentUserId = currentUserId,
                context = context
            )
            
            chatRepo.establishChannel(adminId)
            chatRepo.sendSystemMessage(adminId, payload)
            
            _errorMessage.value = "Join request sent to Admin securely."
            
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
                val existingGroup = withContext(Dispatchers.IO) { groupDao.getGroupById(groupId) }
                val groupEntity = GroupEntity(
                    groupId = snapshot.child("groupId").getValue(String::class.java) ?: groupId,
                    name = snapshot.child("name").getValue(String::class.java) ?: "Unknown Group",
                    description = snapshot.child("description").getValue(String::class.java) ?: "",
                    profilePhotoUrl = snapshot.child("profilePhotoUrl").getValue(String::class.java),
                    adminId = snapshot.child("adminId").getValue(String::class.java) ?: "",
                    members = snapshot.child("members").children.mapNotNull { it.getValue(String::class.java) },
                    groupMasterKey = existingGroup?.groupMasterKey,
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
    
    // --- Join Requests (Admin Only) ---

    suspend fun acceptJoinRequest(requestId: String, groupId: String, requesterId: String) {
        try {
            val group = withContext(Dispatchers.IO) { groupDao.getGroupById(groupId) }
            if (group == null || group.adminId != currentUserId || group.groupMasterKey == null) {
                _errorMessage.value = "Cannot accept: Not admin or master key missing."
                return
            }

            // 1. Derive shared secret with requester to encrypt the master key
            val sharedSecret = KeyManager.deriveStaticSharedSecret(
                myUserId = currentUserId, // Admin
                otherUserId = requesterId
            )
            
            if (sharedSecret == null) {
                _errorMessage.value = "Requester has not set up their encryption keys."
                return
            }

            // 2. Encrypt Master Key
            val encryptedKeyResult = KeyManager.encryptGroupMasterKeyForMember(group.groupMasterKey, sharedSecret)
            
            // 3. Upload encrypted Master Key to RTDB
            val keyPayload = mapOf(
                "ciphertext" to encryptedKeyResult.ciphertext,
                "iv" to encryptedKeyResult.iv
            )
            rtdb.child("groups").child(groupId).child("keys").child(requesterId).setValue(keyPayload).await()

            // 4. Add to members list
            val groupSnapshot = rtdb.child("groups").child(groupId).get().await()
            val currentMembers = groupSnapshot.child("members").children.mapNotNull { it.getValue(String::class.java) }
            if (!currentMembers.contains(requesterId)) {
                val newMembers = currentMembers + requesterId
                rtdb.child("groups").child(groupId).child("members").setValue(newMembers).await()
            }

            // 5. Send JOIN_ACCEPTED DM
            val chatRepo = ChatRepository(
                chatDao = com.example.chatapp.data.local.AppDatabase.getDatabase(context!!).chatDao(),
                currentUserId = currentUserId,
                context = context!!
            )
            val payload = org.json.JSONObject().apply {
                put("type", "JOIN_ACCEPTED")
                put("groupId", groupId)
            }.toString()
            chatRepo.establishChannel(requesterId)
            chatRepo.sendSystemMessage(requesterId, payload)

            // 6. Delete request from local DB
            withContext(Dispatchers.IO) {
                groupDao.deleteJoinRequest(requestId)
            }
            
            // 7. Update local group cache
            fetchAndCacheGroupInfo(groupId)
            
        } catch (e: Exception) {
            _errorMessage.value = "Failed to accept request: ${e.message}"
            Log.e("GroupRepository", "acceptJoinRequest error", e)
        }
    }

    fun declineJoinRequest(requestId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            groupDao.deleteJoinRequest(requestId)
        }
    }

    fun getJoinRequests(groupId: String): Flow<List<com.example.chatapp.data.local.GroupJoinRequestEntity>> {
        return groupDao.getJoinRequestsForGroup(groupId)
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
                        val groupId = groupSnapshot.child("groupId").getValue(String::class.java) ?: continue
                        val members = groupSnapshot.child("members").children.mapNotNull { it.getValue(String::class.java) }
                        if (members.contains(currentUserId)) {
                            val existingGroup = groupDao.getGroupById(groupId)
                            var groupEntity = GroupEntity(
                                groupId = groupId,
                                name = groupSnapshot.child("name").getValue(String::class.java) ?: "Unknown Group",
                                description = groupSnapshot.child("description").getValue(String::class.java) ?: "",
                                profilePhotoUrl = groupSnapshot.child("profilePhotoUrl").getValue(String::class.java),
                                adminId = groupSnapshot.child("adminId").getValue(String::class.java) ?: "",
                                members = members,
                                groupMasterKey = existingGroup?.groupMasterKey,
                                createdAt = groupSnapshot.child("createdAt").getValue(Long::class.java) ?: 0L
                            )

                            // Attempt to fetch/decrypt missing master key immediately upon syncing group
                            if (groupEntity.groupMasterKey == null) {
                                try {
                                    val encKeySnapshot = rtdb.child("groups").child(groupId).child("keys").child(currentUserId).get().await()
                                    if (encKeySnapshot.exists()) {
                                        val ciphertext = encKeySnapshot.child("ciphertext").getValue(String::class.java) ?: ""
                                        val iv = encKeySnapshot.child("iv").getValue(String::class.java) ?: ""
                                        
                                        val sharedSecret = KeyManager.deriveStaticSharedSecret(
                                            myUserId = currentUserId,
                                            otherUserId = groupEntity.adminId
                                        )
                                        
                                        if (sharedSecret != null) {
                                            val plaintextKey = KeyManager.decryptGroupMasterKey(ciphertext, iv, sharedSecret)
                                            groupEntity = groupEntity.copy(groupMasterKey = plaintextKey)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("GroupRepository", "Failed early key fetch for $groupId", e)
                                }
                            }

                            myGroups.add(groupEntity)
                            groupDao.insertGroup(groupEntity) // Update local Cache with key immediately
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
        
        val group = withContext(Dispatchers.IO) { groupDao.getGroupById(groupId) }
        val totalMembers = group?.members?.size ?: 1
        val masterKeyBase64 = group?.groupMasterKey

        if (masterKeyBase64 == null) {
            _errorMessage.value = "Cannot send message: Missing group master key."
            return
        }

        // Encrypt the content using the Group Master Key
        val masterKeyBytes = android.util.Base64.decode(masterKeyBase64, android.util.Base64.NO_WRAP)
        val encryptedResult = KeyManager.encrypt(content, masterKeyBytes)

        val msgMap = mapOf(
            "messageId" to messageId,
            "groupId" to groupId,
            "senderId" to currentUserId,
            "senderName" to senderName,
            "content" to encryptedResult.ciphertext,
            "contentIv" to encryptedResult.iv,
            "timestamp" to timestamp,
            "isPoll" to false,
            "readBy" to mapOf(currentUserId to true)
        )

        try {
            if (totalMembers > 1) {
                rtdb.child("group_messages").child(groupId).child(messageId).setValue(msgMap).await()
            }
            val entity = GroupMessageEntity(
                messageId = messageId,
                groupId = groupId,
                senderId = currentUserId,
                senderName = senderName,
                content = content,
                timestamp = timestamp,
                isSentByMe = true,
                isPoll = false,
                readByCount = 1,
                isReadByMe = true
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
        
        val group = withContext(Dispatchers.IO) { groupDao.getGroupById(groupId) }
        val totalMembers = group?.members?.size ?: 1
        val masterKeyBase64 = group?.groupMasterKey

        if (masterKeyBase64 == null) {
            _errorMessage.value = "Cannot create poll: Missing group master key."
            return
        }

        // Encrypt the 'content' and 'question' strings
        val masterKeyBytes = android.util.Base64.decode(masterKeyBase64, android.util.Base64.NO_WRAP)
        
        val contentResult = KeyManager.encrypt("Created a poll: $question", masterKeyBytes)
        val questionResult = KeyManager.encrypt(question, masterKeyBytes)

        val pollMap = mutableMapOf<String, Any>(
            "messageId" to messageId,
            "groupId" to groupId,
            "senderId" to currentUserId,
            "senderName" to senderName,
            "content" to contentResult.ciphertext,
            "contentIv" to contentResult.iv,
            "timestamp" to timestamp,
            "isPoll" to true,
            "pollId" to pollId,
            "pollQuestion" to questionResult.ciphertext,
            "pollQuestionIv" to questionResult.iv,
            "readBy" to mapOf(currentUserId to true)
        )
        
        val optionsMap = options.associateWith { 0 }
        pollMap["pollOptions"] = optionsMap // Initial votes are 0

        try {
            if (totalMembers > 1) {
                rtdb.child("group_messages").child(groupId).child(messageId).setValue(pollMap).await()
            }
            
            val pollOptionsJson = JSONObject(optionsMap as Map<*, *>).toString()
            val entity = GroupMessageEntity(
                messageId = messageId,
                groupId = groupId,
                senderId = currentUserId,
                senderName = senderName,
                content = "Created a poll: $question",
                timestamp = timestamp,
                isSentByMe = true,
                isPoll = true,
                pollId = pollId,
                pollQuestion = question,
                pollOptionsJson = pollOptionsJson,
                readByCount = 1,
                isReadByMe = true
            )
            withContext(Dispatchers.IO) {
                groupDao.insertGroupMessage(entity)
            }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to create poll: ${e.message}"
            Log.e("GroupRepository", "createPoll error", e)
        }
    }

    suspend fun sendGroupInviteMessage(groupId: String, senderName: String, inviteGroupId: String, inviteGroupName: String) {
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        
        val group = withContext(Dispatchers.IO) { groupDao.getGroupById(groupId) }
        val masterKeyBase64 = group?.groupMasterKey

        if (masterKeyBase64 == null) {
            _errorMessage.value = "Cannot send invite: Missing group master key."
            return
        }

        val masterKeyBytes = android.util.Base64.decode(masterKeyBase64, android.util.Base64.NO_WRAP)
        
        val payload = org.json.JSONObject().apply {
            put("type", "GROUP_INVITE")
            put("groupId", inviteGroupId)
            put("groupName", inviteGroupName)
        }.toString()

        val encryptResult = KeyManager.encrypt(payload, masterKeyBytes)

        val messageMap = mapOf(
            "messageId" to messageId,
            "groupId" to groupId,
            "senderId" to currentUserId,
            "senderName" to senderName,
            "content" to encryptResult.ciphertext,
            "contentIv" to encryptResult.iv,
            "timestamp" to timestamp,
            "readBy" to mapOf(currentUserId to true) // Initialize readBy with sender
        )

        try {
            rtdb.child("group_messages").child(groupId).child(messageId).setValue(messageMap).await()
            val entity = GroupMessageEntity(
                messageId = messageId,
                groupId = groupId,
                senderId = currentUserId,
                senderName = senderName,
                content = "Invited you to join $inviteGroupName",
                timestamp = timestamp,
                isSentByMe = true,
                isPoll = false,
                readByCount = 1,
                isReadByMe = true,
                isGroupInvite = true,
                inviteGroupId = inviteGroupId,
                inviteGroupName = inviteGroupName
            )
            withContext(Dispatchers.IO) {
                groupDao.insertGroupMessage(entity)
            }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to send group invite: ${e.message}"
            Log.e("GroupRepository", "sendGroupInviteMessage error", e)
        }
    }

    suspend fun sendGroupMediaMessage(
        groupId: String,
        senderName: String,
        mediaUrl: String,
        mediaKey: String,
        mediaIv: String,
        mediaType: String,
        mediaFileName: String?,
        messageId: String
    ) {
        val timestamp = System.currentTimeMillis()

        val group = withContext(Dispatchers.IO) { groupDao.getGroupById(groupId) }
        val totalMembers = group?.members?.size ?: 1
        val masterKeyBase64 = group?.groupMasterKey

        if (masterKeyBase64 == null) {
            _errorMessage.value = "Cannot send media: Missing group master key."
            return
        }

        val masterKeyBytes = android.util.Base64.decode(masterKeyBase64, android.util.Base64.NO_WRAP)

        // Encrypt the media metadata (url+key+iv) using group master key
        val payload = org.json.JSONObject().apply {
            put("type", "GROUP_MEDIA")
            put("mediaUrl", mediaUrl)
            put("mediaKey", mediaKey)
            put("mediaIv", mediaIv)
            put("mediaType", mediaType)
            if (mediaFileName != null) put("mediaFileName", mediaFileName)
        }.toString()

        val encryptResult = KeyManager.encrypt(payload, masterKeyBytes)

        val msgMap = mapOf(
            "messageId" to messageId,
            "groupId" to groupId,
            "senderId" to currentUserId,
            "senderName" to senderName,
            "content" to encryptResult.ciphertext,
            "contentIv" to encryptResult.iv,
            "timestamp" to timestamp,
            "isPoll" to false,
            "readBy" to mapOf(currentUserId to true)
        )

        try {
            if (totalMembers > 1) {
                rtdb.child("group_messages").child(groupId).child(messageId).setValue(msgMap).await()
            }
            val entity = GroupMessageEntity(
                messageId = messageId,
                groupId = groupId,
                senderId = currentUserId,
                senderName = senderName,
                content = "[$mediaType]",
                timestamp = timestamp,
                isSentByMe = true,
                isPoll = false,
                mediaUrl = mediaUrl,
                mediaKey = mediaKey,
                mediaIv = mediaIv,
                mediaFileName = mediaFileName,
                mediaType = mediaType,
                readByCount = 1,
                isReadByMe = true
            )
            withContext(Dispatchers.IO) { groupDao.insertGroupMessage(entity) }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to send media: ${e.message}"
            Log.e("GroupRepository", "sendGroupMediaMessage error", e)
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
                    val group = groupDao.getGroupById(groupId) ?: return@launch
                    var masterKeyBase64 = group.groupMasterKey

                    if (masterKeyBase64 == null) {
                        try {
                            val encKeySnapshot = rtdb.child("groups").child(groupId).child("keys").child(currentUserId).get().await()
                            if (encKeySnapshot.exists()) {
                                val ciphertext = encKeySnapshot.child("ciphertext").getValue(String::class.java) ?: ""
                                val iv = encKeySnapshot.child("iv").getValue(String::class.java) ?: ""
                                
                                val sharedSecret = KeyManager.deriveStaticSharedSecret(
                                    myUserId = currentUserId,
                                    otherUserId = group.adminId
                                )
                                
                                if (sharedSecret != null) {
                                    val plaintextKey = KeyManager.decryptGroupMasterKey(ciphertext, iv, sharedSecret)
                                    // Save to DB
                                    groupDao.insertGroup(group.copy(groupMasterKey = plaintextKey))
                                    masterKeyBase64 = plaintextKey
                                    Log.d("GroupRepository", "Successfully fetched and decrypted Master Key for group $groupId")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("GroupRepository", "Failed to fetch/decrypt Master Key", e)
                        }
                    }

                    // If we still don't have it, we cannot decrypt these messages yet.
                    if (masterKeyBase64 == null) {
                        Log.w("GroupRepository", "No Master Key available for $groupId. Cannot read messages.")
                        return@launch
                    }

                    val masterKeyBytes = android.util.Base64.decode(masterKeyBase64, android.util.Base64.NO_WRAP)

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

                        val readByNode = msgSnapshot.child("readBy")
                        val readByCount = readByNode.childrenCount.toInt()
                        val isReadByMe = readByNode.hasChild(currentUserId)

                        // Decrypt content
                        var contentStr = "[Encrypted]"
                        var pollQuestionStr: String? = null
                        
                        var isGroupInvite = false
                        var inviteGroupId: String? = null
                        var inviteGroupName: String? = null
                        
                        try {
                            val encContent = msgSnapshot.child("content").getValue(String::class.java) ?: ""
                            val encContentIv = msgSnapshot.child("contentIv").getValue(String::class.java) ?: ""
                            if (encContent.isNotEmpty() && encContentIv.isNotEmpty()) {
                                contentStr = KeyManager.decrypt(encContent, encContentIv, masterKeyBytes)
                            } else {
                                // Fallback for older plaintext messages
                                contentStr = msgSnapshot.child("content").getValue(String::class.java) ?: ""
                            }
                            
                            // Check if it's a group invite JSON
                            try {
                                val json = org.json.JSONObject(contentStr)
                                val type = json.optString("type", "")
                                if (type == "GROUP_INVITE") {
                                    isGroupInvite = true
                                    inviteGroupId = json.optString("groupId", null)
                                    inviteGroupName = json.optString("groupName", null)
                                    contentStr = "Invited you to join ${inviteGroupName ?: "a group"}"
                                }
                            } catch (e: Exception) {
                                // Not JSON, regular text
                            }

                            if (isPoll) {
                                val encQuestion = msgSnapshot.child("pollQuestion").getValue(String::class.java) ?: ""
                                val encQuestionIv = msgSnapshot.child("pollQuestionIv").getValue(String::class.java) ?: ""
                                if (encQuestion.isNotEmpty() && encQuestionIv.isNotEmpty()) {
                                    pollQuestionStr = KeyManager.decrypt(encQuestion, encQuestionIv, masterKeyBytes)
                                } else {
                                    pollQuestionStr = msgSnapshot.child("pollQuestion").getValue(String::class.java)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("GroupRepository", "Decryption failed for group message $messageId", e)
                        }

                        // Try to fetch unknown user into our contacts quietly
                        CoroutineScope(Dispatchers.IO).launch {
                            ensureUserIsInContacts(senderId)
                        }

                        // Parse media fields from the decrypted content (GROUP_MEDIA messages)
                        var parsedMediaUrl: String? = null
                        var parsedMediaKey: String? = null
                        var parsedMediaIv: String? = null
                        var parsedMediaFileName: String? = null
                        var parsedMediaType: String? = null

                        if (!isGroupInvite) {
                            try {
                                val json = org.json.JSONObject(contentStr)
                                if (json.optString("type") == "GROUP_MEDIA") {
                                    parsedMediaUrl = json.optString("mediaUrl", null)
                                    parsedMediaKey = json.optString("mediaKey", null)
                                    parsedMediaIv = json.optString("mediaIv", null)
                                    parsedMediaType = json.optString("mediaType", null)
                                    parsedMediaFileName = json.optString("mediaFileName", null)
                                    contentStr = "[${parsedMediaType ?: "Media"}]"
                                }
                            } catch (_: Exception) {}
                        }

                        val entity = GroupMessageEntity(
                            messageId = messageId,
                            groupId = groupId,
                            senderId = senderId,
                            senderName = msgSnapshot.child("senderName").getValue(String::class.java) ?: "User",
                            content = contentStr,
                            timestamp = msgSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L,
                            isSentByMe = senderId == currentUserId,
                            isPoll = isPoll,
                            pollId = msgSnapshot.child("pollId").getValue(String::class.java),
                            pollQuestion = pollQuestionStr,
                            pollOptionsJson = pollOptionsJson,
                            userVotedOption = userVotedOption,
                            readByCount = readByCount,
                            isReadByMe = isReadByMe,
                            mediaUrl = parsedMediaUrl,
                            mediaKey = parsedMediaKey,
                            mediaIv = parsedMediaIv,
                            mediaFileName = parsedMediaFileName,
                            mediaType = parsedMediaType,
                            isGroupInvite = isGroupInvite,
                            inviteGroupId = inviteGroupId,
                            inviteGroupName = inviteGroupName
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

    fun getUnreadCountForGroup(groupId: String): Flow<Int> =
        groupDao.getUnreadCountForGroup(groupId)

    /**
     * Mark a message as read by the current user.
     *
     * Uses an RTDB transaction so that exactly one client — the one whose write
     * causes readBy.size to reach the total member count — is responsible for
     * deleting the message from RTDB. All other clients simply add their UID and
     * return. This prevents N simultaneous deletes and any race condition.
     *
     * @param groupId      The group this message belongs to.
     * @param messageId    The message to mark as read.
     * @param totalMembers The current member count of the group (used as the threshold).
     */
    fun markMessageRead(groupId: String, messageId: String, totalMembers: Int) {
        val msgRef = rtdb.child("group_messages").child(groupId).child(messageId)

        msgRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                // Add our UID to readBy. Each user owns only their own subnode,
                // so concurrent writes from different users never conflict.
                data.child("readBy").child(currentUserId).value = true

                // If we are the last reader, flag it so onComplete can delete.
                val readByCount = data.child("readBy").childrenCount
                if (readByCount >= totalMembers) {
                    data.child("readByAll").value = true
                }
                return Transaction.success(data)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                snapshot: DataSnapshot?
            ) {
                if (!committed || snapshot == null) return

                // Only the client whose transaction resulted in readByAll = true deletes from RTDB.
                // We do NOT delete from local Room — messages stay in chat history.
                val readByAll = snapshot.child("readByAll").getValue(Boolean::class.java) ?: false
                if (readByAll) {
                    msgRef.removeValue().addOnFailureListener { e ->
                        Log.e("GroupRepository", "Failed to delete read-by-all message $messageId from RTDB", e)
                    }
                } else {
                    // Update local cache to reflect that we have now read this message.
                    // This flips isReadByMe locally so the unread badge updates instantly;
                    // the next listener fire will do a full sync.
                    CoroutineScope(Dispatchers.IO).launch {
                        val readByCount = snapshot.child("readBy").childrenCount.toInt()
                        groupDao.markMessageReadLocally(messageId, readByCount)
                    }
                }
            }
        })
    }

    suspend fun exitGroup(groupId: String) {
        try {
            // 1. Remove user from RTDB members list
            val groupSnapshot = rtdb.child("groups").child(groupId).get().await()
            val members = groupSnapshot.child("members").children.mapNotNull { it.getValue(String::class.java) }.toMutableList()
            if (members.remove(currentUserId)) {
                rtdb.child("groups").child(groupId).child("members").setValue(members).await()
            }

            // 2. Delete user's master key backup on RTDB
            rtdb.child("groups").child(groupId).child("keys").child(currentUserId).removeValue().await()
            
            // 3. Update local Room schema to set hasExited = true
            withContext(Dispatchers.IO) {
                groupDao.getGroupById(groupId)?.let { localGroup ->
                    groupDao.insertGroup(localGroup.copy(hasExited = true))
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to exit group: ${e.message}"
            Log.e("GroupRepository", "exitGroup error", e)
        }
    }
    
    // Sync missing group member into local contacts
    private suspend fun ensureUserIsInContacts(userId: String) {
        chatDao?.let { dao ->
            if (dao.getUserById(userId) == null) {
                try {
                    val userSnapshot = rtdb.child("users").child(userId).get().await()
                    if (userSnapshot.exists()) {
                        val name = userSnapshot.child("name").getValue(String::class.java) ?: "Unknown User"
                        val email = userSnapshot.child("email").getValue(String::class.java) ?: ""
                        val status = userSnapshot.child("status").getValue(String::class.java) ?: "Available"
                        val profilePhotoUrl = userSnapshot.child("profilePhotoUrl").getValue(String::class.java)
                        
                        val newUser = com.example.chatapp.data.local.UserEntity(
                            userId = userId,
                            name = name,
                            status = status,
                            profilePhotoUrl = profilePhotoUrl
                        )
                        withContext(Dispatchers.IO) {
                            dao.insertUser(newUser)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GroupRepository", "Failed to fetch user $userId for offline caching", e)
                }
            }
        }
    }

    suspend fun deleteGroup(groupId: String) {
        try {
            // Execute exit logic first on server
            exitGroup(groupId)
            
            // Delete all traces locally
            withContext(Dispatchers.IO) {
                groupDao.clearMessagesForGroup(groupId)
                groupDao.deleteGroup(groupId)
            }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to delete group: ${e.message}"
            Log.e("GroupRepository", "deleteGroup error", e)
        }
    }

    private fun deleteCachedMedia(messageId: String, mediaFileName: String?) {
        val ctx = context ?: return
        val name = mediaFileName ?: "${messageId}_media"
        val file = java.io.File(java.io.File(ctx.filesDir, "media"), name)
        if (file.exists()) file.delete()
        try {
            ctx.contentResolver.delete(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(name)
            )
        } catch (e: Exception) {
            Log.w("GroupRepository", "MediaStore delete failed for $name", e)
        }
    }

    fun clearMessagesForGroup(groupId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Delete cached media files before wiping DB rows
                val messages = groupDao.getMessagesForGroup(groupId).first()
                messages.forEach { deleteCachedMedia(it.messageId, it.mediaFileName) }
                groupDao.clearMessagesForGroup(groupId)
            } catch (e: Exception) {
                Log.e("GroupRepository", "Failed to clear chat messages", e)
            }
        }
    }

    fun deleteMessages(messageIds: List<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                messageIds.forEach { id ->
                    val msg = groupDao.getGroupMessageById(id)
                    deleteCachedMedia(id, msg?.mediaFileName)
                    groupDao.deleteGroupMessage(id)
                }
            } catch (e: Exception) {
                Log.e("GroupRepository", "Failed to delete specific messages", e)
            }
        }
    }

    fun clearError() { _errorMessage.value = null }
}

fun GroupEntity.toDomainModel() = Group(
    groupId = groupId, name = name, description = description, profilePhotoUrl = profilePhotoUrl,
    adminId = adminId, members = members, createdAt = createdAt, hasExited = hasExited
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
        mediaUrl = mediaUrl, mediaKey = mediaKey, mediaIv = mediaIv,
        mediaFileName = mediaFileName, mediaType = mediaType,
        isPoll = isPoll, pollId = pollId, pollQuestion = pollQuestion,
        pollOptions = optionsMap, userVotedOption = userVotedOption,
        readByCount = readByCount, isReadByMe = isReadByMe,
        isGroupInvite = isGroupInvite, inviteGroupId = inviteGroupId, inviteGroupName = inviteGroupName, inviteStatus = inviteStatus
    )
}
