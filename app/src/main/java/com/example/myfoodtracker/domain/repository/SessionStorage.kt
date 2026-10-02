package com.example.myfoodtracker.domain.repository

interface SessionStorage {
    fun getRememberedProfileId(): String?
    fun setRememberedProfileId(profileId: String?)
    fun clear()
}
