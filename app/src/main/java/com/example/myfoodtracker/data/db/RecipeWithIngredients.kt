package com.example.myfoodtracker.data.db

import androidx.room3.Embedded
import androidx.room3.Relation

data class RecipeWithIngredients(
    @Embedded val recipe: RecipeEntity,
    @Relation(
        entity = RecipeIngredientEntity::class,
        parentColumns = ["id"],
        entityColumns = ["recipe_id"]
    )
    val ingredients: List<RecipeIngredientEntity>
)
