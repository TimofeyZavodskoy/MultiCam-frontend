package com.example.multicam.ui.screen

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multicam.api.RetrofitClient
import com.example.multicam.api.dto.OCRResponse
import com.example.multicam.api.dto.SaveRequest
import com.example.multicam.model.FavoriteCategory
import com.example.multicam.model.FavoriteItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val TAG = "FavoritesVM"

class FavoritesViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private val gson  = Gson()

    var favorites by mutableStateOf<List<FavoriteItem>>(emptyList())
        private set

    /** true пока идёт начальная загрузка с бекенда */
    var isSyncing by mutableStateOf(false)
        private set

    init {
        loadLocal()
        if (!RetrofitClient.authToken.isNullOrBlank()) {
            syncFromBackend()
        }
    }

    // ── Локальное хранилище ───────────────────────────────────────────────────

    private fun loadLocal() {
        val json = prefs.getString("items", null) ?: return
        val type = object : TypeToken<List<FavoriteItem>>() {}.type
        favorites = gson.fromJson(json, type) ?: emptyList()
    }

    private fun persist() {
        prefs.edit().putString("items", gson.toJson(favorites)).apply()
    }

    // ── Публичный API ─────────────────────────────────────────────────────────

    /**
     * Добавляет лайк локально, затем синкает на бекенд.
     * После успешного сохранения обновляет [FavoriteItem.backendId].
     */
    fun add(item: FavoriteItem, rawResponse: OCRResponse?, category: FavoriteCategory) {
        if (favorites.any { it.id == item.id }) return

        favorites = listOf(item) + favorites
        persist()

        rawResponse ?: return
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.saveLike(
                    SaveRequest(clientJson = rawResponse, category = category.name)
                )
                if (resp.isSuccessful) {
                    val backendId = resp.body()?.id
                    if (backendId != null) {
                        // Сохраняем backendId — понадобится при удалении
                        favorites = favorites.map {
                            if (it.id == item.id) it.copy(backendId = backendId) else it
                        }
                        persist()
                        Log.d(TAG, "Like synced → backendId=$backendId")
                    }
                } else {
                    Log.w(TAG, "saveLike HTTP ${resp.code()}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "saveLike failed (non-critical): ${e.message}")
            }
        }
    }

    /**
     * Удаляет лайк локально и отправляет DELETE на бекенд (если есть backendId).
     */
    fun remove(id: String) {
        val item = favorites.find { it.id == id }
        favorites = favorites.filter { it.id != id }
        persist()

        val backendId = item?.backendId ?: return
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.deleteLike(backendId)
                if (resp.isSuccessful) {
                    Log.d(TAG, "Like deleted on backend: backendId=$backendId")
                } else {
                    Log.w(TAG, "deleteLike HTTP ${resp.code()}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "deleteLike failed (non-critical): ${e.message}")
            }
        }
    }

    fun contains(id: String) = favorites.any { it.id == id }

    fun byCategory(cat: FavoriteCategory) = favorites.filter { it.category == cat }

    // ── Синк с бекендом ───────────────────────────────────────────────────────

    /**
     * Подгружает лайки с бекенда и делает их источником правды.
     * Вызывается при старте (если залогинен) и может вызываться вручную.
     */
    fun syncFromBackend() {
        viewModelScope.launch {
            isSyncing = true
            try {
                val resp = RetrofitClient.api.getLikes()
                if (resp.isSuccessful) {
                    val remote = resp.body() ?: emptyList()
                    favorites = remote.mapNotNull { dto ->
                        val cat = runCatching {
                            FavoriteCategory.valueOf(dto.category ?: "")
                        }.getOrNull() ?: return@mapNotNull null

                        // Пробуем распарсить jsonData для извлечения КБЖУ и текста
                        val ocr = runCatching {
                            gson.fromJson(dto.jsonData, OCRResponse::class.java)
                        }.getOrNull()

                        FavoriteItem(
                            id         = "backend_${dto.id}",
                            backendId  = dto.id,
                            timestamp  = parseTimestamp(dto.createdAt),
                            category   = cat,
                            title      = buildTitleFromOcr(ocr, cat),
                            resultText = ocr?.solution ?: ocr?.result ?: ocr?.content ?: ocr?.description,
                            calories   = ocr?.calories,
                            proteins   = ocr?.proteins,
                            fats       = ocr?.fats,
                            carbs      = ocr?.carbs
                        )
                    }
                    persist()
                    Log.d(TAG, "Synced ${favorites.size} likes from backend")
                } else {
                    Log.w(TAG, "getLikes HTTP ${resp.code()}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "syncFromBackend failed, using local cache: ${e.message}")
            } finally {
                isSyncing = false
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildTitleFromOcr(ocr: OCRResponse?, cat: FavoriteCategory): String {
        if (ocr == null) return cat.displayName
        return when (cat) {
            FavoriteCategory.FOOD          -> "🍽 ${ocr.calories ?: "?"} ккал"
            FavoriteCategory.OBJECT_SEARCH -> "🔍 ${ocr.detections?.firstOrNull()?.label ?: "Объект"}"
            FavoriteCategory.IMAGES        -> "📸 ${ocr.detections?.firstOrNull()?.label ?: "Изображение"}"
            FavoriteCategory.PHYSICS       -> "⚛️ ${ocr.result?.lines()?.firstOrNull { it.isNotBlank() }?.take(50)?.trim() ?: "Физика"}"
            FavoriteCategory.CHEMISTRY     -> "🧪 ${ocr.result?.lines()?.firstOrNull { it.isNotBlank() }?.take(50)?.trim() ?: "Химия"}"
            else -> ocr.result?.lines()?.firstOrNull { it.isNotBlank() }?.take(50)?.trim() ?: cat.displayName
        }
    }

    private fun parseTimestamp(createdAt: String?): Long {
        if (createdAt == null) return System.currentTimeMillis()
        return runCatching {
            // формат от Spring: "2026-05-10T12:34:56" или массив [2026,5,10,12,34,56]
            java.time.LocalDateTime.parse(createdAt)
                .toInstant(java.time.ZoneOffset.UTC)
                .toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
    }
}