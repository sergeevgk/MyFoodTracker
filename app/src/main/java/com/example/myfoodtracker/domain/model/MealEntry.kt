package com.example.myfoodtracker.domain.model

data class MealEntry(
    val id: String,
    val title: String,
    val date: String,
    val time: String,
    val foods: List<Food> = emptyList()
)
