package com.example.myfoodtracker.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "catalog_food_serving_units",
    foreignKeys = [ForeignKey(
        entity = CatalogFoodEntity::class,
        parentColumns = ["id"],
        childColumns = ["food_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["food_id"])]
)
data class CatalogFoodServingUnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "food_id") val foodId: Long,
    @ColumnInfo(name = "unit_name") val unitName: String,
    @ColumnInfo(name = "grams_per_unit") val gramsPerUnit: Double,
)
