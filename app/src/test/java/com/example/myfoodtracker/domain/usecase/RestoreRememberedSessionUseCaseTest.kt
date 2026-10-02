package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.UserProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RestoreRememberedSessionUseCaseTest {

    private lateinit var fakeSessionRepository: FakeSessionRepository
    private lateinit var restoreRememberedSessionUseCase: RestoreRememberedSessionUseCase
    private lateinit var user: UserProfile

    @Before
    fun setUp() {
        fakeSessionRepository = FakeSessionRepository()
        restoreRememberedSessionUseCase = RestoreRememberedSessionUseCase(fakeSessionRepository)
        user = UserProfile(id = "user-1", username = "Georgii", passcodeHash = "salt:hash")
    }

    @Test
    fun invoke_withValidRememberedProfile_activatesSessionAndReturnsProfile() {
        fakeSessionRepository.seedRememberedProfile(user.id)

        val restored = restoreRememberedSessionUseCase(listOf(user))

        assertEquals(user, restored)
        assertTrue(fakeSessionRepository.isLoggedIn())
        assertEquals(user.id, fakeSessionRepository.getActiveProfileId())
        assertEquals(user.id, fakeSessionRepository.getRememberedProfileId())
    }

    @Test
    fun invoke_withStaleRememberedProfile_clearsPersistedIdOnly() {
        fakeSessionRepository.seedRememberedProfile("deleted-user-id")

        val restored = restoreRememberedSessionUseCase(listOf(user))

        assertNull(restored)
        assertNull(fakeSessionRepository.getRememberedProfileId())
        assertFalse(fakeSessionRepository.isLoggedIn())
    }

    @Test
    fun invoke_withoutRememberedProfile_returnsNullWithoutSideEffects() {
        val restored = restoreRememberedSessionUseCase(listOf(user))

        assertNull(restored)
        assertFalse(fakeSessionRepository.isLoggedIn())
        assertNull(fakeSessionRepository.getActiveProfile())
    }
}
