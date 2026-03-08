package com.example.chatapp.data.repository

import android.content.Context
import android.util.Log
import com.example.chatapp.data.crypto.KeyManager
import com.example.chatapp.domain.model.AuthResult
import com.example.chatapp.domain.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles all authentication via the Firebase Auth REST API.
 *
 * WHY REST instead of GMS Firebase Auth SDK?
 * Firebase Auth SDK 22.x+ (bundled in Google Play Services) automatically attempts a
 * reCAPTCHA Enterprise token before every email/password sign-up/sign-in. This fails
 * when the reCAPTCHA Enterprise API is not fully configured OR the device's network
 * blocks Google's reCAPTCHA endpoints. The REST API calls Identity Toolkit directly
 * and has no reCAPTCHA pre-flight.
 *
 * RTDB structure written on register:
 *   /users/{uid}/name       – display name
 *   /users/{uid}/status     – "Available"
 *   /users/{uid}/email      – email
 *   /users/{uid}/publicKey  – Base64 EC public key (written by KeyManager)
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val rtdb: DatabaseReference = RtdbHelper.ref
) {
    companion object {
        private const val TAG = "AuthRepository"

        // Web API key from local.properties -> BuildConfig
        private val API_KEY = com.example.chatapp.BuildConfig.FIREBASE_WEB_API_KEY

        private val SIGN_UP_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$API_KEY"
        private val SIGN_IN_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$API_KEY"
        private val UPDATE_PROFILE_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:update?key=$API_KEY"
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    // ── REST helpers ─────────────────────────────────────────────

    private suspend fun postJson(urlStr: String, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            JSONObject(response)
        }

    // ── Login ────────────────────────────────────────────────────
    
    // Login function removed: Replaced by One-Install-One-Account flow (forced registration)

    // ── Register ─────────────────────────────────────────────────

    /**
     * Full registration flow via REST (no reCAPTCHA pre-flight):
     * 1. Create account via REST
     * 2. Update displayName via REST
     * 3. Sign in via SDK to populate auth.currentUser
     * 4. Write profile to RTDB
     * 5. Generate + publish EC key pair
     */
    suspend fun register(
        email: String,
        password: String,
        name: String,
        context: Context
    ): AuthResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val lowercaseName = name.trim().lowercase()

            // 1. Create account via REST to explicitly catch EMAIL_EXISTS
            val signUpBody = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
                put("returnSecureToken", true)
            }
            val signUpResp = postJson(SIGN_UP_URL, signUpBody)

            if (signUpResp.has("error")) {
                val msg = signUpResp.getJSONObject("error").optString("message", "Registration failed")
                if (msg.contains("EMAIL_EXISTS")) {
                    Log.d(TAG, "Intercepted EMAIL_EXISTS. Attempting ghost account recovery...")
                    return@withContext handleGhostAccountRecovery(email, password, name, context)
                }
                return@withContext AuthResult.Error(friendlyAuthError(msg))
            }

            val idToken = signUpResp.getString("idToken")
            val uid = signUpResp.getString("localId")

            // 2. Sign in via SDK immediately so we have `auth != null` for Security Rules
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = auth.currentUser ?: throw Exception("Failed to authenticate locally")

            // 3. SECURE CLAIM: Check if username is taken now that we are authenticated
            val usernameRef = rtdb.child("usernames").child(lowercaseName)
            val snapshot = usernameRef.get().await()
            
            if (snapshot.exists() && snapshot.getValue(String::class.java) != uid) {
                // Name is taken by someone else! Rollback the account creation.
                Log.d(TAG, "Registration failed: Username '$lowercaseName' is already taken. Rolling back auth...")
                user.delete().await()
                auth.signOut()
                return@withContext AuthResult.Error("Username already taken. Please choose another one.")
            }

            // 4. Claim the username
            usernameRef.setValue(uid).await()

            // 5. Update Profile
            val updateBody = JSONObject().apply {
                put("idToken", idToken)
                put("displayName", name.trim())
                put("returnSecureToken", false)
            }
            postJson(UPDATE_PROFILE_URL, updateBody)

            // 7. Write profile to RTDB
            val profileData = mapOf(
                "name" to name,
                "status" to "Available",
                "email" to email
            )
            rtdb.child("users").child(uid).setValue(profileData).await()

            // 8. Generate EC key pair + publish publicKey to RTDB
            KeyManager.initIdentityKey(context, uid, name)

            AuthResult.Success(Unit)
        } catch (e: Exception) {
            // Failsafe cleanup of claimed username if anything below REST creation crashes
            try {
                val lowercaseName = name.trim().lowercase()
                rtdb.child("usernames").child(lowercaseName).removeValue()
            } catch (cleanupEx: Exception) {
                Log.w(TAG, "Failed to cleanup claimed username after registration error", cleanupEx)
            }
            Log.e(TAG, "Registration failed", e)
            AuthResult.Error(e.message ?: "An unknown error occurred")
        }
    }

    /**
     * If a user reinstalls, their FirebaseAuth cache is empty, but their account still exists in the cloud.
     * When they try to register the same email, it throws EMAIL_EXISTS.
     * We prove ownership by signing in, wipe the old "ghost" data completely, and then re-register fresh keys.
     */
    private suspend fun handleGhostAccountRecovery(
        email: String,
        password: String,
        name: String,
        context: Context
    ): AuthResult<Unit> {
        try {
            // 1. Prove ownership of the ghost account
            auth.signInWithEmailAndPassword(email, password).await()
            val user = auth.currentUser ?: return AuthResult.Error("Failed to claim old account.")
            
            // 2. Obliterate the ghost account data and auth record
            // The ghost account likely corresponds to the current name they are trying to register,
            // but we use the ghost's actual profile displayName if possible.
            val ghostName = user.displayName ?: name
            deleteCurrentAccount(context, user.uid, ghostName)
            
            // 3. Retry registration from scratch now that the email is freed
            return register(email, password, name, context)
        } catch (e: Exception) {
            Log.e(TAG, "Ghost account recovery failed", e)
            return AuthResult.Error("Email already in use. If this is your old account, please double check the password to overwrite it.")
        }
    }

    // ── Profile ──────────────────────────────────────────────────

    /** Read a user's profile from RTDB. Returns null if the node doesn't exist. */
    suspend fun getUserProfile(uid: String): User? {
        return try {
            val snapshot = rtdb.child("users").child(uid).get().await()
            if (!snapshot.exists()) return null
            User(
                userId = uid,
                name = snapshot.child("name").getValue(String::class.java) ?: uid,
                status = snapshot.child("status").getValue(String::class.java) ?: "Available"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read profile for $uid", e)
            null
        }
    }

    /** Re-publish public key to RTDB if missing (called on login). */
    suspend fun ensurePublicKey(context: Context, uid: String, displayName: String) {
        try {
            val snapshot = rtdb.child("users").child(uid).child("publicKey").get().await()
            if (!snapshot.exists() || snapshot.getValue(String::class.java).isNullOrBlank()) {
                KeyManager.initIdentityKey(context, uid, displayName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensurePublicKey failed", e)
        }
    }

    // ── Logout & Account Deletion ────────────────────────────────

    fun logout() {
        auth.signOut()
    }
    
    /**
     * Completely destroys the account, removing all traces from Local DB, Keystore, 
     * RTDB chats, RTDB Profile, and Firebase Auth.
     */
    suspend fun deleteCurrentAccount(context: Context, uid: String, knownName: String? = null) {
        try {
            val user = auth.currentUser ?: return
            val displayName = knownName ?: user.displayName
            
            // 1. Clear Local SQLite Database on IO Thread
            withContext(Dispatchers.IO) {
                val db = com.example.chatapp.data.local.AppDatabase.getDatabase(context)
                db.clearAllTables()
            }

            // 2. Clear our Global Inbox from RTDB
            // Since we use an Inbox model, we just delete our own inbox. Any messages *we* sent
            // to others are in *their* inboxes and will be decrypted/deleted when they pull them,
            // or fail to decrypt (and get deleted anyway) if they pull them after we delete our keys.
            try {
                rtdb.child("user_inboxes").child(uid).removeValue().await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear global inbox on deletion", e)
            }

            // 3. Delete RTDB Profile (Total destruction instead of soft delete)
            rtdb.child("users").child(uid).removeValue().await()

            // 4. Delete EC Identity Key from Android KeyStore
            KeyManager.deleteIdentityKey(uid)
            
            // 5. Delete Firebase Auth Record
            user.delete().await()
            auth.signOut()
            Log.d(TAG, "Account successfully obliterated from system")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fully delete account", e)
            auth.signOut() 
            throw e
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun friendlyAuthError(code: String): String = when {
        code.contains("EMAIL_EXISTS") -> "This email is already registered."
        code.contains("INVALID_EMAIL") -> "Invalid email address."
        code.contains("WEAK_PASSWORD") -> "Password is too weak (min 6 characters)."
        code.contains("EMAIL_NOT_FOUND") -> "No account found with this email."
        code.contains("INVALID_PASSWORD") -> "Incorrect password."
        code.contains("USER_DISABLED") -> "This account has been disabled."
        code.contains("TOO_MANY_ATTEMPTS") -> "Too many attempts. Try again later."
        else -> code
    }
}
