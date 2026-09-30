package com.example.myfoodtracker.domain.repository

import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.UserProfile

interface UserRepository {
    fun createProfile(username: String, passcodeHash: String, dailyGoal: DailyGoal): UserProfile
    fun getProfiles(): List<UserProfile>
    fun getProfileById(id: String): UserProfile?
    fun verifyPasscode(profileId: String, passcode: String): Boolean
    fun hasProfiles(): Boolean
    fun isUsernameTaken(username: String): Boolean
}
