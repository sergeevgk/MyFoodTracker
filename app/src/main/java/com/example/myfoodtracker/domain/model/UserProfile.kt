package com.example.myfoodtracker.domain.model

data class UserProfile(
    val id: String,
    val username: String,
    val passcodeHash: String,
    val createdAt: Long = System.currentTimeMillis(),
    val dailyGoal: DailyGoal? = null
)
