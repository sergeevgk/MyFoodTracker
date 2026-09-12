package com.example.myfoodtracker.data.dao

import androidx.room.Dao
import androidx.room.Embedded
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
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertUser(user: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertGoal(goal: UserDailyGoalEntity)

    @Transaction
    fun insertUserWithGoal(user: UserProfileEntity, goal: UserDailyGoalEntity) {
        insertUser(user)
        insertGoal(goal)
    }

    @Transaction
    @Query("SELECT * FROM users")
    fun getUsersWithGoals(): List<UserWithGoal>

    @Query("SELECT COUNT(*) FROM users")
    fun getUserCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE LOWER(username) = LOWER(:username))")
    fun isUsernameTaken(username: String): Boolean
}
