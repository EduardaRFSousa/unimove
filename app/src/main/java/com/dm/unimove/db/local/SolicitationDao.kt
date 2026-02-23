package com.dm.unimove.db.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SolicitationDao {

    @Query("SELECT * FROM solicitations WHERE ride_id = :rideId AND status = 'PENDING'")
    fun getPendingByRide(rideId: String): Flow<List<SolicitationEntity>>

    @Query("SELECT ride_id FROM solicitations WHERE passenger_id = :passengerId")
    fun getRideIdsByPassenger(passengerId: String): Flow<List<String>>

    @Query("SELECT * FROM solicitations WHERE passenger_id = :passengerId AND status = 'ACCEPTED' LIMIT 1")
    fun getAcceptedByPassenger(passengerId: String): Flow<SolicitationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(solicitation: SolicitationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(solicitations: List<SolicitationEntity>)

    @Query("UPDATE solicitations SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("SELECT * FROM solicitations WHERE sincronizado = 0")
    suspend fun getNaoSincronizados(): List<SolicitationEntity>
}