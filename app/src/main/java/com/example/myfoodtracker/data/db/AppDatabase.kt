package com.example.myfoodtracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MealEntryEntity::class, FoodEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
}
