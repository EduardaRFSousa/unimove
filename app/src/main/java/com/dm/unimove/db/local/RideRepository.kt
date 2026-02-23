package com.dm.unimove.model

import android.util.Log
import com.dm.unimove.db.local.RideDao
import com.dm.unimove.db.local.SolicitationDao
import com.dm.unimove.db.local.SolicitationEntity
import com.dm.unimove.db.local.UserDao
import com.dm.unimove.db.local.UserEntity
import com.dm.unimove.db.local.toEntity
import com.dm.unimove.db.local.toRide
import com.dm.unimove.db.local.toUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class RideRepository(
    private val rideDao: RideDao,
    private val userDao: UserDao,
    private val solicitationDao: SolicitationDao,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // ── Rides ─────────────────────────────────────────────────────────────────

    // A UI sempre observa o Room (offline-first)
    val availableRides: Flow<List<Pair<String, Ride>>> =
        rideDao.getAvailable().map { list ->
            list.map { entity -> entity.id to entity.toRide() }
        }

    fun getActiveRideByDriver(driverId: String): Flow<Pair<String, Ride>?> =
        rideDao.getActiveByDriver(driverId).map { entity ->
            entity?.let { it.id to it.toRide() }
        }

    val rideHistory: Flow<List<Pair<String, Ride>>> =
        rideDao.getHistory().map { list ->
            list.map { entity -> entity.id to entity.toRide() }
        }

    // Busca do Firebase e salva no Room
    suspend fun fetchAndCacheAvailableRides() {
        try {
            val snapshot = db.collection("CARONAS")
                .whereEqualTo("status", RideStatus.AVAILABLE.name)
                .get()
                .await()

            val entities = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Ride::class.java)?.toEntity(doc.id)
            }
            rideDao.insertAll(entities)
        } catch (e: Exception) {
            Log.e("Repository", "Erro ao buscar caronas", e)
        }
    }

    // Cria nova carona: salva local e sincroniza com Firebase
    suspend fun createRide(ride: Ride): String? {
        return try {
            val docRef = db.collection("CARONAS").add(ride).await()
            rideDao.insert(ride.toEntity(docRef.id))

            // Adiciona ao histórico do motorista no Firestore
            ride.driver_ref?.let { driverRef ->
                driverRef.collection("caronas como motorista")
                    .document(docRef.id)
                    .set(mapOf("ride_ref" to docRef))
                    .await()
            }
            docRef.id
        } catch (e: Exception) {
            Log.e("Repository", "Erro ao criar carona", e)
            null
        }
    }

    // ── Solicitations ─────────────────────────────────────────────────────────

    fun getPendingSolicitations(rideId: String): Flow<List<Pair<String, SolicitationEntity>>> =
        solicitationDao.getPendingByRide(rideId).map { list ->
            list.map { entity -> entity.id to entity }
        }

    fun getSolicitedRideIds(passengerId: String): Flow<Set<String>> =
        solicitationDao.getRideIdsByPassenger(passengerId).map { it.toSet() }

    suspend fun sendSolicitation(ride: Ride, rideId: String, passengerId: String) {
        val passengerRef = db.collection("USERS").document(passengerId)
        val rideRef = db.collection("CARONAS").document(rideId)

        val solicitationMap = mapOf(
            "ride_id" to rideRef,
            "passenger_ref" to passengerRef,
            "driver_ref" to ride.driver_ref,
            "status" to "PENDING",
            "timestamp" to com.google.firebase.Timestamp.now()
        )

        try {
            val docRef = db.collection("SOLICITACOES").add(solicitationMap).await()

            // Cache local
            solicitationDao.insert(
                SolicitationEntity(
                    id = docRef.id,
                    ride_id = rideId,
                    passenger_id = passengerId,
                    driver_id = ride.driver_ref?.id ?: "",
                    status = "PENDING",
                    sincronizado = true
                )
            )
        } catch (e: Exception) {
            Log.e("Repository", "Erro ao enviar solicitação", e)
        }
    }

    suspend fun acceptPassenger(solicitationId: String, rideId: String, passengerId: String) {
        try {
            val batch = db.batch()
            val solicitationRef = db.collection("SOLICITACOES").document(solicitationId)
            val rideRef = db.collection("CARONAS").document(rideId)
            val passengerRef = db.collection("USERS").document(passengerId)

            batch.update(solicitationRef, "status", "ACCEPTED")
            batch.update(rideRef, "passenger_refs", com.google.firebase.firestore.FieldValue.arrayUnion(passengerRef))
            batch.commit().await()

            // Atualiza Room
            solicitationDao.updateStatus(solicitationId, "ACCEPTED")
            userDao.insert(UserEntity(id = passengerId, is_busy = true, sincronizado = false))
        } catch (e: Exception) {
            Log.e("Repository", "Erro ao aceitar passageiro", e)
        }
    }

    suspend fun rejectPassenger(solicitationId: String, passengerId: String) {
        try {
            db.collection("SOLICITACOES").document(solicitationId)
                .update("status", "REJECTED")
                .await()

            solicitationDao.updateStatus(solicitationId, "REJECTED")
        } catch (e: Exception) {
            Log.e("Repository", "Erro ao rejeitar passageiro", e)
        }
    }

    // ── User ──────────────────────────────────────────────────────────────────

    fun getUserById(userId: String): Flow<User?> =
        userDao.getById(userId).map { entity -> entity?.toUser() } // ← parênteses corrigidos

    suspend fun saveUser(user: User, userId: String) {
        try {
            db.collection("USERS").document(userId).set(user).await()
            userDao.insert(user.toEntity(userId))
        } catch (e: Exception) {
            Log.e("Repository", "Erro ao salvar usuário", e)
        }
    }

    suspend fun fetchAndCacheUser(userId: String) {
        try {
            val snapshot = db.collection("USERS").document(userId).get().await()
            val user = snapshot.toObject(User::class.java)
            user?.let { userDao.insert(it.toEntity(userId)) }
        } catch (e: Exception) {
            Log.e("Repository", "Erro ao buscar usuário", e)
        }
    }

    suspend fun updateUserBusy(userId: String, busy: Boolean) {
        try {
            db.collection("USERS").document(userId).update("is_busy", busy).await()
            userDao.insert(UserEntity(id = userId, is_busy = busy, sincronizado = true))
        } catch (e: Exception) {
            Log.e("Repository", "Erro ao atualizar status", e)
        }
    }
}