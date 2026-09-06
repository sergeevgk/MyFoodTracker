package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.UserRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CreateProfileUseCaseTest {

    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var createProfileUseCase: CreateProfileUseCase

    @Before
    fun setUp() {
        fakeUserRepository = FakeUserRepository()
        createProfileUseCase = CreateProfileUseCase(fakeUserRepository)
    }

    @Test
    fun invoke_validInputs_createsProfileSuccessfully() {
        val result = createProfileUseCase(
            username = "Georgii",
            passcode = "1234",
            calorieTarget = 2000,
            proteinTarget = 150
        )

        assertTrue(result.isSuccess)
        val profile = result.getOrNull()
        assertNotNull(profile)
        assertEquals("Georgii", profile?.username)
        assertNotEquals("1234", profile?.passcodeHash)
        assertNotNull(profile?.dailyGoal)
        assertEquals(2000, profile?.dailyGoal?.calorieTarget)
        assertEquals(150, profile?.dailyGoal?.proteinTargetGrams)
        assertNull(profile?.dailyGoal?.carbTargetGrams)
        assertNull(profile?.dailyGoal?.waterTargetMl)
    }

    @Test
    fun invoke_emptyUsername_returnsFailure() {
        val result = createProfileUseCase(
            username = "",
            passcode = "1234"
        )

        assertTrue(result.isFailure)
        assertEquals("Username cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun invoke_shortPasscode_returnsFailure() {
        val result = createProfileUseCase(
            username = "Georgii",
            passcode = "123"
        )

        assertTrue(result.isFailure)
        assertEquals("Passcode must be at least 4 digits", result.exceptionOrNull()?.message)
    }

    @Test
    fun invoke_noGoals_createsProfileWithNullGoal() {
        val result = createProfileUseCase(
            username = "Georgii",
            passcode = "1234"
        )

        assertTrue(result.isSuccess)
        val profile = result.getOrNull()
        assertNotNull(profile)
        assertNull(profile?.dailyGoal)
    }
}

class FakeUserRepository : UserRepository {
    private val profiles = mutableListOf<UserProfile>()
    private var idCounter = 1L

    override fun createProfile(username: String, passcodeHash: String, dailyGoal: DailyGoal?): UserProfile {
        val profile = UserProfile(
            id = idCounter++,
            username = username,
            passcodeHash = passcodeHash,
            dailyGoal = dailyGoal?.copy(profileId = idCounter - 1)
        )
        profiles.add(profile)
        return profile
    }

    override fun getProfiles(): List<UserProfile> = profiles

    override fun hasProfiles(): Boolean = profiles.isNotEmpty()
}
