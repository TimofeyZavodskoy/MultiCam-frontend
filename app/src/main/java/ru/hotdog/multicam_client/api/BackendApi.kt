package ru.hotdog.multicam_client.api

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit
import ru.hotdog.multicam_client.api.dto.OCRResponse

interface BackendApi {
    @Multipart
    @POST("api/ocr/process")
    suspend fun processImage(
        @Part image: MultipartBody.Part
    ): OCRResponse
}

object RetrofitClient {
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    val api: BackendApi = Retrofit.Builder()
        .baseUrl("http://192.168.0.15:8080")
        .addConverterFactory(GsonConverterFactory.create())
        .client(client)
        .build()
        .create(BackendApi::class.java)
}