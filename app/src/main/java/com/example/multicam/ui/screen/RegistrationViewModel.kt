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

    private fun persistToken(token: String, isGuest: Boolean) {
        RetrofitClient.authToken = token
        prefs.edit()
            .putString("auth_token", token)
            .putBoolean("is_guest", isGuest)
            .apply()
    }

    fun register(username: String, email: String, password: String) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            state = AuthState.Error("Заполните все поля")
            return
        }
        viewModelScope.launch {
            state = AuthState.Loading
            try {
                val regResp = RetrofitClient.authApi.register(
                    RegisterRequest(username, email, password)
                )
                if (!regResp.isSuccessful) {
                    state = AuthState.Error("Ошибка регистрации: ${regResp.code()}")
                    return@launch
                }
                doLogin(email, password, isGuest = false)
            } catch (e: Exception) {
                state = AuthState.Error(e.localizedMessage ?: "Ошибка сети")
            }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            state = AuthState.Error("Введите почту и пароль")
            return
        }
        viewModelScope.launch {
            state = AuthState.Loading
            try {
                doLogin(email, password, isGuest = false)
            } catch (e: Exception) {
                state = AuthState.Error(e.localizedMessage ?: "Ошибка сети")
            }
        }
    }

    private suspend fun doLogin(email: String, password: String, isGuest: Boolean) {
        val loginResp = RetrofitClient.authApi.signin(LoginRequest(email, password))
        if (loginResp.isSuccessful) {
            val token = loginResp.body()
            if (!token.isNullOrBlank()) {
                persistToken(token, isGuest)
                state = AuthState.Success
            } else {
                state = AuthState.Error("Сервер вернул пустой токен")
            }
        } else {
            state = AuthState.Error(
                when (loginResp.code()) {
                    401  -> "Неверная почта или пароль"
                    404  -> "Аккаунт не найден"
                    else -> "Ошибка входа: ${loginResp.code()}"
                }
            )
        }
    }

    fun loginAsGuest(context: Context) {
        viewModelScope.launch {
            state = AuthState.Loading
            try {
                val uuid = getDeviceUuid(context)
                val resp = RetrofitClient.authApi.registerGuest(GuestRequest(uuid))
                if (resp.isSuccessful) {
                    val token = resp.body()
                    if (!token.isNullOrBlank()) {
                        persistToken(token, isGuest = true)
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

    /** Сбрасываем состояние при повторном показе экрана (после logout / смены аккаунта). */
    fun reset() {
        state = AuthState.Idle
    }
}