package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.data.db.WaterLogDao
import com.example.myfoodtracker.data.db.WaterLogEntity
import com.example.myfoodtracker.domain.model.WaterLog
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.domain.repository.WaterRepository
import java.util.UUID

class WaterRepositoryImpl(
    private val waterLogDao: WaterLogDao,
    private val sessionRepository: SessionRepository
) : WaterRepository {

    override fun getWaterLogs(date: String): List<WaterLog> {
        val profileId = sessionRepository.getActiveProfileId() ?: return emptyList()
        return waterLogDao.getWaterLogsByProfileIdAndDate(profileId, date).map { it.toDomain() }
    }

    override fun getWaterTotalMl(date: String): Int {
        val profileId = sessionRepository.getActiveProfileId() ?: return 0
        return waterLogDao.getTotalWaterMlByProfileIdAndDate(profileId, date)
    }

    override fun logWater(amountMl: Int, date: String): Int {
        if (amountMl <= 0) return getWaterTotalMl(date)
        val profileId = sessionRepository.getActiveProfileId() ?: return 0
        val waterLog = WaterLogEntity(
            id = UUID.randomUUID().toString(),
            profileId = profileId,
            date = date,
            amountMl = amountMl,
            loggedAt = System.currentTimeMillis()
        )
        waterLogDao.insertWaterLog(waterLog)
        return getWaterTotalMl(date)
    }

    // Mapping Helpers
    private fun WaterLogEntity.toDomain(): WaterLog {
        return WaterLog(
            id = id,
            profileId = profileId,
            date = date,
            amountMl = amountMl,
            loggedAt = loggedAt
        )
    }
}
