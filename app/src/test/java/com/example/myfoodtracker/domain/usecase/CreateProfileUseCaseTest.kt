package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.UserRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

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
            calorieTarget = 2000.0,
            proteinTarget = 150.0
        )

        assertTrue(result.isSuccess)
        val profile = result.getOrNull()
        assertNotNull(profile)
        assertEquals("Georgii", profile?.username)
        assertNotEquals("1234", profile?.passcodeHash)
        assertTrue(profile?.passcodeHash?.contains(":") == true)
        assertNotNull(profile?.dailyGoal)
        assertEquals(2000.0, profile?.dailyGoal?.targetCalories)
        assertEquals(150.0, profile?.dailyGoal?.targetProteinG)
        assertNull(profile?.dailyGoal?.targetCarbsG)
        assertNull(profile?.dailyGoal?.targetWaterMl)
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
    fun invoke_duplicateUsername_returnsFailure() {
        createProfileUseCase(username = "Georgii", passcode = "1234")
        val duplicateResult = createProfileUseCase(username = "Georgii", passcode = "5678")

        assertTrue(duplicateResult.isFailure)
        assertEquals("Username already taken", duplicateResult.exceptionOrNull()?.message)
    }

    @Test
    fun invoke_shortPasscode_returnsFailure() {
        val result = createProfileUseCase(
            username = "Georgii",
            passcode = "123"
        )

        assertTrue(result.isFailure)
        assertEquals("Passcode must be exactly 4 digits", result.exceptionOrNull()?.message)
    }

    @Test
    fun invoke_longPasscode_returnsFailure() {
        val result = createProfileUseCase(
            username = "Georgii",
            passcode = "12345"
        )

        assertTrue(result.isFailure)
        assertEquals("Passcode must be exactly 4 digits", result.exceptionOrNull()?.message)
    }

    @Test
    fun invoke_nonNumericPasscode_returnsFailure() {
        val result = createProfileUseCase(
            username = "Georgii",
            passcode = "abcd"
        )

        assertTrue(result.isFailure)
        assertEquals("Passcode must be exactly 4 digits", result.exceptionOrNull()?.message)
    }

    @Test
    fun invoke_noGoals_createsProfileWithBlankGoals() {
        val result = createProfileUseCase(
            username = "Georgii",
            passcode = "1234"
        )

        assertTrue(result.isSuccess)
        val profile = result.getOrNull()
        assertNotNull(profile)
        assertNotNull(profile?.dailyGoal)
        assertNull(profile?.dailyGoal?.targetCalories)
        assertNull(profile?.dailyGoal?.targetProteinG)
        assertNull(profile?.dailyGoal?.targetCarbsG)
        assertNull(profile?.dailyGoal?.targetFatG)
        assertNull(profile?.dailyGoal?.targetWaterMl)
    }
}

class FakeUserRepository : UserRepository {
    private val profiles = mutableListOf<UserProfile>()

    override fun createProfile(username: String, passcodeHash: String, dailyGoal: DailyGoal): UserProfile {
        val userId = UUID.randomUUID().toString()
        val profile = UserProfile(
            id = userId,
            username = username,
            passcodeHash = passcodeHash,
            dailyGoal = dailyGoal.copy(profileId = userId)
        )
        profiles.add(profile)
        return profile
    }

    override fun getProfiles(): List<UserProfile> = profiles

    override fun hasProfiles(): Boolean = profiles.isNotEmpty()

    override fun isUsernameTaken(username: String): Boolean {
        return profiles.any { it.username.equals(username, ignoreCase = true) }
    }
}
