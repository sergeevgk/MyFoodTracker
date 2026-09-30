package com.example.myfoodtracker.domain.repository

import com.example.myfoodtracker.domain.model.WaterLog

interface WaterRepository {
    fun getWaterLogs(date: String): List<WaterLog>
    fun getWaterTotalMl(date: String): Int
    fun logWater(amountMl: Int, date: String): Int
}
