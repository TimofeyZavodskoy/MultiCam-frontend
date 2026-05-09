package com.example.multicam.api

import com.example.multicam.api.dto.AuthResponse
import com.example.multicam.api.dto.GuestRequest
import com.example.multicam.api.dto.LoginRequest
import com.example.multicam.api.dto.RegisterRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/signup/guest")
    suspend fun registerGuest(@Body request: GuestRequest): Response<AuthResponse>

    @POST("auth/signup/save")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/signin")
    suspend fun signin(@Body request: LoginRequest): Response<AuthResponse>
}