package com.dm.unimove.db.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    // Usado pela UI via Flow (reativo)
    @Query("SELECT * FROM users WHERE id = :id")
    fun getById(id: String): Flow<UserEntity?>

    // Usado pelo ViewModel para leitura única (cache instantâneo)
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getByIdOnce(id: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM users WHERE sincronizado = 0")
    suspend fun getNaoSincronizados(): List<UserEntity>
}