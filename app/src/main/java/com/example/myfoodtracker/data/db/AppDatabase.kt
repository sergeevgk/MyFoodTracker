package com.example.myfoodtracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.myfoodtracker.data.dao.UserProfileDao
import com.example.myfoodtracker.data.entity.UserDailyGoalEntity
import com.example.myfoodtracker.data.entity.UserProfileEntity

@Database(
    entities = [
        MealEntryEntity::class,
        FoodEntity::class,
        UserProfileEntity::class,
        UserDailyGoalEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun userProfileDao(): UserProfileDao
}
