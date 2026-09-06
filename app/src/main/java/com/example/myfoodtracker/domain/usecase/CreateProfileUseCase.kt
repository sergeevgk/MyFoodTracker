package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.UserRepository
import java.security.MessageDigest

class CreateProfileUseCase(
    private val userRepository: UserRepository
) {
    operator fun invoke(
        username: String,
        passcode: String,
        calorieTarget: Int? = null,
        proteinTarget: Int? = null,
        carbTarget: Int? = null,
        fatTarget: Int? = null,
        waterTarget: Int? = null
    ): Result<UserProfile> {
        val trimmedUsername = username.trim()
        if (trimmedUsername.isEmpty()) {
            return Result.failure(IllegalArgumentException("Username cannot be empty"))
        }
        if (passcode.length < 4) {
            return Result.failure(IllegalArgumentException("Passcode must be at least 4 digits"))
        }

        val passcodeHash = hashPasscode(passcode)

        val hasAnyGoal = calorieTarget != null || proteinTarget != null || carbTarget != null || fatTarget != null || waterTarget != null
        val dailyGoal = if (hasAnyGoal) {
            DailyGoal(
                calorieTarget = calorieTarget,
                proteinTargetGrams = proteinTarget,
                carbTargetGrams = carbTarget,
                fatTargetGrams = fatTarget,
                waterTargetMl = waterTarget
            )
        } else {
            null
        }

        val createdProfile = userRepository.createProfile(trimmedUsername, passcodeHash, dailyGoal)
        return Result.success(createdProfile)
    }

    private fun hashPasscode(passcode: String): String {
        val salt = "MyFoodTrackerSalt"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest((salt + passcode).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
