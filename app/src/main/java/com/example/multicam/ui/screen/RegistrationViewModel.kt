package com.example.multicam.ui.screen

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
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

class RegistrationViewModel : ViewModel() {

    var state by mutableStateOf<AuthState>(AuthState.Idle)
        private set

    // В RegistrationViewModel.kt
    fun register(username: String, email: String, password: String) {
        viewModelScope.launch {
            state = AuthState.Loading
            try {
                // 1. Регистрация
                val regResp = RetrofitClient.authApi.register(RegisterRequest(username, email, password))

                if (regResp.isSuccessful) {
                    // 2. Сразу логинимся после регистрации
                    val loginResp = RetrofitClient.authApi.signin(LoginRequest(email, password))

                    if (loginResp.isSuccessful) {
                        val token = loginResp.body()?.token
                        // 3. Сохраняем токен для будущих запросов (например, к OCR)
                        RetrofitClient.authToken = token

                        state = AuthState.Success
                    } else {
                        state = AuthState.Error("Ошибка входа: ${loginResp.code()}")
                    }
                } else {
                    state = AuthState.Error("Ошибка регистрации: ${regResp.code()}")
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
                state = if (resp.isSuccessful) AuthState.Success
                        else AuthState.Error("Ошибка сервера: ${resp.code()}")
            } catch (e: Exception) {
                state = AuthState.Error(e.localizedMessage ?: "Ошибка подключения")
            }
        }
    }

    fun clearError() {
        if (state is AuthState.Error) state = AuthState.Idle
    }
}
