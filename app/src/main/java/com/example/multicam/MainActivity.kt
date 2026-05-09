package com.example.multicam

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.multicam.api.RetrofitClient
import com.example.multicam.ui.screen.MainScreen
import com.example.multicam.ui.screen.RegistrationScreen
import com.example.multicam.ui.theme.MultiCamTheme

class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs       = getSharedPreferences("auth", Context.MODE_PRIVATE)
        val wasLoggedIn = prefs.getBoolean("is_logged_in", false)

        val savedToken = prefs.getString("auth_token", null)
        if (!savedToken.isNullOrBlank()) {
            RetrofitClient.authToken = savedToken
        }

        setContent {
            MultiCamTheme {
                var loggedIn by remember { mutableStateOf(wasLoggedIn) }

                if (loggedIn) {
                    val isGuest = prefs.getBoolean("is_guest", false)

                    MainScreen(
                        isGuest = isGuest,
                        onRegisterClick = {
                            // Clear session and show registration screen
                            prefs.edit()
                                .putBoolean("is_logged_in", false)
                                .remove("auth_token")
                                .remove("is_guest")
                                .apply()
                            RetrofitClient.authToken = null
                            loggedIn = false
                        }
                    )
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        RegistrationScreen(
                            modifier       = Modifier.padding(innerPadding),
                            onLoginSuccess = {
                                prefs.edit().putBoolean("is_logged_in", true).apply()
                                loggedIn = true
                            }
                        )
                    }
                }
            }
        }
    }
}