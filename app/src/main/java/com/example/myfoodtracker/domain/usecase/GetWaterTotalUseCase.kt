package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.repository.WaterRepository

class GetWaterTotalUseCase(private val repository: WaterRepository) {
    operator fun invoke(date: String): Int = repository.getWaterTotalMl(date)
}
