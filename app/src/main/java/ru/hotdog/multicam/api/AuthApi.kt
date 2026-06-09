package ru.hotdog.multicam.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// Описывает HTTP-методы авторизации для Retrofit.
interface AuthApi {
    // Регистрирует гостя и возвращает пару токенов.
    @POST("auth/signup/guest")
    suspend fun registerGuest(@Body request: ru.hotdog.multicam.api.dto.GuestRequest): Response<ru.hotdog.multicam.api.dto.TokenPair>

    // Создаёт постоянный аккаунт пользователя.
    @POST("auth/signup/save")
    suspend fun register(@Body request: ru.hotdog.multicam.api.dto.RegisterRequest): Response<String>

    // Выполняет вход пользователя и возвращает пару токенов.
    @POST("auth/signin")
    suspend fun signin(@Body request: ru.hotdog.multicam.api.dto.LoginRequest): Response<ru.hotdog.multicam.api.dto.TokenPair>

    // Превращает гостевой аккаунт в постоянный.
    @POST("auth/upgrade")
    suspend fun upgradeAccount(@Body request: ru.hotdog.multicam.api.dto.RegisterRequest): Response<ru.hotdog.multicam.api.dto.TokenPair>
}