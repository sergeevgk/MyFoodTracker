package com.example.myfoodtracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
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
    ]
)
data class UserDailyGoalEntity(
    @PrimaryKey
    @ColumnInfo(name = "profile_id")
    val profileId: String,
    @ColumnInfo(name = "target_calories")
    val targetCalories: Double? = null,
    @ColumnInfo(name = "target_protein_g")
    val targetProteinG: Double? = null,
    @ColumnInfo(name = "target_carbs_g")
    val targetCarbsG: Double? = null,
    @ColumnInfo(name = "target_fat_g")
    val targetFatG: Double? = null,
    @ColumnInfo(name = "target_water_ml")
    val targetWaterMl: Int? = null
)
