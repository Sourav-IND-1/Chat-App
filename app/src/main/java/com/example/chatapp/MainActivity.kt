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
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.example.chatapp.data.network.ApiKeys

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
            ChatAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}