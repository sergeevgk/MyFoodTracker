package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.domain.repository.SessionStorage
import java.util.concurrent.atomic.AtomicReference

class SessionRepositoryImpl(
    private val sessionStorage: SessionStorage
) : SessionRepository {

    private val currentProfile = AtomicReference<UserProfile?>(null)

    override fun getActiveProfile(): UserProfile? = currentProfile.get()

    override fun getActiveProfileId(): String? = currentProfile.get()?.id

    override fun setActiveProfile(profile: UserProfile, rememberDevice: Boolean) {
        currentProfile.set(profile)
        if (rememberDevice) {
            sessionStorage.setRememberedProfileId(profile.id)
        } else {
            sessionStorage.setRememberedProfileId(null)
        }
    }

    override fun getRememberedProfileId(): String? = sessionStorage.getRememberedProfileId()

    override fun forgetRememberedProfile() {
        sessionStorage.setRememberedProfileId(null)
    }

    override fun clearSession() {
        currentProfile.set(null)
        sessionStorage.clear()
    }

    override fun isLoggedIn(): Boolean = currentProfile.get() != null
}
