package com.example.myfoodtracker.data.repository

import android.content.Context
import com.example.myfoodtracker.domain.model.FoodEntry
import com.example.myfoodtracker.domain.repository.FoodRepository
import org.json.JSONArray
import java.util.UUID

class FoodRepositoryImpl(private val context: Context) : FoodRepository {
    private val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private var cachedEntries: MutableList<FoodEntry>? = null

    override fun getFoodEntries(): List<FoodEntry> {
        cachedEntries?.let { return it }

        val jsonString = sharedPreferences.getString("text_items", null)
        val list = mutableListOf<FoodEntry>()
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    list.add(FoodEntry(id = UUID.randomUUID().toString(), text = jsonArray.getString(i)))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        cachedEntries = list
        return list
    }

    override fun addFoodEntry(): List<FoodEntry> {
        val entries = getFoodEntries().toMutableList()
        entries.add(FoodEntry(id = UUID.randomUUID().toString(), text = ""))
        cachedEntries = entries
        saveToPrefs(entries)
        return entries
    }

    override fun updateFoodEntry(id: String, newText: String): List<FoodEntry> {
        val entries = getFoodEntries().toMutableList()
        val index = entries.indexOfFirst { it.id == id }
        if (index != -1) {
            entries[index] = entries[index].copy(text = newText)
            cachedEntries = entries
            saveToPrefs(entries)
        }
        return entries
    }

    override fun deleteFoodEntry(id: String): List<FoodEntry> {
        val entries = getFoodEntries().toMutableList()
        val index = entries.indexOfFirst { it.id == id }
        if (index != -1) {
            entries.removeAt(index)
            cachedEntries = entries
            saveToPrefs(entries)
        }
        return entries
    }

    private fun saveToPrefs(entries: List<FoodEntry>) {
        val jsonArray = JSONArray(entries.map { it.text })
        sharedPreferences.edit().putString("text_items", jsonArray.toString()).apply()
    }
}
