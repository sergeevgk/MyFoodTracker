package com.example.myfoodtracker.data.db

import androidx.room3.ColumnInfo

// Flat query projection, data layer only. Never returned past the repository.
data class FoodSearchRow(
    val id: Long,
    val name: String,
    val brand: String?,
    val barcode: String?,
    @ColumnInfo(name = "is_custom") val isCustom: Int,
    val calories: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double,
    @ColumnInfo(name = "carbs_g") val carbsG: Double,
    @ColumnInfo(name = "fat_g") val fatG: Double,
    @ColumnInfo(name = "fiber_g") val fiberG: Double,
    @ColumnInfo(name = "sugar_g") val sugarG: Double,
    @ColumnInfo(name = "sodium_mg") val sodiumMg: Double,
)
