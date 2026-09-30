package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.SessionRepository
import java.util.concurrent.atomic.AtomicReference

class SessionRepositoryImpl : SessionRepository {

    private val currentProfile = AtomicReference<UserProfile?>(null)

    override fun getActiveProfile(): UserProfile? = currentProfile.get()

    override fun getActiveProfileId(): String? = currentProfile.get()?.id

    override fun setActiveProfile(profile: UserProfile) {
        currentProfile.set(profile)
    }

    override fun clearSession() {
        currentProfile.set(null)
    }

    override fun isLoggedIn(): Boolean = currentProfile.get() != null
}
