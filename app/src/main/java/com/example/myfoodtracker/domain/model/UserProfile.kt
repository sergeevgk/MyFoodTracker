package com.example.myfoodtracker.domain.model

data class UserProfile(
    val id: Long = 0,
    val username: String,
    val passcodeHash: String,
    val dailyGoal: DailyGoal? = null
)
