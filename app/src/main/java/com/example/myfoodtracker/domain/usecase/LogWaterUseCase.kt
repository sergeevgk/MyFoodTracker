package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.repository.WaterRepository

class LogWaterUseCase(private val repository: WaterRepository) {
    operator fun invoke(amountMl: Int = 250, date: String): Int = repository.logWater(amountMl, date)
}
