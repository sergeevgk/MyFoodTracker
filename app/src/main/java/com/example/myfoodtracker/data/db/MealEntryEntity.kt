package com.example.myfoodtracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meals")
data class MealEntryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val date: String,
    val time: String
)
