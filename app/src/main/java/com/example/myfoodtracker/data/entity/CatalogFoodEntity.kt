package com.example.myfoodtracker.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "catalog_foods",
    indices = [Index(value = ["name"]), Index(value = ["barcode"])]
)
data class CatalogFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String?,
    val barcode: String?,
    @ColumnInfo(name = "base_serving_size") val baseServingSize: Double = 100.0,
    @ColumnInfo(name = "base_serving_unit") val baseServingUnit: String = "g",
    @ColumnInfo(name = "is_custom") val isCustom: Int = 0,
    @ColumnInfo(name = "is_deleted") val isDeleted: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
)
