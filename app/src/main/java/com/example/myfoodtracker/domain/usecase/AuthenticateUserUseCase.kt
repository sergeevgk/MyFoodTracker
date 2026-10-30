package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.PasscodeAttemptStore
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.domain.repository.UserRepository
import com.example.myfoodtracker.domain.security.LockoutPolicy
import com.example.myfoodtracker.domain.security.PasscodeLockoutException

class AuthenticateUserUseCase(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val attemptStore: PasscodeAttemptStore
) {
    operator fun invoke(profileId: String, passcode: String, rememberDevice: Boolean = false): Result<UserProfile> {
        val result = runCatching {
            val profile = userRepository.getProfileById(profileId)
                ?: throw IllegalArgumentException("Profile not found")

            // Escalating lockout (deferred-work 1.2-D2): persisted per profile so an
            // app restart does not reset it. While wait remains, verification is
            // short-circuited — the KDF is not run and the counter is not changed.
            val attemptState = attemptStore.getFailureState(profileId)
            if (attemptState != null) {
                val remainingWait = LockoutPolicy.remainingWaitSeconds(
                    failureCount = attemptState.first,
                    lastFailureAtMillis = attemptState.second,
                    nowMillis = System.currentTimeMillis()
                )
                if (remainingWait > 0) {
                    throw PasscodeLockoutException(remainingWait.toInt())
                }
            }

            if (passcode.length != 4 || !passcode.all { it.isDigit() }) {
                throw IllegalArgumentException("Incorrect passcode")
            }

            val isPasscodeValid = userRepository.verifyPasscode(profileId, passcode)
            if (!isPasscodeValid) {
                attemptStore.recordFailure(profileId, System.currentTimeMillis())
                throw IllegalArgumentException("Incorrect passcode")
            }

            // Successful login resets the lockout counter.
            runCatching { attemptStore.reset(profileId) }

            profile
        }

        // Session activation is best-effort: a storage failure must not turn a valid
        // passcode into an authentication failure.
        result.getOrNull()?.let { profile ->
            runCatching { sessionRepository.setActiveProfile(profile, rememberDevice) }
        }
        return result
    }
}
