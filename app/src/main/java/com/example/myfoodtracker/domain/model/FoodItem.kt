package com.example.myfoodtracker.domain.model

data class FoodItem(
    val id: Long,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val isCustom: Boolean,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val sugarG: Double,
    val sodiumMg: Double,
)
