package com.example.data.repository

import com.example.data.dao.ImpairProfileDao
import com.example.data.dao.PingHistoryDao
import com.example.data.model.ImpairProfile
import com.example.data.model.PingHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PlayPingRepository(
    private val impairProfileDao: ImpairProfileDao,
    private val pingHistoryDao: PingHistoryDao
) {
    val allProfiles: Flow<List<ImpairProfile>> = impairProfileDao.getAllProfiles()
    val allHistory: Flow<List<PingHistory>> = pingHistoryDao.getAllHistory()

    suspend fun insertProfile(profile: ImpairProfile) {
        impairProfileDao.insertProfile(profile)
    }

    suspend fun updateProfile(profile: ImpairProfile) {
        impairProfileDao.updateProfile(profile)
    }

    suspend fun deleteProfile(profile: ImpairProfile) {
        impairProfileDao.deleteProfile(profile)
    }

    suspend fun deleteProfileById(id: Int) {
        impairProfileDao.deleteProfileById(id)
    }

    suspend fun insertHistory(history: PingHistory) {
        pingHistoryDao.insertHistory(history)
    }

    suspend fun deleteHistoryById(id: Int) {
        pingHistoryDao.deleteHistoryById(id)
    }

    suspend fun clearHistory() {
        pingHistoryDao.clearAllHistory()
    }

    suspend fun checkAndPreseedProfiles() {
        impairProfileDao.deleteAllProfiles()
        val defaultPresets = listOf(
            ImpairProfile(name = "Connection lost", latencyMs = 0, packetLossPercent = 100f, jitterMs = 0, bandwidthMbps = 0f, isPreset = true),
            ImpairProfile(name = "Block upload", latencyMs = 300, packetLossPercent = 100f, jitterMs = 0, bandwidthMbps = 0f, isPreset = true),
            ImpairProfile(name = "Block download", latencyMs = 300, packetLossPercent = 100f, jitterMs = 0, bandwidthMbps = 0f, isPreset = true)
        )
        for (p in defaultPresets) {
            impairProfileDao.insertProfile(p)
        }
    }
}
