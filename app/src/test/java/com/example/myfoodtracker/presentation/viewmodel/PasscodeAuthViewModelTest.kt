package com.example.myfoodtracker.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.security.PasscodeHasher
import com.example.myfoodtracker.domain.usecase.AuthenticateUserUseCase
import com.example.myfoodtracker.domain.usecase.FakeAuthUserRepository
import com.example.myfoodtracker.domain.usecase.FakeSessionRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PasscodeAuthViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var fakeUserRepository: FakeAuthUserRepository
    private lateinit var fakeSessionRepository: FakeSessionRepository
    private lateinit var authenticateUserUseCase: AuthenticateUserUseCase
    private lateinit var viewModel: PasscodeAuthViewModel

    private lateinit var user1: UserProfile
    private lateinit var user2: UserProfile

    @Before
    fun setUp() {
        fakeUserRepository = FakeAuthUserRepository()
        fakeSessionRepository = FakeSessionRepository()
        authenticateUserUseCase = AuthenticateUserUseCase(fakeUserRepository, fakeSessionRepository)
        viewModel = PasscodeAuthViewModel(fakeUserRepository, authenticateUserUseCase)
    }

    @Test
    fun loadProfiles_noProfiles_emitsNoProfilesState() {
        viewModel.loadProfiles()

        val state = viewModel.state.value
        assertTrue(state is PasscodeAuthState.NoProfiles)
    }

    @Test
    fun loadProfiles_withProfiles_emitsContentWithFirstProfileSelected() {
        user1 = fakeUserRepository.createProfile("Georgii", PasscodeHasher.hashPasscode("1234"), DailyGoal())
        user2 = fakeUserRepository.createProfile("Alex", PasscodeHasher.hashPasscode("5678"), DailyGoal())

        viewModel.loadProfiles()

        val state = viewModel.state.value
        assertTrue(state is PasscodeAuthState.Content)
        val content = state as PasscodeAuthState.Content
        assertEquals(2, content.profiles.size)
        assertEquals(user1.id, content.selectedProfile?.id)
        assertNull(content.passcodeError)
    }

    @Test
    fun selectProfile_changesSelectedProfileAndClearsError() {
        user1 = fakeUserRepository.createProfile("Georgii", PasscodeHasher.hashPasscode("1234"), DailyGoal())
        user2 = fakeUserRepository.createProfile("Alex", PasscodeHasher.hashPasscode("5678"), DailyGoal())
        viewModel.loadProfiles()

        viewModel.authenticate("9999")
        val errorContent = viewModel.state.value as PasscodeAuthState.Content
        assertEquals("Incorrect passcode", errorContent.passcodeError)

        viewModel.selectProfile(user2.id)

        val updatedContent = viewModel.state.value as PasscodeAuthState.Content
        assertEquals(user2.id, updatedContent.selectedProfile?.id)
        assertNull(updatedContent.passcodeError)
    }

    @Test
    fun authenticate_shortPasscode_setsPasscodeError() {
        user1 = fakeUserRepository.createProfile("Georgii", PasscodeHasher.hashPasscode("1234"), DailyGoal())
        viewModel.loadProfiles()

        viewModel.authenticate("12")

        val state = viewModel.state.value
        assertTrue(state is PasscodeAuthState.Content)
        val content = state as PasscodeAuthState.Content
        assertEquals("Incorrect passcode", content.passcodeError)
    }

    @Test
    fun authenticate_incorrectPasscode_setsPasscodeError() {
        user1 = fakeUserRepository.createProfile("Georgii", PasscodeHasher.hashPasscode("1234"), DailyGoal())
        viewModel.loadProfiles()

        viewModel.authenticate("0000")

        val state = viewModel.state.value
        assertTrue(state is PasscodeAuthState.Content)
        val content = state as PasscodeAuthState.Content
        assertEquals("Incorrect passcode", content.passcodeError)
        assertFalse(fakeSessionRepository.isLoggedIn())
    }

    @Test
    fun authenticate_correctPasscode_emitsAuthenticatedState() {
        user1 = fakeUserRepository.createProfile("Georgii", PasscodeHasher.hashPasscode("1234"), DailyGoal())
        viewModel.loadProfiles()

        viewModel.authenticate("1234")

        val state = viewModel.state.value
        assertTrue(state is PasscodeAuthState.Authenticated)
        val authState = state as PasscodeAuthState.Authenticated
        assertEquals(user1.id, authState.profile.id)
        assertTrue(fakeSessionRepository.isLoggedIn())
        assertEquals(user1.id, fakeSessionRepository.getActiveProfileId())
    }
}
