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
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    // ── Email Link (Passwordless) Flow ───────────────────────────

    /**
     * Step 1: Send the sign-in link to the user's email.
     */
    suspend fun sendEmailLink(email: String, context: Context): AuthResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val actionCodeSettings = com.google.firebase.auth.ActionCodeSettings.newBuilder()
                // The domain below must be in Firebase Console → Authentication → Settings → Authorised domains.
                // For the link to open the app directly (not browser), Firebase Hosting must serve
                // /.well-known/assetlinks.json at this domain. See walkthrough for setup steps.
                .setUrl("https://chatting-27210.firebaseapp.com/login")
                .setHandleCodeInApp(true)
                .setAndroidPackageName(
                    context.packageName,
                    true, /* installIfNotAvailable */
                    null /* minimumVersion */
                )
                .build()

            auth.sendSignInLinkToEmail(email, actionCodeSettings).await()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email link", e)
            AuthResult.Error(friendlyAuthError(e.message ?: "Failed to send login link"))
        }
    }

    /**
     * Step 2: Verify the email link clicked by the user.
     * Returns true if it's a NEW user (needs profile setup), false if returning user.
     */
    suspend fun verifyEmailLink(
        email: String,
        emailLink: String,
        context: Context
    ): AuthResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!auth.isSignInWithEmailLink(emailLink)) {
                return@withContext AuthResult.Error("Invalid or expired sign-in link.")
            }

            val authResult = auth.signInWithEmailLink(email, emailLink).await()
            val user = authResult.user ?: throw Exception("Failed to authenticate locally")
            val isNewUser = authResult.additionalUserInfo?.isNewUser == true

            if (!isNewUser) {
                // For returning users, just make sure we have their keys
                val name = user.displayName ?: "User"
                ensurePublicKey(context, user.uid, name)
            }

            // Return true if new user (needs profile setup), false otherwise
            AuthResult.Success(isNewUser)
        } catch (e: Exception) {
            Log.e(TAG, "Email link verification failed", e)
            AuthResult.Error(friendlyAuthError(e.message ?: "Failed to sign in with link"))
        }
    }

    /**
     * Step 3: Complete profile setup for NEW users.
     */
    suspend fun completeProfileSetup(
        name: String,
        context: Context,
        profilePhotoUri: android.net.Uri? = null
    ): AuthResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser ?: return@withContext AuthResult.Error("Not authenticated")
            val uid = user.uid
            val email = user.email ?: ""
            val lowercaseName = name.trim().lowercase()

            // 1. Claim username
            val usernameRef = rtdb.child("usernames").child(lowercaseName)
            val snapshot = usernameRef.get().await()
            
            if (snapshot.exists() && snapshot.getValue(String::class.java) != uid) {
                return@withContext AuthResult.Error("Username already taken. Please choose another one.")
            }
            usernameRef.setValue(uid).await()

            // 2. Update Firebase Profile
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(name.trim())
                .build()
            user.updateProfile(profileUpdates).await()

            // 3. Upload photo
            var photoUrl = ""
            if (profilePhotoUri != null) {
                val uploadedUrl = com.example.chatapp.utils.CloudinaryHelper.uploadProfilePhoto(context, profilePhotoUri)
                if (uploadedUrl != null) {
                    photoUrl = uploadedUrl
                }
            }

            // 4. Write to RTDB users node
            val profileData = mapOf(
                "name" to name.trim(),
                "status" to "Available",
                "email" to email,
                "profilePhotoUrl" to photoUrl
            )
            rtdb.child("users").child(uid).setValue(profileData).await()

            // 5. Generate Identity Keys
            KeyManager.initIdentityKey(context, uid, name.trim())

            AuthResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Profile setup failed", e)
            AuthResult.Error(e.message ?: "Profile setup failed")
        }
    }

    /**
     * Google Sign-In Flow:
     * 1. Exchange Google ID Token for Firebase Credential
     * 2. Sign in or Create account in Firebase Auth
     * 3. Sync profile to RTDB and generate EC Keys
     */
    suspend fun signInWithGoogle(context: Context, idToken: String): AuthResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user ?: throw Exception("Google Sign-In failed")

            val isNewUser = authResult.additionalUserInfo?.isNewUser == true
            val name = user.displayName ?: "Google User"
            val email = user.email ?: ""
            val uid = user.uid

            if (isNewUser) {
                // Claim username (using a sanitized version of their name or email prefix)
                val baseUsername = (user.email?.substringBefore("@") ?: name.replace(" ", "")).lowercase()
                var finalUsername = baseUsername
                var counter = 1
                
                // Keep checking until we find an available username
                while (rtdb.child("usernames").child(finalUsername).get().await().exists()) {
                    finalUsername = "$baseUsername$counter"
                    counter++
                }
                rtdb.child("usernames").child(finalUsername).setValue(uid).await()

                // Save initial profile
                val profileData = mapOf(
                    "name" to name,
                    "status" to "Available",
                    "email" to email,
                    "profilePhotoUrl" to (user.photoUrl?.toString() ?: "")
                )
                rtdb.child("users").child(uid).setValue(profileData).await()

                // Generate keys immediately for new users
                KeyManager.initIdentityKey(context, uid, name)
            } else {
                // For returning users, just ensure their keys still exist locally
                ensurePublicKey(context, uid, name)
            }

            AuthResult.Success(isNewUser)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            AuthResult.Error(e.message ?: "Failed to sign in with Google")
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
