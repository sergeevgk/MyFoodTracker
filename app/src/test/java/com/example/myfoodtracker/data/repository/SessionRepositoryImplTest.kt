package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.SessionStorage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SessionRepositoryImplTest {

    private lateinit var sessionStorage: InMemorySessionStorage
    private lateinit var sessionRepository: SessionRepositoryImpl

    @Before
    fun setUp() {
        sessionStorage = InMemorySessionStorage()
        sessionRepository = SessionRepositoryImpl(sessionStorage)
    }

    @Test
    fun initialState_hasNoActiveProfileAndNotLoggedIn() {
        assertNull(sessionRepository.getActiveProfile())
        assertNull(sessionRepository.getActiveProfileId())
        assertNull(sessionRepository.getRememberedProfileId())
        assertFalse(sessionRepository.isLoggedIn())
    }

    @Test
    fun setActiveProfile_storesProfileAndReportsLoggedIn() {
        val user = UserProfile(id = "user-123", username = "Georgii", passcodeHash = "salt:hash")
        sessionRepository.setActiveProfile(user)

        assertEquals(user, sessionRepository.getActiveProfile())
        assertEquals("user-123", sessionRepository.getActiveProfileId())
        assertTrue(sessionRepository.isLoggedIn())
        assertNull(sessionRepository.getRememberedProfileId())
    }

    @Test
    fun setActiveProfile_withRememberDeviceTrue_persistsProfileId() {
        val user = UserProfile(id = "user-123", username = "Georgii", passcodeHash = "salt:hash")
        sessionRepository.setActiveProfile(user, rememberDevice = true)

        assertEquals(user, sessionRepository.getActiveProfile())
        assertEquals("user-123", sessionRepository.getActiveProfileId())
        assertEquals("user-123", sessionRepository.getRememberedProfileId())
        assertEquals("user-123", sessionStorage.getRememberedProfileId())
    }

    @Test
    fun setActiveProfile_withRememberDeviceFalse_clearsPersistedProfileId() {
        val user = UserProfile(id = "user-123", username = "Georgii", passcodeHash = "salt:hash")
        sessionRepository.setActiveProfile(user, rememberDevice = true)
        assertEquals("user-123", sessionRepository.getRememberedProfileId())

        sessionRepository.setActiveProfile(user, rememberDevice = false)
        assertNull(sessionRepository.getRememberedProfileId())
        assertNull(sessionStorage.getRememberedProfileId())
    }

    @Test
    fun clearSession_clearsStoredProfileAndPersistedSession() {
        val user = UserProfile(id = "user-123", username = "Georgii", passcodeHash = "salt:hash")
        sessionRepository.setActiveProfile(user, rememberDevice = true)
        assertTrue(sessionRepository.isLoggedIn())
        assertEquals("user-123", sessionRepository.getRememberedProfileId())

        sessionRepository.clearSession()

        assertNull(sessionRepository.getActiveProfile())
        assertNull(sessionRepository.getActiveProfileId())
        assertNull(sessionRepository.getRememberedProfileId())
        assertNull(sessionStorage.getRememberedProfileId())
        assertFalse(sessionRepository.isLoggedIn())
    }

    @Test
    fun forgetRememberedProfile_clearsPersistedIdButKeepsActiveSession() {
        val user = UserProfile(id = "user-123", username = "Georgii", passcodeHash = "salt:hash")
        sessionRepository.setActiveProfile(user, rememberDevice = true)
        assertEquals("user-123", sessionRepository.getRememberedProfileId())

        sessionRepository.forgetRememberedProfile()

        assertTrue(sessionRepository.isLoggedIn())
        assertEquals("user-123", sessionRepository.getActiveProfileId())
        assertNull(sessionRepository.getRememberedProfileId())
        assertNull(sessionStorage.getRememberedProfileId())
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

class InMemorySessionStorage : SessionStorage {
    private var rememberedProfileId: String? = null

    override fun getRememberedProfileId(): String? = rememberedProfileId

    override fun setRememberedProfileId(profileId: String?) {
        rememberedProfileId = profileId
    }

    override fun clear() {
        rememberedProfileId = null
    }
}
