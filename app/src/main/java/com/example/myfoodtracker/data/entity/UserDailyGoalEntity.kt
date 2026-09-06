package com.example.myfoodtracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_daily_goals",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profile_id"])]
)
data class UserDailyGoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "profile_id")
    val profileId: Long,
    @ColumnInfo(name = "calorie_target")
    val calorieTarget: Int? = null,
    @ColumnInfo(name = "protein_target_grams")
    val proteinTargetGrams: Int? = null,
    @ColumnInfo(name = "carb_target_grams")
    val carbTargetGrams: Int? = null,
    @ColumnInfo(name = "fat_target_grams")
    val fatTargetGrams: Int? = null,
    @ColumnInfo(name = "water_target_ml")
    val waterTargetMl: Int? = null
)
