package com.example.multicam.ui.screen

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.multicam.api.RetrofitClient
import com.example.multicam.api.dto.DetectedObj
import com.example.multicam.api.dto.SearchResult
import com.example.multicam.ui.component.NutritionData
import com.example.multicam.util.compressImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.UUID

class ImageViewModel : ViewModel() {

    var result by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var detections by mutableStateOf<List<DetectedObj>?>(null)
        private set
    var nutritionData by mutableStateOf<NutritionData?>(null)
        private set
    var searchResult by mutableStateOf<List<SearchResult>>(emptyList())
        private set

    // Unique ID for the current result — used to track like state
    var currentResultId by mutableStateOf<String?>(null)
        private set

    private var currentJob: Job? = null

    fun analyzeImage(context: Context, uri: Uri) {
        currentJob?.cancel()

        currentJob = viewModelScope.launch {
            isLoading = true
            error = null
            result = null
            detections = null
            nutritionData = null
            currentResultId = null

            try {
                val bytes = compressImage(context, uri, maxDimension = 1024, quality = 80)
                    ?: throw Exception("Не удалось прочитать или сжать файл")

                val requestBody = bytes.toRequestBody("image/jpeg".toMediaType())
                val part = MultipartBody.Part.createFormData("image", "photo.jpg", requestBody)

                val response = RetrofitClient.api.processImage(part)

                detections    = response.detections
                searchResult  = response.searchResults ?: emptyList()

                val cal  = response.calories
                val pro  = response.proteins
                val fat  = response.fats
                val carb = response.carbs
                if (cal != null || pro != null || fat != null || carb != null) {
                    nutritionData = NutritionData(
                        calories = cal  ?: 0,
                        proteins = pro  ?: 0,
                        fats     = fat  ?: 0,
                        carbs    = carb ?: 0
                    )
                }

                result = response.solution
                    ?: response.result
                            ?: response.content
                            ?: response.description

                // Generate a stable ID for this result so the like button can track state
                currentResultId = UUID.randomUUID().toString()

            } catch (e: SocketTimeoutException) {
                error = "Сервер не отвечает — проверь IP и порт"
            } catch (e: ConnectException) {
                error = "Нет подключения — сервер недоступен"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ImageViewModel", "Ошибка", e)
                error = "Ошибка: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }
}