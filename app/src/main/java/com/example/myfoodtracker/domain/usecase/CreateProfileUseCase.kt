package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.UserRepository
import java.security.MessageDigest
import java.security.SecureRandom

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

        val passcodeHash = hashPasscode(passcode)

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

    private fun hashPasscode(passcode: String): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        val saltHex = saltBytes.joinToString("") { "%02x".format(it) }

        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest((saltHex + passcode).toByteArray(Charsets.UTF_8))
        val hashHex = digest.joinToString("") { "%02x".format(it) }
        return "$saltHex:$hashHex"
    }
}
