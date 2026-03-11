package com.example.chatapp.data.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

object MediaCryptoHelper {
    private const val GCM_TAG_LENGTH = 128 // bits
    private const val GCM_IV_LENGTH = 12   // bytes
    private const val AES_KEY_LENGTH = 32  // 256 bits

    class MediaCryptResult(
        val ciphertext: ByteArray,
        val mediaKeyBase64: String,
        val mediaIvBase64: String
    )

    fun encryptMedia(plaintext: ByteArray): MediaCryptResult {
        // Generate random AES-256 key
        val keyBytes = ByteArray(AES_KEY_LENGTH)
        SecureRandom().nextBytes(keyBytes)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        // Generate random IV
        val ivBytes = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(ivBytes)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, ivBytes)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        
        val ciphertext = cipher.doFinal(plaintext)
        
        return MediaCryptResult(
            ciphertext = ciphertext,
            mediaKeyBase64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP),
            mediaIvBase64 = Base64.encodeToString(ivBytes, Base64.NO_WRAP)
        )
    }

    fun decryptMedia(ciphertext: ByteArray, mediaKeyBase64: String, mediaIvBase64: String): ByteArray {
        val keyBytes = Base64.decode(mediaKeyBase64, Base64.NO_WRAP)
        val ivBytes = Base64.decode(mediaIvBase64, Base64.NO_WRAP)

        val secretKey = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, ivBytes)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        
        return cipher.doFinal(ciphertext)
    }
}
