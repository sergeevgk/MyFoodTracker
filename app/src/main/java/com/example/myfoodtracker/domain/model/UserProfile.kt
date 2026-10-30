package com.example.myfoodtracker.domain.model

data class UserProfile(
    val id: String,
    val username: String,
    val createdAt: Long = System.currentTimeMillis(),
    val dailyGoal: DailyGoal? = null
)
