package ru.hotdog.multicam.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/signup/guest")
    suspend fun registerGuest(@Body request: ru.hotdog.multicam.api.dto.GuestRequest): Response<ru.hotdog.multicam.api.dto.TokenPair>

    @POST("auth/signup/save")
    suspend fun register(@Body request: ru.hotdog.multicam.api.dto.RegisterRequest): Response<String>

    @POST("auth/signin")
    suspend fun signin(@Body request: ru.hotdog.multicam.api.dto.LoginRequest): Response<ru.hotdog.multicam.api.dto.TokenPair>

    @POST("auth/upgrade")
    suspend fun upgradeAccount(@Body request: ru.hotdog.multicam.api.dto.RegisterRequest): Response<ru.hotdog.multicam.api.dto.TokenPair>
}