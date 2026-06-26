package com.example.myfoodtracker.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class MealWithFoods(
    @Embedded val meal: MealEntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "mealId"
    )
    val foods: List<FoodEntity>
)
