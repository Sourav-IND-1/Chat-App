package com.example.chatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.chatapp.ui.navigation.AppNavHost
import com.example.chatapp.ui.theme.ChatAppTheme
import com.example.chatapp.data.network.ApiKeys
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.cloudinary.android.MediaManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            val config = mapOf(
                "cloud_name" to ApiKeys.cloudinaryCloudName,
                "api_key" to ApiKeys.cloudinaryApiKey,
                "api_secret" to "", // Intentionally left blank. We are using unsigned uploads.
                "secure" to true // Force HTTPS
            )
            MediaManager.init(this, config)
        } catch (e: IllegalStateException) {
            // MediaManager is already initialized (can happen during activity recreation)
        }
        
        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey(ApiKeys.firebaseWebApi)
                .setApplicationId("1:551919609978:android:af622b41cb8fe32ae373c3")
                .setProjectId("chatting-27210")
                .setDatabaseUrl("https://chatting-27210-default-rtdb.asia-southeast1.firebasedatabase.app")
                .setStorageBucket("chatting-27210.firebasestorage.app")
                .build()
            FirebaseApp.initializeApp(this, options)
        }

        enableEdgeToEdge()
        setContent {
            val authViewModel: com.example.chatapp.ui.auth.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val context = androidx.compose.ui.platform.LocalContext.current
            
            // Handle Deep Link for Email Authentication
            androidx.compose.runtime.LaunchedEffect(intent) {
                val action = intent?.action
                val data = intent?.data
                if (action == android.content.Intent.ACTION_VIEW && data != null) {
                    val link = data.toString()
                    if (com.google.firebase.auth.FirebaseAuth.getInstance().isSignInWithEmailLink(link)) {
                        authViewModel.verifyEmailLink(link, this@MainActivity)
                    }
                }
            }
            
            ChatAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(authViewModel = authViewModel)
                }
            }
        }
    }
}