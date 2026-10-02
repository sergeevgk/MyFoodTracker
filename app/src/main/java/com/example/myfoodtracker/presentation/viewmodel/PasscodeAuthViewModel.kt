package com.example.myfoodtracker.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.UserRepository
import com.example.myfoodtracker.domain.usecase.AuthenticateUserUseCase
import com.example.myfoodtracker.domain.usecase.RestoreRememberedSessionUseCase

sealed class PasscodeAuthState {
    object Loading : PasscodeAuthState()
    object NoProfiles : PasscodeAuthState()
    data class Content(
        val profiles: List<UserProfile>,
        val selectedProfile: UserProfile?,
        val passcodeError: String? = null,
        val isAuthenticating: Boolean = false
    ) : PasscodeAuthState()
    data class Authenticated(val profile: UserProfile) : PasscodeAuthState()
}

class PasscodeAuthViewModel(
    private val userRepository: UserRepository,
    private val authenticateUserUseCase: AuthenticateUserUseCase,
    private val restoreRememberedSessionUseCase: RestoreRememberedSessionUseCase
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

        _state.value = PasscodeAuthState.Content(
            profiles = profiles,
            selectedProfile = selectedProfile,
            passcodeError = null,
            isAuthenticating = false
        )
    }

    fun selectProfile(profileId: String) {
        val currentState = _state.value as? PasscodeAuthState.Content ?: return
        val profile = currentState.profiles.find { it.id == profileId } ?: return
        _state.value = currentState.copy(
            selectedProfile = profile,
            passcodeError = null
        )
    }

    fun authenticate(passcode: String, rememberDevice: Boolean = false) {
        val currentState = _state.value as? PasscodeAuthState.Content ?: return
        val selectedProfile = currentState.selectedProfile ?: return

        if (passcode.length != 4 || !passcode.all { it.isDigit() }) {
            _state.value = currentState.copy(
                passcodeError = "Incorrect passcode"
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
                _state.value = currentState.copy(
                    isAuthenticating = false,
                    passcodeError = error.message ?: "Incorrect passcode"
                )
            }
        )
    }

    fun clearError() {
        val currentState = _state.value as? PasscodeAuthState.Content ?: return
        if (currentState.passcodeError != null) {
            _state.value = currentState.copy(passcodeError = null)
        }
    }
}
