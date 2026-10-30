package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.PasscodeAttemptStore
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.domain.repository.UserRepository
import com.example.myfoodtracker.domain.security.LockoutPolicy
import com.example.myfoodtracker.domain.security.PasscodeHasher
import com.example.myfoodtracker.domain.security.PasscodeLockoutException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class AuthenticateUserUseCaseTest {

    private lateinit var fakeUserRepository: FakeAuthUserRepository
    private lateinit var fakeSessionRepository: FakeSessionRepository
    private lateinit var fakeAttemptStore: FakePasscodeAttemptStore
    private lateinit var authenticateUserUseCase: AuthenticateUserUseCase
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var existingProfile: UserProfile

    @Before
    fun setUp() {
        fakeUserRepository = FakeAuthUserRepository()
        fakeSessionRepository = FakeSessionRepository()
        fakeAttemptStore = FakePasscodeAttemptStore()
        authenticateUserUseCase = AuthenticateUserUseCase(
            fakeUserRepository,
            fakeSessionRepository,
            fakeAttemptStore
        )
        logoutUseCase = LogoutUseCase(fakeSessionRepository)

        existingProfile = fakeUserRepository.createProfile(
            username = "Georgii",
            passcodeHash = PasscodeHasher.hashPasscode("1234"),
            dailyGoal = DailyGoal(targetCalories = 2200.0)
        )
    }

    @Test
    fun invoke_validPasscode_authenticatesAndSetsActiveSession() {
        fakeSessionRepository.seedRememberedProfile(existingProfile.id)

        val result = authenticateUserUseCase(existingProfile.id, "1234")

        assertTrue(result.isSuccess)
        val profile = result.getOrNull()
        assertNotNull(profile)
        assertEquals(existingProfile.id, profile?.id)
        assertEquals(existingProfile.id, fakeSessionRepository.getActiveProfileId())
        assertEquals(existingProfile, fakeSessionRepository.getActiveProfile())
        assertTrue(fakeSessionRepository.isLoggedIn())
        assertNull(fakeSessionRepository.getRememberedProfileId())
    }

    @Test
    fun invoke_validPasscode_withRememberDeviceTrue_persistsRememberedProfile() {
        val result = authenticateUserUseCase(existingProfile.id, "1234", rememberDevice = true)

        assertTrue(result.isSuccess)
        assertEquals(existingProfile.id, fakeSessionRepository.getActiveProfileId())
        assertEquals(existingProfile.id, fakeSessionRepository.getRememberedProfileId())
    }

    @Test
    fun invoke_validPasscode_withRememberDeviceFalse_clearsRememberedProfile() {
        fakeSessionRepository.setActiveProfile(existingProfile, rememberDevice = true)
        assertEquals(existingProfile.id, fakeSessionRepository.getRememberedProfileId())

        val result = authenticateUserUseCase(existingProfile.id, "1234", rememberDevice = false)

        assertTrue(result.isSuccess)
        assertEquals(existingProfile.id, fakeSessionRepository.getActiveProfileId())
        assertNull(fakeSessionRepository.getRememberedProfileId())
    }

    @Test
    fun invoke_incorrectPasscode_returnsFailureAndDoesNotSetSession() {
        val result = authenticateUserUseCase(existingProfile.id, "9999")

        assertTrue(result.isFailure)
        assertEquals("Incorrect passcode", result.exceptionOrNull()?.message)
        assertNull(fakeSessionRepository.getActiveProfile())
        assertFalse(fakeSessionRepository.isLoggedIn())
    }

    @Test
    fun invoke_invalidPasscodeFormat_returnsFailure() {
        val shortResult = authenticateUserUseCase(existingProfile.id, "12")
        assertTrue(shortResult.isFailure)
        assertEquals("Incorrect passcode", shortResult.exceptionOrNull()?.message)

        val nonDigitResult = authenticateUserUseCase(existingProfile.id, "abcd")
        assertTrue(nonDigitResult.isFailure)
        assertEquals("Incorrect passcode", nonDigitResult.exceptionOrNull()?.message)
    }

    @Test
    fun invoke_nonExistentProfile_returnsFailure() {
        val result = authenticateUserUseCase("non-existent-id", "1234")

        assertTrue(result.isFailure)
        assertEquals("Profile not found", result.exceptionOrNull()?.message)
        assertNull(fakeSessionRepository.getActiveProfile())
    }

    @Test
    fun invoke_databaseException_returnsFailure() {
        val throwingRepo = object : UserRepository by fakeUserRepository {
            override fun getProfileById(id: String): UserProfile? {
                throw RuntimeException("Database I/O Error")
            }
        }
        val useCase = AuthenticateUserUseCase(throwingRepo, fakeSessionRepository, fakeAttemptStore)
        val result = useCase(existingProfile.id, "1234")

        assertTrue(result.isFailure)
        assertEquals("Database I/O Error", result.exceptionOrNull()?.message)
        assertNull(fakeSessionRepository.getActiveProfile())
    }

    // ----- Lockout behavior (story 7.1) -----

    @Test
    fun invoke_wrongPasscode_recordsFailure() {
        authenticateUserUseCase(existingProfile.id, "9999")

        assertTrue(authenticateUserUseCase(existingProfile.id, "9999").isFailure)
        assertEquals(2, fakeAttemptStore.failureCount(existingProfile.id))
    }

    @Test
    fun invoke_underLockout_shortCircuitsBeforeVerification() {
        // 3 failures just happened → +5 s after 3rd → wait remains
        fakeAttemptStore.seedFailures(existingProfile.id, 3, System.currentTimeMillis())
        val trackingRepo = VerifyTrackingRepository(fakeUserRepository)
        val useCase = AuthenticateUserUseCase(trackingRepo, fakeSessionRepository, fakeAttemptStore)

        val result = useCase(existingProfile.id, "9999")

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is PasscodeLockoutException)
        assertTrue((exception as PasscodeLockoutException).remainingSeconds > 0)
        // Verification was NEVER attempted (KDF not run)
        assertEquals(0, trackingRepo.verifyPasscodeCalls)
        // Counter unchanged while short-circuited
        assertEquals(3, fakeAttemptStore.failureCount(existingProfile.id))
        assertEquals(0, fakeAttemptStore.recordFailureCalls)
        assertNull(fakeSessionRepository.getActiveProfile())
    }

    @Test
    fun invoke_underLockout_invalidFormatStillThrowsLockoutException() {
        fakeAttemptStore.seedFailures(existingProfile.id, 3, System.currentTimeMillis())
        val result = authenticateUserUseCase(existingProfile.id, "12")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PasscodeLockoutException)
    }

    @Test
    fun invoke_repeatedFailures_escalateToLockout() {
        // 1st failure: free, counter = 1
        assertTrue(authenticateUserUseCase(existingProfile.id, "9999").isFailure)
        assertEquals(1, fakeAttemptStore.failureCount(existingProfile.id))

        // 2nd failure: attempt still accepted, counter = 2 → next wait is +1 s
        assertTrue(authenticateUserUseCase(existingProfile.id, "9999").isFailure)
        assertEquals(2, fakeAttemptStore.failureCount(existingProfile.id))

        // 3rd attempt: locked (+1 s after 2nd failure) — short-circuited, no new failure
        val locked = authenticateUserUseCase(existingProfile.id, "9999")
        assertTrue(locked.isFailure)
        val exception = locked.exceptionOrNull() as? PasscodeLockoutException
        assertNotNull(exception)
        assertEquals(1, exception?.remainingSeconds)
        assertEquals(2, fakeAttemptStore.failureCount(existingProfile.id))
    }

    @Test
    fun invoke_successAfterFailures_resetsCounter() {
        // Two failures long enough ago that no wait remains
        fakeAttemptStore.seedFailures(
            existingProfile.id,
            2,
            System.currentTimeMillis() - (LockoutPolicy.maxDelaySeconds + 10) * 1000L
        )
        assertTrue(authenticateUserUseCase(existingProfile.id, "9999").isFailure)
        assertEquals(3, fakeAttemptStore.failureCount(existingProfile.id))

        // Wait > 0 now — must wait out the countdown before a correct attempt lands
        fakeAttemptStore.seedFailures(
            existingProfile.id,
            3,
            System.currentTimeMillis() - (LockoutPolicy.maxDelaySeconds + 10) * 1000L
        )
        val success = authenticateUserUseCase(existingProfile.id, "1234")

        assertTrue(success.isSuccess)
        // Successful login resets the counter
        assertNull(fakeAttemptStore.getFailureState(existingProfile.id))
        assertEquals(1, fakeAttemptStore.resetCalls)
    }

    @Test
    fun logoutUseCase_clearsActiveSession() {
        fakeSessionRepository.setActiveProfile(existingProfile, rememberDevice = true)
        assertTrue(fakeSessionRepository.isLoggedIn())
        assertEquals(existingProfile.id, fakeSessionRepository.getRememberedProfileId())

        logoutUseCase()

        assertNull(fakeSessionRepository.getActiveProfile())
        assertNull(fakeSessionRepository.getActiveProfileId())
        assertNull(fakeSessionRepository.getRememberedProfileId())
        assertFalse(fakeSessionRepository.isLoggedIn())
    }
}

