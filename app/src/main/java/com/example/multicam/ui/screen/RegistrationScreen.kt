package com.example.multicam.ui.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalTime

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RegistrationScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegistrationViewModel = viewModel()
) {
    val context = LocalContext.current

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

    // Navigate on success
    LaunchedEffect(viewModel.state) {
        if (viewModel.state is AuthState.Success) {
            onLoginSuccess()
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
        Text(
            text  = greeting,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text      = "Добро пожаловать в MultiCam,\nпройдите регистрацию",
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value          = username,
            onValueChange  = { username = it; viewModel.clearError() },
            label          = { Text("Имя пользователя") },
            modifier       = Modifier.fillMaxWidth(),
            singleLine     = true,
            enabled        = !isLoading
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value          = email,
            onValueChange  = { email = it; viewModel.clearError() },
            label          = { Text("Почта") },
            modifier       = Modifier.fillMaxWidth(),
            singleLine     = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled        = !isLoading
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value                  = password,
            onValueChange          = { password = it; viewModel.clearError() },
            label                  = { Text("Пароль") },
            modifier               = Modifier.fillMaxWidth(),
            singleLine             = true,
            visualTransformation   = PasswordVisualTransformation(),
            keyboardOptions        = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled                = !isLoading
        )

        // Error message
        AnimatedVisibility(visible = errorMsg != null) {
            Text(
                text     = errorMsg ?: "",
                color    = MaterialTheme.colorScheme.error,
                style    = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick  = { viewModel.register(username, email, password) },
            modifier = Modifier.fillMaxWidth(),
            shape    = MaterialTheme.shapes.medium,
            enabled  = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Зарегистрироваться", modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        TextButton(
            onClick  = { viewModel.loginAsGuest(context) },
            enabled  = !isLoading
        ) {
            Text("Войти как гость")
        }
    }
}