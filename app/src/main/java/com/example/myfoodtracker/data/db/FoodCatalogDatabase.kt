package com.example.myfoodtracker.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.example.myfoodtracker.data.dao.FoodCatalogDao
import com.example.myfoodtracker.data.entity.CatalogFoodEntity
import com.example.myfoodtracker.data.entity.CatalogFoodFtsEntity
import com.example.myfoodtracker.data.entity.CatalogFoodNutrientEntity
import com.example.myfoodtracker.data.entity.CatalogFoodServingUnitEntity

@Database(
    entities = [
        CatalogFoodEntity::class,
        CatalogFoodNutrientEntity::class,
        CatalogFoodServingUnitEntity::class,
        CatalogFoodFtsEntity::class,
    ],
    version = 1,
    exportSchema = true
)
abstract class FoodCatalogDatabase : RoomDatabase() {
    abstract fun foodCatalogDao(): FoodCatalogDao
}
