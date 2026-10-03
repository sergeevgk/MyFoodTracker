package com.example.myfoodtracker.domain.repository

import com.example.myfoodtracker.domain.model.FoodItem

interface FoodCatalogRepository {
    fun search(query: String, limit: Int = 30): List<FoodItem>
}
