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
}

class PasscodeAuthViewModel(
    private val userRepository: UserRepository,
    private val authenticateUserUseCase: AuthenticateUserUseCase,
    private val restoreRememberedSessionUseCase: RestoreRememberedSessionUseCase,
    private val attemptStore: PasscodeAttemptStore
) : ViewModel() {

    private val _state = MutableLiveData<PasscodeAuthState>(PasscodeAuthState.Loading)
    val state: LiveData<PasscodeAuthState> = _state

    fun loadProfiles() {
        val profiles = userRepository.getProfiles()

        val rememberedProfile = restoreRememberedSessionUseCase(profiles)
        if (rememberedProfile != null) {
            _state.value = PasscodeAuthState.Authenticated(rememberedProfile)
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
