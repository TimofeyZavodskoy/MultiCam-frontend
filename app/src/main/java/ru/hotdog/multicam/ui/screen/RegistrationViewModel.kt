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

// Описывает состояние экрана авторизации.
sealed class AuthState {
    // Показывает, что авторизация ещё не выполняется.
    object Idle    : AuthState()
    // Показывает, что auth-запрос сейчас выполняется.
    object Loading : AuthState()
    // Показывает, что авторизация успешно завершена.
    object Success : AuthState()
    // Хранит текст ошибки авторизации для UI.
    data class Error(val message: String) : AuthState()
}

// Управляет регистрацией, входом, гостевым режимом и апгрейдом аккаунта.
class RegistrationViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("auth", Context.MODE_PRIVATE)

    var state by mutableStateOf<AuthState>(AuthState.Idle)
        private set

    // Сохраняет токены и тип пользователя в память приложения и SharedPreferences.
    private fun persistTokens(pair: TokenPair, isGuest: Boolean) {
        RetrofitClient.authToken = pair.accessToken
        prefs.edit()
            .putString("auth_token", pair.accessToken)
            .putString("refresh_token", pair.refreshToken)
            .putBoolean("is_guest", isGuest)
            .apply()
    }

    // Регистрирует пользователя и сразу выполняет вход.
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

    // Выполняет вход по email и паролю после проверки полей.
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

    // Отправляет запрос входа и обновляет состояние по ответу backend.
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

    // Создаёт или восстанавливает гостевую сессию по UUID устройства.
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

    // Апгрейдит гостевой аккаунт до постоянного пользователя.
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

    // Сбрасывает ошибку авторизации в idle-состояние.
    fun clearError() { if (state is AuthState.Error) state = AuthState.Idle }
    // Возвращает состояние авторизации к начальному.
    fun reset()      { state = AuthState.Idle }
}