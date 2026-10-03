package com.example.myfoodtracker.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Fts5
import androidx.room3.PrimaryKey

@Entity(tableName = "catalog_foods_fts")
@Fts5(contentEntity = CatalogFoodEntity::class)
data class CatalogFoodFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val name: String,
    val brand: String,
)
