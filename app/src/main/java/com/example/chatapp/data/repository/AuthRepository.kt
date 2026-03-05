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

    /**
     * Sign in via REST → then sign in via SDK to set currentUser.
     */
    suspend fun login(email: String, password: String): AuthResult<Unit> {
        return try {
            // 1. Validate credentials via REST (no reCAPTCHA)
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("returnSecureToken", true)
            }
            val response = postJson(SIGN_IN_URL, body)

            if (response.has("error")) {
                val msg = response.getJSONObject("error").optString("message", "Login failed")
                return AuthResult.Error(friendlyAuthError(msg))
            }

            // 2. Sign in via SDK to populate auth.currentUser
            auth.signInWithEmailAndPassword(email, password).await()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Login failed", e)
            AuthResult.Error(e.message ?: "An unknown error occurred")
        }
    }

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
    ): AuthResult<Unit> {
        return try {
            // 1. Create account via REST
            val signUpBody = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("returnSecureToken", true)
            }
            val signUpResp = postJson(SIGN_UP_URL, signUpBody)

            if (signUpResp.has("error")) {
                val msg = signUpResp.getJSONObject("error").optString("message", "Registration failed")
                return AuthResult.Error(friendlyAuthError(msg))
            }

            val idToken = signUpResp.getString("idToken")
            val uid = signUpResp.getString("localId")

            // 2. Set displayName via REST
            val updateBody = JSONObject().apply {
                put("idToken", idToken)
                put("displayName", name)
                put("returnSecureToken", false)
            }
            postJson(UPDATE_PROFILE_URL, updateBody)

            // 3. Sign in via SDK to populate auth.currentUser
            auth.signInWithEmailAndPassword(email, password).await()

            // 4. Write profile to RTDB
            val profileData = mapOf(
                "name" to name,
                "status" to "Available",
                "email" to email
            )
            rtdb.child("users").child(uid).setValue(profileData).await()

            // 5. Generate EC key pair + publish publicKey to RTDB
            KeyManager.initIdentityKey(context, uid, name)

            AuthResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Registration failed", e)
            AuthResult.Error(e.message ?: "An unknown error occurred")
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

    // ── Logout ───────────────────────────────────────────────────

    fun logout() {
        auth.signOut()
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
