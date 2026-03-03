package com.example.chatapp.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.tasks.await
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Manages the two-tier key architecture:
 * Tier 1: EC identity key pair (per user, created at login)
 * Tier 2: Per-conversation AES-256 shared secret (derived via ECDH)
 */
object KeyManager {

    private const val TAG = "KeyManager"
    private const val KEYSTORE_ALIAS_PREFIX = "chatapp_ec_"
    private const val PREFS_NAME = "chatapp_crypto"
    private const val GCM_TAG_LENGTH = 128 // bits
    private const val GCM_IV_LENGTH = 12   // bytes

    // ── Tier 1: Identity Key ─────────────────────────────────────

    /**
     * Generate EC key pair (if not already done) and publish public key + name to RTDB.
     * Called once at login time.
     */
    suspend fun initIdentityKey(context: Context, userId: String, displayName: String = userId) {
        val alias = KEYSTORE_ALIAS_PREFIX + userId

        // Check if key already exists in Keystore
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (!keyStore.containsAlias(alias)) {
            // Generate new EC key pair in AndroidKeyStore
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
            )
            kpg.initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_AGREE_KEY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .build()
            )
            kpg.generateKeyPair()
            Log.d(TAG, "Generated new EC identity key for $userId")
        }

        // Publish public key + profile to RTDB
        val publicKey = keyStore.getCertificate(alias).publicKey
        val publicKeyBase64 = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)

        val db = com.example.chatapp.data.repository.RtdbHelper.db
        db.getReference("users/$userId/publicKey").setValue(publicKeyBase64).await()
        db.getReference("users/$userId/name").setValue(displayName).await()
        db.getReference("users/$userId/status").setValue("Available").await()

        Log.d(TAG, "Published identity for $userId ($displayName) to RTDB")
    }

    /**
     * Get our private key from AndroidKeyStore.
     */
    private fun getPrivateKey(userId: String): java.security.PrivateKey {
        val alias = KEYSTORE_ALIAS_PREFIX + userId
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return (keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry).privateKey
    }

    // ── Tier 2: Per-Conversation Shared Secret ───────────────────

    /**
     * Derive (or retrieve cached) shared secret for a conversation.
     * Returns null if the other user's public key is not yet available.
     */
    suspend fun getOrDeriveSharedSecret(
        context: Context,
        myUserId: String,
        otherUserId: String
    ): ByteArray? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cacheKey = "shared_${myUserId}_$otherUserId"

        // Check cache first
        val cached = prefs.getString(cacheKey, null)
        if (cached != null) {
            return Base64.decode(cached, Base64.NO_WRAP)
        }

        // Fetch other user's public key from RTDB
        val snapshot = com.example.chatapp.data.repository.RtdbHelper.db
            .getReference("users/$otherUserId/publicKey")
            .get()
            .await()

        val otherPubKeyBase64 = snapshot.getValue(String::class.java) ?: return null

        // Decode their public key
        val otherPubKeyBytes = Base64.decode(otherPubKeyBase64, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance("EC")
        val otherPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(otherPubKeyBytes))

        // ECDH key agreement
        val myPrivateKey = getPrivateKey(myUserId)
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivateKey)
        keyAgreement.doPhase(otherPublicKey, true)
        val rawSecret = keyAgreement.generateSecret()

        // Hash to get a consistent 32-byte AES key
        val digest = MessageDigest.getInstance("SHA-256")
        val sharedSecret = digest.digest(rawSecret)

        // Cache it
        prefs.edit()
            .putString(cacheKey, Base64.encodeToString(sharedSecret, Base64.NO_WRAP))
            .apply()

        Log.d(TAG, "Derived shared secret for conversation with $otherUserId")
        return sharedSecret
    }

    // ── Encryption / Decryption ──────────────────────────────────

    data class EncryptionResult(val ciphertext: String, val iv: String)

    /**
     * Encrypt plaintext using AES-256-GCM with the shared secret.
     */
    fun encrypt(plaintext: String, sharedSecret: ByteArray): EncryptionResult {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey: SecretKey = SecretKeySpec(sharedSecret, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        val ciphertextBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return EncryptionResult(
            ciphertext = Base64.encodeToString(ciphertextBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    /**
     * Decrypt ciphertext using AES-256-GCM with the shared secret.
     */
    fun decrypt(ciphertextBase64: String, ivBase64: String, sharedSecret: ByteArray): String {
        val ciphertextBytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey: SecretKey = SecretKeySpec(sharedSecret, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        val plaintextBytes = cipher.doFinal(ciphertextBytes)
        return String(plaintextBytes, Charsets.UTF_8)
    }
}
