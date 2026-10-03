package com.example.myfoodtracker.data.db

import androidx.room3.Embedded
import androidx.room3.Relation

data class MealWithFoods(
    @Embedded val meal: MealEntryEntity,
    @Relation(
        entity = FoodEntity::class,
        parentColumns = ["id"],
        entityColumns = ["mealId"]
    )
    val foods: List<FoodEntity>
)