/** Delegating repository that counts verifyPasscode calls — used to assert short-circuiting. */
private class VerifyTrackingRepository(
    private val delegate: UserRepository
) : UserRepository by delegate {
    var verifyPasscodeCalls = 0

    override fun verifyPasscode(profileId: String, passcode: String): Boolean {
        verifyPasscodeCalls++
        return delegate.verifyPasscode(profileId, passcode)
    }
}

class FakeAuthUserRepository : UserRepository {
    private val profiles = mutableListOf<UserProfile>()

    override fun createProfile(username: String, passcodeHash: String, dailyGoal: DailyGoal): UserProfile {
        val id = UUID.randomUUID().toString()
        val profile = UserProfile(
            id = id,
            username = username,
            passcodeHash = passcodeHash,
            dailyGoal = dailyGoal.copy(profileId = id)
        )
        profiles.add(profile)
        return profile
    }

    override fun getProfiles(): List<UserProfile> = profiles

    override fun getProfileById(id: String): UserProfile? {
        return profiles.find { it.id == id }
    }

    override fun verifyPasscode(profileId: String, passcode: String): Boolean {
        val profile = getProfileById(profileId) ?: return false
        return PasscodeHasher.verifyPasscode(passcode, profile.passcodeHash)
    }

    override fun hasProfiles(): Boolean = profiles.isNotEmpty()

