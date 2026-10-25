package com.example.myfoodtracker.data.db

import androidx.room3.ColumnInfo

// Flat query projection, data layer only. Never returned past the repository.
data class RecipeIngredientLinkRow(
    val id: Long,
    @ColumnInfo(name = "catalog_food_id") val catalogFoodId: Long
)
