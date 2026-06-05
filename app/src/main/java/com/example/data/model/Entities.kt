package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "impair_profiles")
data class ImpairProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val latencyMs: Int,
    val packetLossPercent: Float,
    val jitterMs: Int,
    val bandwidthMbps: Float,
    val isPreset: Boolean = false
)

@Entity(tableName = "ping_histories")
data class PingHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val appName: String,
    val targetHost: String,
    val averagePingMs: Float,
    val packetLossPercent: Float,
    val timestamp: Long = System.currentTimeMillis()
)
