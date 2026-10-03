package com.example.myfoodtracker.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "catalog_food_nutrients",
    foreignKeys = [ForeignKey(
        entity = CatalogFoodEntity::class,
        parentColumns = ["id"],
        childColumns = ["food_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["food_id"])]
)
data class CatalogFoodNutrientEntity(
    @PrimaryKey @ColumnInfo(name = "food_id") val foodId: Long,
    val calories: Double = 0.0,
    @ColumnInfo(name = "protein_g") val proteinG: Double = 0.0,
    @ColumnInfo(name = "carbs_g") val carbsG: Double = 0.0,
    @ColumnInfo(name = "fat_g") val fatG: Double = 0.0,
    @ColumnInfo(name = "fiber_g") val fiberG: Double = 0.0,
    @ColumnInfo(name = "sugar_g") val sugarG: Double = 0.0,
    @ColumnInfo(name = "sodium_mg") val sodiumMg: Double = 0.0,
)
