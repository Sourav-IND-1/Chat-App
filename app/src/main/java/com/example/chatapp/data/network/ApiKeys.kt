package com.example.chatapp.data.network

import android.util.Base64

/**
 * Utility to decode obfuscated API keys that are injected via CMake/NDK.
 * Keys are decoded lazily once and cached to optimize startup and repeated access.
 */
object ApiKeys {
    init {
        System.loadLibrary("apikeys")
    }

    @JvmStatic
    private external fun getFirebaseKeyBase64(): String

    @JvmStatic
    private external fun getCloudinaryApiKeyBase64(): String

    @JvmStatic
    private external fun getCloudinaryCloudNameBase64(): String

    val firebaseWebApi: String by lazy { decode(getFirebaseKeyBase64()) }
    val cloudinaryApiKey: String by lazy { decode(getCloudinaryApiKeyBase64()) }
    val cloudinaryCloudName: String by lazy { decode(getCloudinaryCloudNameBase64()) }

    private fun decode(obfuscated: String): String {
        return try {
            String(Base64.decode(obfuscated, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            "" // Fallback
        }
    }
}