    override fun isUsernameTaken(username: String): Boolean {
        return profiles.any { it.username.equals(username, ignoreCase = true) }
    }
}

class FakeSessionRepository : SessionRepository {
    private var activeProfile: UserProfile? = null
    private var rememberedProfileId: String? = null

    override fun getActiveProfile(): UserProfile? = activeProfile

    override fun getActiveProfileId(): String? = activeProfile?.id

    override fun setActiveProfile(profile: UserProfile, rememberDevice: Boolean) {
        activeProfile = profile
        rememberedProfileId = if (rememberDevice) profile.id else null
    }

    override fun getRememberedProfileId(): String? = rememberedProfileId

    /** Test-only seed: sets the persisted ID without activating an in-memory session. */
    fun seedRememberedProfile(profileId: String) {
        rememberedProfileId = profileId
    }

    override fun forgetRememberedProfile() {
        rememberedProfileId = null
    }

    override fun clearSession() {
        activeProfile = null
        rememberedProfileId = null
    }

    override fun isLoggedIn(): Boolean = activeProfile != null
}

class FakePasscodeAttemptStore : PasscodeAttemptStore {
    private val states = mutableMapOf<String, Pair<Int, Long>>()
    var recordFailureCalls = 0
    var resetCalls = 0

    override fun recordFailure(profileId: String, nowMillis: Long) {
        recordFailureCalls++
        val previous = states[profileId]
        states[profileId] = ((previous?.first ?: 0) + 1) to nowMillis
    }

    override fun getFailureState(profileId: String): Pair<Int, Long>? {
        val state = states[profileId] ?: return null
        return if (state.first > 0) state else null
    }

    override fun reset(profileId: String) {
        resetCalls++
        states.remove(profileId)
    }

    /** Test-only seed: installs a failure state without going through recordFailure. */
    fun seedFailures(profileId: String, count: Int, lastFailureAtMillis: Long) {
        states[profileId] = count to lastFailureAtMillis
    }

    fun failureCount(profileId: String): Int = states[profileId]?.first ?: 0
}
