package com.example.myfoodtracker.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.usecase.CreateProfileUseCase

sealed class ProfileSetupState {
    object Idle : ProfileSetupState()
    object Loading : ProfileSetupState()
    data class Success(val userProfile: UserProfile) : ProfileSetupState()
    data class Error(
        val usernameError: String? = null,
        val passcodeError: String? = null,
        val generalError: String? = null
    ) : ProfileSetupState()
}

class ProfileSetupViewModel(
    private val createProfileUseCase: CreateProfileUseCase
) : ViewModel() {

    private val _state = MutableLiveData<ProfileSetupState>(ProfileSetupState.Idle)
    val state: LiveData<ProfileSetupState> = _state

    fun createProfile(
        username: String,
        passcode: String,
        calorieTargetStr: String? = null,
        proteinTargetStr: String? = null,
        carbTargetStr: String? = null,
        fatTargetStr: String? = null,
        waterTargetStr: String? = null
    ) {
        val trimmedUsername = username.trim()
        var hasValidationError = false
        var usernameErr: String? = null
        var passcodeErr: String? = null

        if (trimmedUsername.isEmpty()) {
            usernameErr = "Username cannot be empty"
            hasValidationError = true
        }

        if (passcode.length < 4) {
            passcodeErr = "Passcode must be at least 4 digits"
            hasValidationError = true
        }

        if (hasValidationError) {
            _state.value = ProfileSetupState.Error(
                usernameError = usernameErr,
                passcodeError = passcodeErr
            )
            return
        }

        val calorieTarget = calorieTargetStr?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
        val proteinTarget = proteinTargetStr?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
        val carbTarget = carbTargetStr?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
        val fatTarget = fatTargetStr?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
        val waterTarget = waterTargetStr?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()

        _state.value = ProfileSetupState.Loading

        val result = createProfileUseCase(
            username = trimmedUsername,
            passcode = passcode,
            calorieTarget = calorieTarget,
            proteinTarget = proteinTarget,
            carbTarget = carbTarget,
            fatTarget = fatTarget,
            waterTarget = waterTarget
        )

        result.fold(
            onSuccess = { profile ->
                _state.value = ProfileSetupState.Success(profile)
            },
            onFailure = { throwable ->
                _state.value = ProfileSetupState.Error(generalError = throwable.message)
            }
        )
    }

    fun resetState() {
        _state.value = ProfileSetupState.Idle
    }
}
