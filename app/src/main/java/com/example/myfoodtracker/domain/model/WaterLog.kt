package com.example.myfoodtracker.domain.model

data class WaterLog(
    val id: String = "",
    val profileId: String = "",
    val date: String = "",
    val amountMl: Int = 0,
    val loggedAt: Long = 0L
)
