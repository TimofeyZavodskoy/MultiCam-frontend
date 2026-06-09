package ru.hotdog.multicam.api

import android.content.Context
import ru.hotdog.multicam.api.dto.GuestRequest
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

// Описывает основные HTTP-методы backend API для Retrofit.
interface BackendApi {

    // Отправляет изображение на распознавание и получает результат анализа.
    @Multipart
    @POST("api/ocr/process")
    suspend fun processImage(
        @Part image: okhttp3.MultipartBody.Part
    ): ru.hotdog.multicam.api.dto.OCRResponse

    // Сохраняет результат в избранное на сервере.
    @POST("api/save/like")
    suspend fun saveLike(@Body request: ru.hotdog.multicam.api.dto.SaveRequest): RetrofitResponse<ru.hotdog.multicam.api.dto.SavedResultDto>

    // Удаляет сохранённый результат с сервера по id.
    @DELETE("api/save/like/{id}")
    suspend fun deleteLike(@Path("id") id: Long): RetrofitResponse<String>

    // Загружает все сохранённые результаты пользователя.
    @GET("api/save/likes/all")
    suspend fun getLikes(): RetrofitResponse<List<ru.hotdog.multicam.api.dto.SavedResultDto>>
}

// Выполняет синхронные auth-запросы внутри OkHttp Authenticator.
interface SyncAuthApi {
    // Синхронно обновляет пару токенов по refresh token.
    @POST("auth/refresh")
    fun refreshSync(@Body body: Map<String, String>): Call<ru.hotdog.multicam.api.dto.TokenPair>

    // Синхронно создаёт новую гостевую пару токенов.
    @POST("auth/signup/guest")
    fun registerGuestSync(@Body request: ru.hotdog.multicam.api.dto.GuestRequest): Call<ru.hotdog.multicam.api.dto.TokenPair>
}

// Создаёт и настраивает Retrofit, OkHttp и авторизацию запросов.
object RetrofitClient {
    var authToken: String? = null
    var appContext: Context? = null

    private val syncRetrofit by lazy {
        Retrofit.Builder()
            .baseUrl("multicam-api-production.up.railway.app")
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build())
            .build()
    }

    private val syncAuthApi by lazy { syncRetrofit.create(_root_ide_package_.ru.hotdog.multicam.api.SyncAuthApi::class.java) }

    // При 401 обновляет access token или завершает сессию без бесконечных повторов.
    private val tokenAuthenticator = object : Authenticator {
        // Строит повторный запрос с новым токеном или отменяет авторизацию.
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.priorResponse != null) return null  // уже пробовали — сдаёмся

            val ctx = appContext ?: return null
            val prefs = ctx.getSharedPreferences("auth", Context.MODE_PRIVATE)
            val isGuest = prefs.getBoolean("is_guest", false)
            val refreshToken = prefs.getString("refresh_token", null)

            val newPair: ru.hotdog.multicam.api.dto.TokenPair? = try {
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
                    val resp = syncAuthApi.registerGuestSync(
                        _root_ide_package_.ru.hotdog.multicam.api.dto.GuestRequest(
                            uuid
                        )
                    ).execute()
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
        .baseUrl("multicam-api-production.up.railway.app")
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(GsonConverterFactory.create())
        .client(httpClient)
        .build()

    val api: ru.hotdog.multicam.api.BackendApi = retrofit.create(_root_ide_package_.ru.hotdog.multicam.api.BackendApi::class.java)
    val authApi: ru.hotdog.multicam.api.AuthApi = retrofit.create(_root_ide_package_.ru.hotdog.multicam.api.AuthApi::class.java)
}