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
        val calorieError: String? = null,
        val proteinError: String? = null,
        val carbError: String? = null,
        val fatError: String? = null,
        val waterError: String? = null,
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
        var calorieErr: String? = null
        var proteinErr: String? = null
        var carbErr: String? = null
        var fatErr: String? = null
        var waterErr: String? = null

        if (trimmedUsername.isEmpty()) {
            usernameErr = "Username cannot be empty"
            hasValidationError = true
        }

        if (passcode.length != 4 || !passcode.all { it.isDigit() }) {
            passcodeErr = "Passcode must be exactly 4 digits"
            hasValidationError = true
        }

        var calorieTarget: Double? = null
        if (!calorieTargetStr.isNullOrBlank()) {
            val parsed = calorieTargetStr.trim().toDoubleOrNull()
            if (parsed == null || parsed < 0.0 || parsed > 10000.0) {
                calorieErr = "Calories must be a valid number between 0 and 10000"
                hasValidationError = true
            } else {
                calorieTarget = parsed
            }
        }

        var proteinTarget: Double? = null
        if (!proteinTargetStr.isNullOrBlank()) {
            val parsed = proteinTargetStr.trim().toDoubleOrNull()
            if (parsed == null || parsed < 0.0 || parsed > 1000.0) {
                proteinErr = "Protein must be a valid number between 0 and 1000"
                hasValidationError = true
            } else {
                proteinTarget = parsed
            }
        }

        var carbTarget: Double? = null
        if (!carbTargetStr.isNullOrBlank()) {
            val parsed = carbTargetStr.trim().toDoubleOrNull()
            if (parsed == null || parsed < 0.0 || parsed > 1000.0) {
                carbErr = "Carbs must be a valid number between 0 and 1000"
                hasValidationError = true
            } else {
                carbTarget = parsed
            }
        }

        var fatTarget: Double? = null
        if (!fatTargetStr.isNullOrBlank()) {
            val parsed = fatTargetStr.trim().toDoubleOrNull()
            if (parsed == null || parsed < 0.0 || parsed > 1000.0) {
                fatErr = "Fat must be a valid number between 0 and 1000"
                hasValidationError = true
            } else {
                fatTarget = parsed
            }
        }

        var waterTarget: Int? = null
        if (!waterTargetStr.isNullOrBlank()) {
            val parsed = waterTargetStr.trim().toIntOrNull()
            if (parsed == null || parsed < 0 || parsed > 20000) {
                waterErr = "Water must be a valid integer between 0 and 20000"
                hasValidationError = true
            } else {
                waterTarget = parsed
            }
        }

        if (hasValidationError) {
            _state.value = ProfileSetupState.Error(
                usernameError = usernameErr,
                passcodeError = passcodeErr,
                calorieError = calorieErr,
                proteinError = proteinErr,
                carbError = carbErr,
                fatError = fatErr,
                waterError = waterErr
            )
            return
        }

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
