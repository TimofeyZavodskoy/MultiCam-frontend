package com.example.multicam.ui.screen

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multicam.api.RetrofitClient
import com.example.multicam.api.dto.GuestRequest
import com.example.multicam.api.dto.LoginRequest
import com.example.multicam.api.dto.RegisterRequest
import com.example.multicam.sevice.getDeviceUuid
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle    : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class RegistrationViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("auth", Context.MODE_PRIVATE)

    var state by mutableStateOf<AuthState>(AuthState.Idle)
        private set

    /** Сохраняет токен в память и в SharedPreferences */
    private fun persistToken(token: String) {
        RetrofitClient.authToken = token
        prefs.edit().putString("auth_token", token).apply()
    }

    fun register(username: String, email: String, password: String) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            state = AuthState.Error("Заполните все поля")
            return
        }
        viewModelScope.launch {
            state = AuthState.Loading
            try {
                // 1. Регистрация — бэк возвращает "Signup successful"
                val regResp = RetrofitClient.authApi.register(
                    RegisterRequest(username, email, password)
                )
                if (!regResp.isSuccessful) {
                    state = AuthState.Error("Ошибка регистрации: ${regResp.code()}")
                    return@launch
                }

                // 2. Логин — бэк возвращает сырую JWT-строку
                val loginResp = RetrofitClient.authApi.signin(
                    LoginRequest(email, password)
                )
                if (loginResp.isSuccessful) {
                    val token = loginResp.body()
                    if (!token.isNullOrBlank()) {
                        persistToken(token)
                        state = AuthState.Success
                    } else {
                        state = AuthState.Error("Сервер вернул пустой токен")
                    }
                } else {
                    state = AuthState.Error("Ошибка входа: ${loginResp.code()}")
                }

            } catch (e: Exception) {
                state = AuthState.Error(e.localizedMessage ?: "Ошибка сети")
            }
        }
    }

    fun loginAsGuest(context: Context) {
        viewModelScope.launch {
            state = AuthState.Loading
            try {
                val uuid = getDeviceUuid(context)
                val resp = RetrofitClient.authApi.registerGuest(GuestRequest(uuid))
                if (resp.isSuccessful) {
                    // Бэк возвращает JWT-строку напрямую
                    val token = resp.body()
                    if (!token.isNullOrBlank()) {
                        persistToken(token)
                        state = AuthState.Success
                    } else {
                        state = AuthState.Error("Сервер вернул пустой токен")
                    }
                } else {
                    state = AuthState.Error("Ошибка сервера: ${resp.code()}")
                }
            } catch (e: Exception) {
                state = AuthState.Error(e.localizedMessage ?: "Ошибка подключения")
            }
        }
    }

    fun clearError() {
        if (state is AuthState.Error) state = AuthState.Idle
    }
}