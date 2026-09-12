package com.example.myfoodtracker.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.usecase.CreateProfileUseCase
import com.example.myfoodtracker.domain.usecase.FakeUserRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProfileSetupViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var createProfileUseCase: CreateProfileUseCase
    private lateinit var viewModel: ProfileSetupViewModel

    @Before
    fun setUp() {
        fakeUserRepository = FakeUserRepository()
        createProfileUseCase = CreateProfileUseCase(fakeUserRepository)
        viewModel = ProfileSetupViewModel(createProfileUseCase)
    }

    @Test
    fun createProfile_emptyUsername_setsErrorState() {
        viewModel.createProfile(username = "", passcode = "1234")

        val state = viewModel.state.value
        assertTrue(state is ProfileSetupState.Error)
        val errorState = state as ProfileSetupState.Error
        assertEquals("Username cannot be empty", errorState.usernameError)
    }

    @Test
    fun createProfile_shortPasscode_setsErrorState() {
        viewModel.createProfile(username = "Georgii", passcode = "12")

        val state = viewModel.state.value
        assertTrue(state is ProfileSetupState.Error)
        val errorState = state as ProfileSetupState.Error
        assertEquals("Passcode must be exactly 4 digits", errorState.passcodeError)
    }

    @Test
    fun createProfile_nonNumericPasscode_setsErrorState() {
        viewModel.createProfile(username = "Georgii", passcode = "12ab")

        val state = viewModel.state.value
        assertTrue(state is ProfileSetupState.Error)
        val errorState = state as ProfileSetupState.Error
        assertEquals("Passcode must be exactly 4 digits", errorState.passcodeError)
    }

    @Test
    fun createProfile_invalidTarget_setsErrorState() {
        viewModel.createProfile(
            username = "Georgii",
            passcode = "1234",
            calorieTargetStr = "invalid"
        )

        val state = viewModel.state.value
        assertTrue(state is ProfileSetupState.Error)
        val errorState = state as ProfileSetupState.Error
        assertEquals("Calories must be a valid number between 0 and 10000", errorState.calorieError)
    }

    @Test
    fun createProfile_negativeTarget_setsErrorState() {
        viewModel.createProfile(
            username = "Georgii",
            passcode = "1234",
            waterTargetStr = "-100"
        )

        val state = viewModel.state.value
        assertTrue(state is ProfileSetupState.Error)
        val errorState = state as ProfileSetupState.Error
        assertEquals("Water must be a valid integer between 0 and 20000", errorState.waterError)
    }

    @Test
    fun createProfile_duplicateUsername_setsGeneralError() {
        fakeUserRepository.createProfile("Georgii", "hashed_pin", DailyGoal())

        viewModel.createProfile(username = "Georgii", passcode = "1234")

        val state = viewModel.state.value
        assertTrue(state is ProfileSetupState.Error)
        val errorState = state as ProfileSetupState.Error
        assertEquals("Username already taken", errorState.generalError)
    }

    @Test
    fun createProfile_validInputs_setsSuccessState() {
        viewModel.createProfile(
            username = "Georgii",
            passcode = "1234",
            calorieTargetStr = "2000"
        )

        val state = viewModel.state.value
        assertTrue(state is ProfileSetupState.Success)
        val successState = state as ProfileSetupState.Success
        assertEquals("Georgii", successState.userProfile.username)
        assertEquals(2000.0, successState.userProfile.dailyGoal?.targetCalories)
    }
}
