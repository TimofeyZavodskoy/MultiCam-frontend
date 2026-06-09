package ru.hotdog.multicam.sevice

import android.content.Context
import android.provider.Settings

// Возвращает стабильный Android ID устройства для гостевой авторизации.
fun getDeviceUuid(context: Context): String {
    return Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    )
}