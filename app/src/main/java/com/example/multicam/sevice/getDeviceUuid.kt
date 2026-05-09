package com.example.multicam.sevice

import android.content.Context
import android.provider.Settings

fun getDeviceUuid(context: Context): String {
    return Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    )
}