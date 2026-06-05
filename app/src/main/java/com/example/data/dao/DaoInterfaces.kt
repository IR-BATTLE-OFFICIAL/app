package com.example.data.dao

import androidx.room.*
import com.example.data.model.ImpairProfile
import com.example.data.model.PingHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface ImpairProfileDao {
    @Query("SELECT * FROM impair_profiles ORDER BY id ASC")
    fun getAllProfiles(): Flow<List<ImpairProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ImpairProfile)

    @Update
    suspend fun updateProfile(profile: ImpairProfile)

    @Delete
    suspend fun deleteProfile(profile: ImpairProfile)

    @Query("DELETE FROM impair_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Int)

    @Query("DELETE FROM impair_profiles")
    suspend fun deleteAllProfiles()
}

@Dao
interface PingHistoryDao {
    @Query("SELECT * FROM ping_histories ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<PingHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PingHistory)

    @Query("DELETE FROM ping_histories WHERE id = :id")
    suspend fun deleteHistoryById(id: Int)

    @Query("DELETE FROM ping_histories")
    suspend fun clearAllHistory()
}
