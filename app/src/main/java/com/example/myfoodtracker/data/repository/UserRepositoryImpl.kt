package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.data.dao.UserProfileDao
import com.example.myfoodtracker.data.dao.UserWithGoal
import com.example.myfoodtracker.data.entity.UserDailyGoalEntity
import com.example.myfoodtracker.data.entity.UserProfileEntity
import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.UserRepository
import java.util.UUID

class UserRepositoryImpl(
    private val userProfileDao: UserProfileDao
) : UserRepository {

    override fun createProfile(
        username: String,
        passcodeHash: String,
        dailyGoal: DailyGoal
    ): UserProfile {
        val userId = UUID.randomUUID().toString()
        val userEntity = UserProfileEntity(
            id = userId,
            username = username,
            passcodeHash = passcodeHash
        )
        val goalEntity = UserDailyGoalEntity(
            profileId = userId,
            targetCalories = dailyGoal.targetCalories,
            targetProteinG = dailyGoal.targetProteinG,
            targetCarbsG = dailyGoal.targetCarbsG,
            targetFatG = dailyGoal.targetFatG,
            targetWaterMl = dailyGoal.targetWaterMl
        )

        userProfileDao.insertUserWithGoal(userEntity, goalEntity)

        return UserProfile(
            id = userId,
            username = username,
            passcodeHash = passcodeHash,
            createdAt = userEntity.createdAt,
            dailyGoal = dailyGoal.copy(profileId = userId)
        )
    }

    override fun getProfiles(): List<UserProfile> {
        return userProfileDao.getUsersWithGoals().map { it.toDomain() }
    }

    override fun hasProfiles(): Boolean {
        return userProfileDao.getUserCount() > 0
    }

    override fun isUsernameTaken(username: String): Boolean {
        return userProfileDao.isUsernameTaken(username)
    }

    private fun UserWithGoal.toDomain(): UserProfile {
        return UserProfile(
            id = user.id,
            username = user.username,
            passcodeHash = user.passcodeHash,
            createdAt = user.createdAt,
            dailyGoal = goal?.toDomain()
        )
    }

    private fun UserDailyGoalEntity.toDomain(): DailyGoal {
        return DailyGoal(
            profileId = profileId,
            targetCalories = targetCalories,
            targetProteinG = targetProteinG,
            targetCarbsG = targetCarbsG,
            targetFatG = targetFatG,
            targetWaterMl = targetWaterMl
        )
    }
}
