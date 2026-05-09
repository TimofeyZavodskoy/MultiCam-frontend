package com.example.multicam.ui.screen

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.multicam.model.FavoriteCategory
import com.example.multicam.model.FavoriteItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class FavoritesViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private val gson  = Gson()

    var favorites by mutableStateOf<List<FavoriteItem>>(emptyList())
        private set

    init { load() }

    private fun load() {
        val json = prefs.getString("items", null) ?: return
        val type = object : TypeToken<List<FavoriteItem>>() {}.type
        favorites = gson.fromJson(json, type) ?: emptyList()
    }

    private fun persist() {
        prefs.edit().putString("items", gson.toJson(favorites)).apply()
    }

    fun add(item: FavoriteItem) {
        if (favorites.none { it.id == item.id }) {
            favorites = listOf(item) + favorites
            persist()
        }
    }

    fun remove(id: String) {
        favorites = favorites.filter { it.id != id }
        persist()
    }

    fun contains(id: String) = favorites.any { it.id == id }

    fun byCategory(cat: FavoriteCategory) = favorites.filter { it.category == cat }
}
