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
        RetrofitClient.appContext = this
        setContent {
            MultiCamTheme {
                var loggedIn     by remember { mutableStateOf(wasLoggedIn) }
                /**
                 * true — пользователь нажал «Регистрация» будучи гостем.
                 * В этом случае токен НЕ очищаем: он нужен для вызова /auth/upgrade.
                 */
                var upgradeMode  by remember { mutableStateOf(false) }

                if (loggedIn) {
                    val isGuest = prefs.getBoolean("is_guest", false)

                    MainScreen(
                        isGuest = isGuest,
                        onRegisterClick = {
                            // Токен гостя остаётся в RetrofitClient — он нужен для апгрейда.
                            // Только убираем флаг is_logged_in, чтобы перейти на экран регистрации.
                            prefs.edit()
                                .putBoolean("is_logged_in", false)
                                .apply()
                            upgradeMode = true
                            loggedIn = false
                        }
                    )
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        RegistrationScreen(
                            modifier        = Modifier.padding(innerPadding),
                            isGuestUpgrade  = upgradeMode,
                            onCancelUpgrade = if (upgradeMode) {
                                {
                                    // Гость передумал — возвращаем его обратно без изменений
                                    prefs.edit().putBoolean("is_logged_in", true).apply()
                                    upgradeMode = false
                                    loggedIn = true
                                }
                            } else null,
                            onLoginSuccess  = {
                                if (upgradeMode) {
                                    // После апгрейда: is_guest уже false (persistToken в VM),
                                    // просто снимаем режим и пускаем на главный экран.
                                    upgradeMode = false
                                }
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