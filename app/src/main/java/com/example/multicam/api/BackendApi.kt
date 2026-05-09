package com.example.multicam.api

import com.example.multicam.api.dto.OCRResponse
import com.example.multicam.api.dto.SaveRequest
import com.example.multicam.api.dto.SavedResultDto
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface BackendApi {

    @Multipart
    @POST("api/ocr/process")
    suspend fun processImage(
        @Part image: MultipartBody.Part
    ): OCRResponse

    /**
     * Сохраняет лайк. Возвращает сохранённую запись с id —
     * клиент использует его для последующего удаления.
     */
    @POST("api/save/like")
    suspend fun saveLike(@Body request: SaveRequest): Response<SavedResultDto>

    /**
     * Удаляет лайк по id записи в БД.
     */
    @DELETE("api/save/like/{id}")
    suspend fun deleteLike(@Path("id") id: Long): Response<String>

    /**
     * Возвращает все лайки текущего пользователя.
     */
    @GET("api/save/likes/all")
    suspend fun getLikes(): Response<List<SavedResultDto>>
}

object RetrofitClient {
    var authToken: String? = null

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().apply {
                authToken?.let { addHeader("Authorization", "Bearer $it") }
            }.build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(1200, TimeUnit.SECONDS)
        .readTimeout(3000, TimeUnit.SECONDS)
        .writeTimeout(1200, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://192.168.0.15:8080/")
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(GsonConverterFactory.create())
        .client(httpClient)
        .build()

    val api: BackendApi = retrofit.create(BackendApi::class.java)
    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
}