package com.dm.unimove.model

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dm.unimove.db.local.AppDatabase
import com.dm.unimove.db.local.SolicitationEntity
import com.dm.unimove.db.local.toEntity
import com.dm.unimove.db.local.toRide
import com.dm.unimove.db.local.toUser
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Firebase.firestore

    // ── Room DAOs (cache local) ───────────────────────────────────────────────
    private val roomDb = AppDatabase.getInstance(application)
    private val rideDao = roomDb.rideDao()
    private val userDao = roomDb.userDao()
    private val solicitationDao = roomDb.solicitationDao()

    // Estado do Usuário Logado
    private val _user = mutableStateOf<User?>(null)
    val user: State<User?> = _user

    // Estado para a lista de caronas (Ex: para o Feed/Mapa)
    private val _availableRides = mutableStateOf<List<Pair<String, Ride>>>(emptyList())
    val availableRides: State<List<Pair<String, Ride>>> = _availableRides

    private val _activeRide = mutableStateOf<Pair<String, Ride>?>(null)
    val activeRide: State<Pair<String, Ride>?> = _activeRide

    private val _pendingSolicitations = mutableStateOf<List<Pair<String, Map<String, Any>>>>(emptyList())
    val pendingSolicitations: State<List<Pair<String, Map<String, Any>>>> = _pendingSolicitations

    private val _userSolicitadosIds = mutableStateOf<Set<String>>(emptySet())
    val userSolicitadosIds: State<Set<String>> = _userSolicitadosIds

    /**
     * Salva uma nova carona no Firestore.
     * Implementa a lógica de salvar na coleção global e no histórico do motorista.
     */
    fun createNewRide(ride: Ride) {
        db.collection("CARONAS")
            .add(ride)
            .addOnSuccessListener { docRef ->
                ride.driver_ref?.let { driverRef ->
                    // Usamos o ID da carona como ID do documento na subcoleção
                    driverRef.collection("caronas como motorista")
                        .document(docRef.id)
                        .set(mapOf("ride_ref" to docRef)) // Guarda a referência direta
                }
                // Salva no cache local após sucesso no Firestore
                viewModelScope.launch {
                    rideDao.insert(ride.toEntity(docRef.id))
                }
            }
    }

    /**
     * Busca todas as caronas com status AVAILABLE para mostrar no mapa.
     * Estratégia offline-first: carrega do Room imediatamente, depois sincroniza com Firebase.
     */
    fun fetchAvailableRides() {
        // 1. Carrega do cache local instantaneamente (funciona offline)
        viewModelScope.launch {
            val cached = rideDao.getAvailableOnce()
            if (cached.isNotEmpty()) {
                _availableRides.value = cached.map { entity -> entity.id to entity.toRide() }
            }
        }

        // 2. Escuta o Firebase em tempo real e atualiza Room + UI
        db.collection("CARONAS")
            .whereEqualTo("status", RideStatus.AVAILABLE.name)
            .addSnapshotListener { value, error ->
                if (error != null) return@addSnapshotListener
                val list = value?.documents?.mapNotNull { doc ->
                    val ride = doc.toObject(Ride::class.java)
                    if (ride != null) doc.id to ride else null
                }
                _availableRides.value = list ?: emptyList()

                // Atualiza o cache local com os dados mais recentes
                viewModelScope.launch {
                    list?.let { pairs ->
                        rideDao.insertAll(pairs.map { (id, ride) -> ride.toEntity(id) })
                    }
                }
            }
    }

    /**
     * Carrega os dados do perfil do usuário do Firestore.
     * Chamado logo após o login ou na inicialização.
     * Estratégia offline-first: carrega do Room imediatamente, depois sincroniza com Firebase.
     */
    fun loadUserProfile(userId: String) {
        // 1. Carrega do cache local instantaneamente (funciona offline)
        viewModelScope.launch {
            val cached = userDao.getByIdOnce(userId)
            if (cached != null) {
                _user.value = cached.toUser()
            }
        }

        // 2. Escuta o Firebase e atualiza Room + UI
        db.collection("USERS").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("Firestore", "Erro ao carregar perfil", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val fetchedUser = snapshot.toObject(User::class.java)
                    _user.value = fetchedUser

                    // Atualiza o cache local com os dados mais recentes
                    viewModelScope.launch {
                        fetchedUser?.let { userDao.insert(it.toEntity(userId)) }
                    }
                }
            }
    }

    /**
     * Define o usuário atual após o login.
     */
    fun setUser(loggedUser: User) {
        _user.value = loggedUser
    }

    /**
     * Cria o documento com o ID do Auth e coloca os campos name e email
     */
    fun saveUserToFirestore(user: User, userId: String) {
        db.collection("USERS").document(userId)
            .set(user)
            .addOnSuccessListener {
                Log.d("Firestore", "Perfil do usuário atualizado com sucesso!")
                // Salva no cache local após sucesso no Firestore
                viewModelScope.launch {
                    userDao.insert(user.toEntity(userId))
                }
            }
    }

    fun canUserStartNewActivity(): Boolean {
        val currentUser = _user.value
        return currentUser?.is_busy == false || currentUser == null
    }

    fun updateUserBusyStatus(userId: String, busy: Boolean) {
        db.collection("USERS").document(userId)
            .update("is_busy", busy)
            .addOnSuccessListener {
                Log.d("Firestore", "Status de ocupação atualizado: $busy")
                // Atualiza o cache local
                viewModelScope.launch {
                    val cached = userDao.getByIdOnce(userId)
                    if (cached != null) {
                        userDao.insert(cached.copy(is_busy = busy))
                    }
                }
            }
    }

    fun sendRideSolicitation(ride: Ride, rideId: String, passengerId: String) {
        val currentUser = _user.value
        if (currentUser?.is_busy == true) {
            Log.w("Ride", "Usuário já está ocupado")
            return
        }

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val passengerRef = db.collection("USERS").document(passengerId)
        val rideRef = db.collection("CARONAS").document(rideId)

        val solicitation = mapOf(
            "ride_id" to rideRef,
            "passenger_ref" to passengerRef,
            "driver_ref" to ride.driver_ref,
            "status" to "PENDING",
            "timestamp" to com.google.firebase.Timestamp.now()
        )

        db.collection("SOLICITACOES")
            .add(solicitation)
            .addOnSuccessListener { docRef ->
                Log.d("Firestore", "Solicitação enviada!")
                // Salva no cache local após sucesso no Firestore
                viewModelScope.launch {
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
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Erro ao enviar solicitação", e)
            }
    }

    fun registerNewUser(user: User, password: String, onComplete: (Boolean, String?) -> Unit) {
        val auth = com.google.firebase.Firebase.auth
        auth.createUserWithEmailAndPassword(user.email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = task.result?.user?.uid
                    if (userId != null) {
                        db.collection("USERS").document(userId)
                            .set(user)
                            .addOnSuccessListener {
                                // Salva no cache local após sucesso no Firestore
                                viewModelScope.launch {
                                    userDao.insert(user.toEntity(userId))
                                }
                                onComplete(true, null)
                            }
                            .addOnFailureListener { e -> onComplete(false, e.message) }
                    }
                } else {
                    onComplete(false, task.exception?.message)
                }
            }
    }

    fun fetchActiveRide(userId: String) {
        val userRef = db.collection("USERS").document(userId)
        db.collection("CARONAS")
            .whereEqualTo("driver_ref", userRef)
            .whereIn("status", listOf("AVAILABLE", "ON_GOING"))
            .addSnapshotListener { snapshot, _ ->
                val doc = snapshot?.documents?.firstOrNull()
                if (doc != null) {
                    val ride = doc.toObject(Ride::class.java)!!
                    _activeRide.value = doc.id to ride
                    fetchSolicitationsForRide(doc.id) // Se é motorista, busca quem pediu carona

                    // Salva no cache local
                    viewModelScope.launch {
                        rideDao.insert(ride.toEntity(doc.id))
                    }
                } else {
                    // 2. Se não achou como motorista, procura como PASSAGEIRO aceito
                    // TODO: Implementar lógica de busca em SOLICITACOES com status 'ACCEPTED'
                }
            }
    }

    private fun fetchSolicitationsForRide(rideId: String) {
        val rideRef = db.collection("CARONAS").document(rideId)
        db.collection("SOLICITACOES")
            .whereEqualTo("ride_id", rideRef)
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, _ ->
                _pendingSolicitations.value = snapshot?.documents?.map { it.id to it.data!! } ?: emptyList()
            }
    }

    fun acceptPassenger(solicitationId: String, rideId: String, passengerId: String) {
        val batch = db.batch()
        val solicitationRef = db.collection("SOLICITACOES").document(solicitationId)
        val rideRef = db.collection("CARONAS").document(rideId)
        val passengerRef = db.collection("USERS").document(passengerId)

        batch.update(solicitationRef, "status", "ACCEPTED")
        batch.update(rideRef, "passenger_refs", com.google.firebase.firestore.FieldValue.arrayUnion(passengerRef))
        batch.commit().addOnSuccessListener {
            updateUserBusyStatus(passengerId, true)
            Log.d("Firestore", "Passageiro aceito e bloqueado para novas ações.")
            // Atualiza o cache local
            viewModelScope.launch {
                solicitationDao.updateStatus(solicitationId, "ACCEPTED")
            }
        }
    }

    fun rejectPassenger(solicitationId: String, passengerId: String) {
        db.collection("SOLICITACOES").document(solicitationId)
            .update("status", "REJECTED")
            .addOnSuccessListener {
                // Liberamos o passageiro para pedir outra carona se ele for rejeitado
                updateUserBusyStatus(passengerId, false)
                // Atualiza o cache local
                viewModelScope.launch {
                    solicitationDao.updateStatus(solicitationId, "REJECTED")
                }
            }
    }

    fun fetchUserSolicitations(userId: String) {
        val passengerRef = db.collection("USERS").document(userId)
        db.collection("SOLICITACOES")
            .whereEqualTo("passenger_ref", passengerRef)
            .addSnapshotListener { snapshot, _ ->
                val ids = snapshot?.documents?.mapNotNull { doc ->
                    val rideRef = doc.get("ride_id") as? DocumentReference
                    rideRef?.id
                }?.toSet()
                _userSolicitadosIds.value = ids ?: emptySet()

                // Salva solicitações no cache local
                viewModelScope.launch {
                    snapshot?.documents?.forEach { doc ->
                        solicitationDao.insert(
                            SolicitationEntity(
                                id = doc.id,
                                ride_id = (doc.get("ride_id") as? DocumentReference)?.id ?: "",
                                passenger_id = userId,
                                driver_id = (doc.get("driver_ref") as? DocumentReference)?.id ?: "",
                                status = doc.getString("status") ?: "PENDING",
                                sincronizado = true
                            )
                        )
                    }
                }
            }
    }
}