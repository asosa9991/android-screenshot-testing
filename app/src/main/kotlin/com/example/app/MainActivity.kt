package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chat.AppTheme
import com.example.chat.ChatRoute
import com.example.chat.ChatViewModel
import com.example.chat.HttpChatService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val vm = viewModel { ChatViewModel(HttpChatService(BuildConfig.CHAT_BASE_URL)) }
                ChatRoute(vm)
            }
        }
    }
}
