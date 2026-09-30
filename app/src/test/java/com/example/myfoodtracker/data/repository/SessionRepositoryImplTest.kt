package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.domain.model.UserProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SessionRepositoryImplTest {

    private lateinit var sessionRepository: SessionRepositoryImpl

    @Before
    fun setUp() {
        sessionRepository = SessionRepositoryImpl()
    }

    @Test
    fun initialState_hasNoActiveProfileAndNotLoggedIn() {
        assertNull(sessionRepository.getActiveProfile())
        assertNull(sessionRepository.getActiveProfileId())
        assertFalse(sessionRepository.isLoggedIn())
    }

    @Test
    fun setActiveProfile_storesProfileAndReportsLoggedIn() {
        val user = UserProfile(id = "user-123", username = "Georgii", passcodeHash = "salt:hash")
        sessionRepository.setActiveProfile(user)

        assertEquals(user, sessionRepository.getActiveProfile())
        assertEquals("user-123", sessionRepository.getActiveProfileId())
        assertTrue(sessionRepository.isLoggedIn())
    }

    @Test
    fun clearSession_clearsStoredProfileAndReportsNotLoggedIn() {
        val user = UserProfile(id = "user-123", username = "Georgii", passcodeHash = "salt:hash")
        sessionRepository.setActiveProfile(user)
        assertTrue(sessionRepository.isLoggedIn())

        sessionRepository.clearSession()

        assertNull(sessionRepository.getActiveProfile())
        assertNull(sessionRepository.getActiveProfileId())
        assertFalse(sessionRepository.isLoggedIn())
    }

    @Test
    fun setActiveProfile_switchesActiveProfileCleanly() {
        val user1 = UserProfile(id = "user-1", username = "User One", passcodeHash = "salt:1")
        val user2 = UserProfile(id = "user-2", username = "User Two", passcodeHash = "salt:2")

        sessionRepository.setActiveProfile(user1)
        assertEquals("user-1", sessionRepository.getActiveProfileId())

        sessionRepository.setActiveProfile(user2)
        assertEquals("user-2", sessionRepository.getActiveProfileId())
        assertEquals("User Two", sessionRepository.getActiveProfile()?.username)
    }
}
