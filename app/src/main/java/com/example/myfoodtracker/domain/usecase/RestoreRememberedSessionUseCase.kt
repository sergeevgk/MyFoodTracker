package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.SessionRepository

/**
 * Restores the session for the profile remembered on this device, if that profile still exists.
 *
 * A stale remembered ID (profile deleted or database reset) is cleared from device storage only;
 * any live in-memory session is left untouched.
 */
class RestoreRememberedSessionUseCase(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(profiles: List<UserProfile>): UserProfile? {
        val rememberedId = sessionRepository.getRememberedProfileId() ?: return null

        val rememberedProfile = profiles.find { it.id == rememberedId }
        if (rememberedProfile == null) {
            sessionRepository.forgetRememberedProfile()
            return null
        }

        sessionRepository.setActiveProfile(rememberedProfile, rememberDevice = true)
        return rememberedProfile
    }
}
