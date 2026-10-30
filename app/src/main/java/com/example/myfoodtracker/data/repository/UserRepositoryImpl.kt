package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.data.dao.UserProfileDao
import com.example.myfoodtracker.data.dao.UserWithGoal
import com.example.myfoodtracker.data.entity.UserDailyGoalEntity
import com.example.myfoodtracker.data.entity.UserProfileEntity
import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.UserRepository
import com.example.myfoodtracker.domain.security.PasscodeHasher
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

    override fun getProfileById(id: String): UserProfile? {
        return userProfileDao.getUserById(id)?.toDomain()
    }

    override fun verifyPasscode(profileId: String, passcode: String): Boolean {
        val user = getProfileById(profileId) ?: return false
        val verified = PasscodeHasher.verifyPasscode(passcode, user.passcodeHash)

        // Transparent legacy → prefixed upgrade (deferred-work 1.2-D1): re-hash and
        // persist only after a successful LEGACY verification. Prefixed hashes are
        // never re-hashed. Best-effort: a failed upgrade write must not fail the
        // login itself — the upgrade retries on the next successful login.
        if (verified && !user.passcodeHash.startsWith(PasscodeHasher.FORMAT_PREFIX)) {
            runCatching {
                userProfileDao.updatePasscodeHash(profileId, PasscodeHasher.hashPasscode(passcode))
            }
        }
        return verified
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
