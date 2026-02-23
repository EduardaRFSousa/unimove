package com.dm.unimove.db.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {

    // Usado pela UI via Flow (reativo)
    @Query("SELECT * FROM rides WHERE status = 'AVAILABLE'")
    fun getAvailable(): Flow<List<RideEntity>>

    // Usado pelo ViewModel para leitura única (cache instantâneo)
    @Query("SELECT * FROM rides WHERE status = 'AVAILABLE'")
    suspend fun getAvailableOnce(): List<RideEntity>

    @Query("SELECT * FROM rides WHERE driver_id = :driverId AND status IN ('AVAILABLE', 'ON_GOING') LIMIT 1")
    fun getActiveByDriver(driverId: String): Flow<RideEntity?>

    @Query("SELECT * FROM rides WHERE status IN ('FINISHED', 'CANCELED') ORDER BY date_time DESC")
    fun getHistory(): Flow<List<RideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ride: RideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rides: List<RideEntity>)

    @Query("UPDATE rides SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM rides WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM rides WHERE sincronizado = 0")
    suspend fun getNaoSincronizados(): List<RideEntity>
}