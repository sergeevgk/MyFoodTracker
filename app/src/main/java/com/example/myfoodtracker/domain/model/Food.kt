package com.example.myfoodtracker.domain.model

data class Food(
    val name: String,
    val weight: Double,
    val calories: Double,
    val carbs: Double,
    val fat: Double,
    val protein: Double,
    val fiber: Double
)
