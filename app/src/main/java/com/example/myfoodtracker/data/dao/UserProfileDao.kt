package com.example.myfoodtracker.data.dao

import androidx.room.Embedded
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.example.myfoodtracker.data.entity.UserDailyGoalEntity
import com.example.myfoodtracker.data.entity.UserProfileEntity

data class UserWithGoal(
    @Embedded val user: UserProfileEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "profile_id"
    )
    val goal: UserDailyGoalEntity?
)

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUser(user: UserProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGoal(goal: UserDailyGoalEntity): Long

    @Transaction
    @Query("SELECT * FROM users")
    fun getUsersWithGoals(): List<UserWithGoal>

    @Query("SELECT COUNT(*) FROM users")
    fun getUserCount(): Int
}
