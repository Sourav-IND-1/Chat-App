package com.example.chatapp.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object CloudinaryHelper {
    
    // We use a predefined Unsigned Upload Preset from the Cloudinary dashboard
    // The user needs to create this preset and configure it to target a specific folder (e.g. "app_profile_photos")
    private const val UNSIGNED_UPLOAD_PRESET = "profile_photos"
    
    /**
     * Uploads an image to Cloudinary using an unsigned upload preset.
     * Returns the secure HTTPS URL of the uploaded image if successful, or null if it fails.
     */
    suspend fun uploadProfilePhoto(context: Context, imageUri: Uri): String? {
        return suspendCancellableCoroutine { continuation ->
            try {
                MediaManager.get().upload(imageUri)
                    .unsigned(UNSIGNED_UPLOAD_PRESET)
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {
                            Log.d("CloudinaryHelper", "Upload started: $requestId")
                        }

                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                            // Optional: Handle progress
                        }

                        override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                            val secureUrl = resultData["secure_url"] as? String
                            Log.d("CloudinaryHelper", "Upload success. URL: $secureUrl")
                            continuation.resume(secureUrl)
                        }

                        override fun onError(requestId: String?, error: ErrorInfo?) {
                            Log.e("CloudinaryHelper", "Upload error: ${error?.description}")
                            continuation.resume(null)
                        }

                        override fun onReschedule(requestId: String?, error: ErrorInfo?) {
                            Log.e("CloudinaryHelper", "Upload rescheduled: ${error?.description}")
                        }
                    })
                    .dispatch()
            } catch (e: Exception) {
                Log.e("CloudinaryHelper", "Upload failed with exception", e)
                continuation.resume(null)
            }
        }
    }
}
