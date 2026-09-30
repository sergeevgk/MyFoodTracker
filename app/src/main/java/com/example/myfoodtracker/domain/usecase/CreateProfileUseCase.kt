package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.UserRepository
import com.example.myfoodtracker.domain.security.PasscodeHasher

class CreateProfileUseCase(
    private val userRepository: UserRepository
) {
    operator fun invoke(
        username: String,
        passcode: String,
        calorieTarget: Double? = null,
        proteinTarget: Double? = null,
        carbTarget: Double? = null,
        fatTarget: Double? = null,
        waterTarget: Int? = null
    ): Result<UserProfile> {
        val trimmedUsername = username.trim()
        if (trimmedUsername.isEmpty()) {
            return Result.failure(IllegalArgumentException("Username cannot be empty"))
        }
        if (passcode.length != 4 || !passcode.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Passcode must be exactly 4 digits"))
        }
        if (userRepository.isUsernameTaken(trimmedUsername)) {
            return Result.failure(IllegalArgumentException("Username already taken"))
        }

        val passcodeHash = PasscodeHasher.hashPasscode(passcode)

        val dailyGoal = DailyGoal(
            targetCalories = calorieTarget,
            targetProteinG = proteinTarget,
            targetCarbsG = carbTarget,
            targetFatG = fatTarget,
            targetWaterMl = waterTarget
        )

        val createdProfile = userRepository.createProfile(trimmedUsername, passcodeHash, dailyGoal)
        return Result.success(createdProfile)
    }
}
