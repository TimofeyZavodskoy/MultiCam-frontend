package ru.hotdog.multicam.ui.screen

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.hotdog.multicam.api.RetrofitClient
import ru.hotdog.multicam.api.dto.GuestRequest
import ru.hotdog.multicam.api.dto.LoginRequest
import ru.hotdog.multicam.api.dto.RegisterRequest
import ru.hotdog.multicam.api.dto.TokenPair
import ru.hotdog.multicam.sevice.getDeviceUuid

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

    private fun persistTokens(pair: TokenPair, isGuest: Boolean) {
        RetrofitClient.authToken = pair.accessToken
        prefs.edit()
            .putString("auth_token", pair.accessToken)
            .putString("refresh_token", pair.refreshToken)
            .putBoolean("is_guest", isGuest)
            .apply()
    }

    // ── Регистрация ───────────────────────────────────────────────────────────

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
                doLogin(email, password)
            } catch (e: Exception) {
                state = AuthState.Error(e.localizedMessage ?: "Ошибка сети")
            }
        }
    }

    // ── Вход ──────────────────────────────────────────────────────────────────

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            state = AuthState.Error("Введите почту и пароль")
            return
        }
        viewModelScope.launch {
            state = AuthState.Loading
            try {
                doLogin(email, password)
            } catch (e: Exception) {
                state = AuthState.Error(e.localizedMessage ?: "Ошибка сети")
            }
        }
    }

    private suspend fun doLogin(email: String, password: String) {
        val resp = RetrofitClient.authApi.signin(LoginRequest(email, password))
        if (resp.isSuccessful) {
            val pair = resp.body()
            if (pair != null) {
                persistTokens(pair, isGuest = false)
                state = AuthState.Success
            } else {
                state = AuthState.Error("Сервер вернул пустой ответ")
            }
        } else {
            state = AuthState.Error(
                when (resp.code()) {
                    401  -> "Неверная почта или пароль"
                    404  -> "Аккаунт не найден"
                    else -> "Ошибка входа: ${resp.code()}"
                }
            )
        }
    }

    // ── Гостевой вход ─────────────────────────────────────────────────────────

    fun loginAsGuest(context: Context) {
        viewModelScope.launch {
            state = AuthState.Loading
            try {
                val uuid = getDeviceUuid(context)
                val resp = RetrofitClient.authApi.registerGuest(GuestRequest(uuid))
                if (resp.isSuccessful) {
                    val pair = resp.body()
                    if (pair != null) {
                        persistTokens(pair, isGuest = true)
                        state = AuthState.Success
                    } else {
                        state = AuthState.Error("Сервер вернул пустой ответ")
                    }
                } else {
                    state = AuthState.Error("Ошибка сервера: ${resp.code()}")
                }
            } catch (e: Exception) {
                state = AuthState.Error(e.localizedMessage ?: "Ошибка подключения")
            }
        }
    }

    // ── Апгрейд гостя ─────────────────────────────────────────────────────────

    fun upgrade(username: String, email: String, password: String) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            state = AuthState.Error("Заполните все поля")
            return
        }
        viewModelScope.launch {
            state = AuthState.Loading
            try {
                val resp = RetrofitClient.authApi.upgradeAccount(
                    RegisterRequest(username, email, password)
                )
                when {
                    resp.isSuccessful -> {
                        val pair = resp.body()
                        if (pair != null) {
                            persistTokens(pair, isGuest = false)
                            state = AuthState.Success
                        } else {
                            state = AuthState.Error("Сервер вернул пустой ответ")
                        }
                    }
                    resp.code() == 409 -> state = AuthState.Error("Этот email уже занят")
                    resp.code() == 401 -> state = AuthState.Error("Сессия истекла, войдите снова")
                    else               -> state = AuthState.Error("Ошибка сервера: ${resp.code()}")
                }
            } catch (e: Exception) {
                state = AuthState.Error(e.localizedMessage ?: "Ошибка сети")
            }
        }
    }

    fun clearError() { if (state is AuthState.Error) state = AuthState.Idle }
    fun reset()      { state = AuthState.Idle }
}