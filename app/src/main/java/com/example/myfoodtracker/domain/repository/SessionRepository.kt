package com.example.myfoodtracker.domain.repository

import com.example.myfoodtracker.domain.model.UserProfile

interface SessionRepository {
    fun getActiveProfile(): UserProfile?
    fun getActiveProfileId(): String?
    fun setActiveProfile(profile: UserProfile, rememberDevice: Boolean = false)
    fun getRememberedProfileId(): String?
    fun forgetRememberedProfile()
    fun clearSession()
    fun isLoggedIn(): Boolean
}
