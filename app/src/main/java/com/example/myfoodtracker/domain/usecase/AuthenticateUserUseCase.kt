package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.domain.repository.UserRepository

class AuthenticateUserUseCase(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(profileId: String, passcode: String): Result<UserProfile> = runCatching {
        if (passcode.length != 4 || !passcode.all { it.isDigit() }) {
            throw IllegalArgumentException("Incorrect passcode")
        }

        val profile = userRepository.getProfileById(profileId)
            ?: throw IllegalArgumentException("Profile not found")

        val isPasscodeValid = userRepository.verifyPasscode(profileId, passcode)
        if (!isPasscodeValid) {
            throw IllegalArgumentException("Incorrect passcode")
        }

        sessionRepository.setActiveProfile(profile)
        profile
    }
}
