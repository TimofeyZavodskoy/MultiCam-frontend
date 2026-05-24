package com.example.multicam.api

import android.content.Context
import com.example.multicam.api.dto.GuestRequest
import com.example.multicam.api.dto.LoginRequest
import com.example.multicam.api.dto.OCRResponse
import com.example.multicam.api.dto.RegisterRequest
import com.example.multicam.api.dto.SaveRequest
import com.example.multicam.api.dto.SavedResultDto
import com.example.multicam.api.dto.TokenPair
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.Response as RetrofitResponse
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface BackendApi {

    @Multipart
    @POST("api/ocr/process")
    suspend fun processImage(
        @Part image: okhttp3.MultipartBody.Part
    ): OCRResponse

    @POST("api/save/like")
    suspend fun saveLike(@Body request: SaveRequest): RetrofitResponse<SavedResultDto>

    @DELETE("api/save/like/{id}")
    suspend fun deleteLike(@Path("id") id: Long): RetrofitResponse<String>

    @GET("api/save/likes/all")
    suspend fun getLikes(): RetrofitResponse<List<SavedResultDto>>
}

// Синхронный интерфейс только для Authenticator — используем Call.execute()
interface SyncAuthApi {
    @POST("auth/refresh")
    fun refreshSync(@Body body: Map<String, String>): Call<TokenPair>

    @POST("auth/signup/guest")
    fun registerGuestSync(@Body request: GuestRequest): Call<TokenPair>
}

object RetrofitClient {
    var authToken: String? = null
    var appContext: Context? = null

    private val syncRetrofit by lazy {
        Retrofit.Builder()
            .baseUrl("http://192.168.0.16:8080/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build())
            .build()
    }

    private val syncAuthApi by lazy { syncRetrofit.create(SyncAuthApi::class.java) }

    /**
     * Authenticator: при 401 пытаемся обновить access token через refresh token.
     * Защита от бесконечного цикла: если запрос уже содержит Authorization
     * и это уже повторная попытка (priorResponse != null) — сдаёмся.
     */
    private val tokenAuthenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.priorResponse != null) return null  // уже пробовали — сдаёмся

            val ctx = appContext ?: return null
            val prefs = ctx.getSharedPreferences("auth", Context.MODE_PRIVATE)
            val isGuest = prefs.getBoolean("is_guest", false)
            val refreshToken = prefs.getString("refresh_token", null)

            val newPair: TokenPair? = try {
                if (!refreshToken.isNullOrBlank()) {
                    // Сначала пробуем refresh для всех типов пользователей
                    val resp = syncAuthApi
                        .refreshSync(mapOf("refreshToken" to refreshToken))
                        .execute()
                    if (resp.isSuccessful) resp.body() else null
                } else if (isGuest) {
                    // Refresh token отсутствует, но это гость — получаем новую пару по UUID
                    val uuid = android.provider.Settings.Secure.getString(
                        ctx.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    )
                    val resp = syncAuthApi.registerGuestSync(GuestRequest(uuid)).execute()
                    if (resp.isSuccessful) resp.body() else null
                } else {
                    null  // обычный юзер без refresh token — не можем помочь
                }
            } catch (e: Exception) {
                null
            }

            if (newPair == null) {
                // Не удалось обновить — очищаем всё, пользователь должен перелогиниться
                prefs.edit()
                    .remove("auth_token")
                    .remove("refresh_token")
                    .putBoolean("is_logged_in", false)
                    .apply()
                authToken = null
                return null
            }

            // Сохраняем новую пару токенов
            authToken = newPair.accessToken
            prefs.edit()
                .putString("auth_token", newPair.accessToken)
                .putString("refresh_token", newPair.refreshToken)
                .apply()

            return response.request.newBuilder()
                .header("Authorization", "Bearer ${newPair.accessToken}")
                .build()
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .authenticator(tokenAuthenticator)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().apply {
                authToken?.let { addHeader("Authorization", "Bearer $it") }
            }.build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://192.168.0.16:8080/")
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(GsonConverterFactory.create())
        .client(httpClient)
        .build()

    val api: BackendApi = retrofit.create(BackendApi::class.java)
    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
}