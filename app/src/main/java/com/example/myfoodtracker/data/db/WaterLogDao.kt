package com.example.myfoodtracker.data.db

import androidx.room.*

@Dao
interface WaterLogDao {
    @Query("SELECT * FROM water_logs WHERE profile_id = :profileId AND logged_date = :date")
    fun getWaterLogsByProfileIdAndDate(profileId: String, date: String): List<WaterLogEntity>

    @Query("SELECT COALESCE(SUM(amount_ml), 0) FROM water_logs WHERE profile_id = :profileId AND logged_date = :date")
    fun getTotalWaterMlByProfileIdAndDate(profileId: String, date: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWaterLog(waterLog: WaterLogEntity)
}
