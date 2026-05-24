package ru.hotdog.multicam.ui.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalTime

private enum class AuthMode { REGISTER, LOGIN }

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RegistrationScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * true — пользователь пришёл сюда как гость через плашку «Регистрация».
     * Токен гостя ещё активен → вызываем /auth/upgrade вместо /auth/signup.
     */
    isGuestUpgrade: Boolean = false,
    onCancelUpgrade: (() -> Unit)? = null,
    viewModel: RegistrationViewModel = viewModel()
) {
    val context = LocalContext.current

    var mode     by remember { mutableStateOf(AuthMode.REGISTER) }
    var username by remember { mutableStateOf("") }
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val currentHour = remember { LocalTime.now().hour }
    val greeting = remember(currentHour) {
        when (currentHour) {
            in 0..5   -> "Доброй ночи"
            in 6..11  -> "Доброе утро"
            in 12..17 -> "Добрый день"
            else      -> "Добрый вечер"
        }
    }

    // Сбрасываем старый Success (может остаться от гостевого логина),
    // затем наблюдаем за новым через snapshotFlow.
    LaunchedEffect(Unit) {
        viewModel.reset()
        snapshotFlow { viewModel.state }
            .collect { state ->
                if (state is AuthState.Success) onLoginSuccess()
            }
    }

    val isLoading = viewModel.state is AuthState.Loading
    val errorMsg  = (viewModel.state as? AuthState.Error)?.message

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // ── Заголовок ─────────────────────────────────────────────────────────
        if (isGuestUpgrade) {
            // Кнопка «Назад» — вернуться в приложение гостем
            onCancelUpgrade?.let {
                IconButton(
                    onClick  = it,
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                }
            }

            Text(
                text  = "Создать аккаунт",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = "Ваши данные и история лайков\nсохранятся — просто задайте новые данные для входа",
                style     = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text  = greeting,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = if (mode == AuthMode.REGISTER)
                    "Добро пожаловать в MultiCam,\nпройдите регистрацию"
                else
                    "Рады снова вас видеть,\nвойдите в аккаунт",
                style     = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(32.dp))

        // ── Вкладки (только в обычном режиме) ────────────────────────────────
        if (!isGuestUpgrade) {
            TabRow(selectedTabIndex = if (mode == AuthMode.REGISTER) 0 else 1) {
                Tab(
                    selected = mode == AuthMode.REGISTER,
                    onClick  = { mode = AuthMode.REGISTER; viewModel.clearError() },
                    text     = { Text("Регистрация") }
                )
                Tab(
                    selected = mode == AuthMode.LOGIN,
                    onClick  = { mode = AuthMode.LOGIN; viewModel.clearError() },
                    text     = { Text("Войти") }
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── Поля ─────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Имя — только при регистрации или апгрейде
            AnimatedVisibility(visible = isGuestUpgrade || mode == AuthMode.REGISTER) {
                OutlinedTextField(
                    value         = username,
                    onValueChange = { username = it; viewModel.clearError() },
                    label         = { Text("Имя пользователя") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    enabled       = !isLoading
                )
            }

            OutlinedTextField(
                value           = email,
                onValueChange   = { email = it; viewModel.clearError() },
                label           = { Text("Почта") },
                modifier        = Modifier.fillMaxWidth(),
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled         = !isLoading
            )

            OutlinedTextField(
                value                = password,
                onValueChange        = { password = it; viewModel.clearError() },
                label                = { Text("Пароль") },
                modifier             = Modifier.fillMaxWidth(),
                singleLine           = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled              = !isLoading
            )
        }

        // ── Ошибка ────────────────────────────────────────────────────────────
        AnimatedVisibility(visible = errorMsg != null) {
            Text(
                text     = errorMsg ?: "",
                color    = MaterialTheme.colorScheme.error,
                style    = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── Кнопка действия ───────────────────────────────────────────────────
        Button(
            onClick = {
                when {
                    isGuestUpgrade          -> viewModel.upgrade(username, email, password)
                    mode == AuthMode.REGISTER -> viewModel.register(username, email, password)
                    else                    -> viewModel.login(email, password)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape    = MaterialTheme.shapes.medium,
            enabled  = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color       = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = when {
                        isGuestUpgrade          -> "Сохранить и продолжить"
                        mode == AuthMode.REGISTER -> "Зарегистрироваться"
                        else                    -> "Войти"
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        // ── Гостевой вход (только в обычном режиме) ───────────────────────────
        if (!isGuestUpgrade) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { viewModel.loginAsGuest(context) }, enabled = !isLoading) {
                Text("Войти как гость")
            }
        }
    }
}