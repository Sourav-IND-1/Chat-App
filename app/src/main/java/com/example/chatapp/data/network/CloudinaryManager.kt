package com.example.chatapp.data.network

import android.content.Context
import android.util.Log
import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles unsigned uploads to Cloudinary.
 * The user requested to bypass deletion and use Cloudinary purely for holding the E2EE blobs.
 */
object CloudinaryManager {
    private const val TAG = "CloudinaryManager"
    
    // Configured with the user-provided credentials
    private val cloudinary = Cloudinary(ObjectUtils.asMap(
        "cloud_name", "drpzfssem",
        "api_key", "277344235119189"
    ))

    /**
     * Upload an encrypted byte array to Cloudinary and return the secure URL.
     * We MUST use an unsigned upload preset since we don't have the API Secret.
     * NOTE: the user must configure "unsigned_preset" in their Cloudinary dashboard
     * to allow unsigned uploads, otherwise this will fail. We default to "default_unsigned"
     * as a placeholder.
     */
    suspend fun uploadEncryptedBlob(byteArray: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val params = ObjectUtils.asMap(
                "resource_type", "auto", // 'auto' allows Cloudinary to handle any raw file type including PDF, video, audio
                "upload_preset", "vids_preset"
                // We will use "ml_default" which is often the default unsigned preset name.
            )
            // Hardcode to ml_default, if it fails they need to create it.
            params["upload_preset"] = "ml_default"

            val result = cloudinary.uploader().unsignedUpload(byteArray, "ml_default", params)
            val url = result["secure_url"] as? String
            Log.d(TAG, "Uploaded blob to Cloudinary: $url")
            return@withContext url
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload to Cloudinary", e)
            return@withContext null
        }
    }
}
