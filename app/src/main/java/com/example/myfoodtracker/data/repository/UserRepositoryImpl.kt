package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.data.dao.UserProfileDao
import com.example.myfoodtracker.data.dao.UserWithGoal
import com.example.myfoodtracker.data.entity.UserDailyGoalEntity
import com.example.myfoodtracker.data.entity.UserProfileEntity
import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.UserRepository

class UserRepositoryImpl(
    private val userProfileDao: UserProfileDao
) : UserRepository {

    override fun createProfile(
        username: String,
        passcodeHash: String,
        dailyGoal: DailyGoal?
    ): UserProfile {
        val userEntity = UserProfileEntity(
            username = username,
            passcodeHash = passcodeHash
        )
        val userId = userProfileDao.insertUser(userEntity)

        val savedGoal = dailyGoal?.let { goal ->
            val goalEntity = UserDailyGoalEntity(
                profileId = userId,
                calorieTarget = goal.calorieTarget,
                proteinTargetGrams = goal.proteinTargetGrams,
                carbTargetGrams = goal.carbTargetGrams,
                fatTargetGrams = goal.fatTargetGrams,
                waterTargetMl = goal.waterTargetMl
            )
            val goalId = userProfileDao.insertGoal(goalEntity)
            goal.copy(id = goalId, profileId = userId)
        }

        return UserProfile(
            id = userId,
            username = username,
            passcodeHash = passcodeHash,
            dailyGoal = savedGoal
        )
    }

    override fun getProfiles(): List<UserProfile> {
        return userProfileDao.getUsersWithGoals().map { it.toDomain() }
    }

    override fun hasProfiles(): Boolean {
        return userProfileDao.getUserCount() > 0
    }

    private fun UserWithGoal.toDomain(): UserProfile {
        return UserProfile(
            id = user.id,
            username = user.username,
            passcodeHash = user.passcodeHash,
            dailyGoal = goal?.toDomain()
        )
    }

    private fun UserDailyGoalEntity.toDomain(): DailyGoal {
        return DailyGoal(
            id = id,
            profileId = profileId,
            calorieTarget = calorieTarget,
            proteinTargetGrams = proteinTargetGrams,
            carbTargetGrams = carbTargetGrams,
            fatTargetGrams = fatTargetGrams,
            waterTargetMl = waterTargetMl
        )
    }
}
