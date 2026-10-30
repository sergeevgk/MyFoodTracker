package com.example.myfoodtracker.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.PasscodeAttemptStore
import com.example.myfoodtracker.domain.repository.UserRepository
import com.example.myfoodtracker.domain.security.LockoutPolicy
import com.example.myfoodtracker.domain.security.PasscodeLockoutException
import com.example.myfoodtracker.domain.usecase.AuthenticateUserUseCase
import com.example.myfoodtracker.domain.usecase.LogoutUseCase
import com.example.myfoodtracker.domain.usecase.RestoreRememberedSessionUseCase

sealed class PasscodeAuthState {
    object Loading : PasscodeAuthState()
    object NoProfiles : PasscodeAuthState()
    data class Content(
        val profiles: List<UserProfile>,
        val selectedProfile: UserProfile?,
        val passcodeError: String? = null,
        val isAuthenticating: Boolean = false,
        /** Non-zero while a failed-attempt lockout wait is running (VM stays Context-free). */
        val lockoutRemainingSeconds: Int = 0
    ) : PasscodeAuthState()
    data class Authenticated(val profile: UserProfile) : PasscodeAuthState()
    /** Remembered session restored on app start: the user must confirm or decline (story 7.2, 3.4-D2). */
    data class RestoredSession(
        val profileId: String,
        val username: String
    ) : PasscodeAuthState()
}

class PasscodeAuthViewModel(
    private val userRepository: UserRepository,
    private val authenticateUserUseCase: AuthenticateUserUseCase,
    private val restoreRememberedSessionUseCase: RestoreRememberedSessionUseCase,
    private val attemptStore: PasscodeAttemptStore,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _state = MutableLiveData<PasscodeAuthState>(PasscodeAuthState.Loading)
    val state: LiveData<PasscodeAuthState> = _state

    fun loadProfiles() {
        val profiles = userRepository.getProfiles()

        val rememberedProfile = restoreRememberedSessionUseCase(profiles)
        if (rememberedProfile != null) {
            // Gated restored session: the user explicitly confirms ("Continue as …")
            // or declines ("Not you?") instead of silently auto-navigating (3.4-D2).
            _state.value = PasscodeAuthState.RestoredSession(
                profileId = rememberedProfile.id,
                username = rememberedProfile.username
            )
            return
        }

        if (profiles.isEmpty()) {
            _state.value = PasscodeAuthState.NoProfiles
            return
        }

        val currentSelected = (state.value as? PasscodeAuthState.Content)?.selectedProfile
        val selectedProfile = profiles.find { it.id == currentSelected?.id } ?: profiles.first()
        val remainingWait = calculateLockoutRemaining(selectedProfile.id)

        _state.value = PasscodeAuthState.Content(
            profiles = profiles,
            selectedProfile = selectedProfile,
            passcodeError = null,
            isAuthenticating = false,
            lockoutRemainingSeconds = remainingWait
        )
    }

    fun selectProfile(profileId: String) {
        val currentState = _state.value as? PasscodeAuthState.Content ?: return
        val profile = currentState.profiles.find { it.id == profileId } ?: return
        val remainingWait = calculateLockoutRemaining(profile.id)
        _state.value = currentState.copy(
            selectedProfile = profile,
            passcodeError = null,
            lockoutRemainingSeconds = remainingWait
        )
    }

    fun authenticate(passcode: String, rememberDevice: Boolean = false) {
        val currentState = _state.value as? PasscodeAuthState.Content ?: return
        val selectedProfile = currentState.selectedProfile ?: return

        val remainingWait = calculateLockoutRemaining(selectedProfile.id)
        if (remainingWait > 0) {
            _state.value = currentState.copy(
                isAuthenticating = false,
                passcodeError = null,
                lockoutRemainingSeconds = remainingWait
            )
            return
        }

        if (passcode.length != 4 || !passcode.all { it.isDigit() }) {
            _state.value = currentState.copy(
                passcodeError = "Incorrect passcode",
                lockoutRemainingSeconds = 0
            )
            return
        }

        _state.value = currentState.copy(isAuthenticating = true, passcodeError = null)

        val result = authenticateUserUseCase(selectedProfile.id, passcode, rememberDevice)
        result.fold(
            onSuccess = { profile ->
                _state.value = PasscodeAuthState.Authenticated(profile)
            },
            onFailure = { error ->
                if (error is PasscodeLockoutException) {
                    // Explicit lockout path: the UI counts down via lockoutRemainingSeconds
                    _state.value = currentState.copy(
                        isAuthenticating = false,
                        passcodeError = null,
                        lockoutRemainingSeconds = error.remainingSeconds
                    )
                } else {
                    _state.value = currentState.copy(
                        isAuthenticating = false,
                        passcodeError = error.message ?: "Incorrect passcode",
                        lockoutRemainingSeconds = 0
                    )
                }
            }
        )
    }

    fun clearError() {
        val currentState = _state.value as? PasscodeAuthState.Content ?: return
        if (currentState.passcodeError != null) {
            _state.value = currentState.copy(passcodeError = null)
        }
    }

    /**
     * "Not you?" decline for a restored session (story 7.2): clears the in-memory
     * session AND the persisted remembered ID, then re-emits the normal profile
     * selection without a full logout round-trip. Reuses the existing [LogoutUseCase].
     */
    fun declineRestoredSession() {
        if (_state.value !is PasscodeAuthState.RestoredSession) return
        logoutUseCase()
        loadProfiles()
    }

    fun clearLockout() {
        val currentState = _state.value as? PasscodeAuthState.Content ?: return
        if (currentState.lockoutRemainingSeconds > 0) {
            _state.value = currentState.copy(lockoutRemainingSeconds = 0)
        }
    }

    private fun calculateLockoutRemaining(profileId: String): Int {
        val attemptState = attemptStore.getFailureState(profileId) ?: return 0
        return LockoutPolicy.remainingWaitSeconds(
            failureCount = attemptState.first,
            lastFailureAtMillis = attemptState.second,
            nowMillis = System.currentTimeMillis()
        ).toInt()
    }
}
