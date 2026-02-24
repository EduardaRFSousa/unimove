package com.dm.unimove.model

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore

class MainViewModel : ViewModel() {
    private val db = Firebase.firestore

    // Estado do Usuário Logado
    private val _user = mutableStateOf<User?>(null)
    val user: State<User?> = _user

    // Estado para a lista de caronas (Ex: para o Feed/Mapa)
    private val _availableRides = mutableStateOf<List<Pair<String, Ride>>>(emptyList())
    val availableRides: State<List<Pair<String, Ride>>> = _availableRides

    private val _activeRide = mutableStateOf<Pair<String, Ride>?>(null)
    val activeRide: State<Pair<String, Ride>?> = _activeRide

    private val _pendingSolicitations =
        mutableStateOf<List<Pair<String, Map<String, Any>>>>(emptyList())
    val pendingSolicitations: State<List<Pair<String, Map<String, Any>>>> = _pendingSolicitations

    private val _userSolicitadosIds = mutableStateOf<Set<String>>(emptySet())
    val userSolicitadosIds: State<Set<String>> = _userSolicitadosIds

    // Assento selecionado pelo passageiro
    private val _selectedSeat = mutableStateOf<String?>(null)
    val selectedSeat: State<String?> = _selectedSeat

    /**
     * Salva o assento escolhido pelo passageiro no estado do ViewModel.
     */
    fun selectSeat(seatKey: String) {
        _selectedSeat.value = seatKey
        Log.d("ViewModel", "Assento selecionado: $seatKey")
    }

    /**
     * Salva uma nova carona no Firestore.
     */
    fun createNewRide(ride: Ride) {
        db.collection("CARONAS")
            .add(ride)
            .addOnSuccessListener { docRef ->
                ride.driver_ref?.let { driverRef ->
                    driverRef.collection("caronas como motorista")
                        .document(docRef.id)
                        .set(mapOf("ride_ref" to docRef))
                }
            }
    }

    /**
     * Busca todas as caronas com status AVAILABLE para mostrar no mapa.
     * Preenche o campo 'id' de cada Ride com o ID do documento.
     */
    fun fetchAvailableRides() {
        db.collection("CARONAS")
            .whereEqualTo("status", RideStatus.AVAILABLE.name)
            .addSnapshotListener { value, error ->
                if (error != null) return@addSnapshotListener
                val list = value?.documents?.mapNotNull { doc ->
                    val ride = doc.toObject(Ride::class.java)
                    if (ride != null) {
                        ride.id = doc.id // <-- preenche o id
                        doc.id to ride
                    } else null
                }
                _availableRides.value = list ?: emptyList()
            }
    }

    /**
     * Carrega os dados do perfil do usuário do Firestore.
     */
    fun loadUserProfile(userId: String) {
        db.collection("USERS").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("Firestore", "Erro ao carregar perfil", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    _user.value = snapshot.toObject(User::class.java)
                }
            }
    }

    fun setUser(loggedUser: User) {
        _user.value = loggedUser
    }

    fun saveUserToFirestore(user: User, userId: String) {
        db.collection("USERS").document(userId)
            .set(user)
            .addOnSuccessListener {
                Log.d("Firestore", "Perfil do usuário atualizado com sucesso!")
            }
    }

    fun canUserStartNewActivity(): Boolean {
        val currentUser = _user.value
        return currentUser?.is_busy == false || currentUser == null
    }

    fun updateUserBusyStatus(userId: String, busy: Boolean) {
        db.collection("USERS").document(userId)
            .update("is_busy", busy)
            .addOnSuccessListener { Log.d("Firestore", "Status de ocupação atualizado: $busy") }
    }

    fun sendRideSolicitation(
        ride: Ride,
        rideId: String,
        passengerId: String,
        tempSelectedSeat: String
    ) {
        // CORREÇÃO: Usando os nomes corretos dos parâmetros
        val solicitationData = hashMapOf(
            "ride_id" to rideId,
            "passenger_id" to passengerId, // Ajustado de userId para passengerId
            "selected_seat" to tempSelectedSeat, // Ajustado de selectedSeat para tempSelectedSeat
            "status" to "PENDING",
            "timestamp" to FieldValue.serverTimestamp()
        )

        val currentUser = _user.value
        if (currentUser?.is_busy == true) {
            Log.w("Ride", "Usuário já está ocupado")
            return
        }

        val passengerRef = db.collection("USERS").document(passengerId)
        val rideRef = db.collection("CARONAS").document(rideId)

        val solicitation = mapOf(
            "ride_id" to rideRef,
            "passenger_ref" to passengerRef,
            "driver_ref" to ride.driver_ref,
            "selected_seat" to tempSelectedSeat, // Incluído também no mapa enviado ao Firestore
            "status" to "PENDING",
            "timestamp" to com.google.firebase.Timestamp.now()
        )

        db.collection("SOLICITACOES")
            .add(solicitation)
            .addOnSuccessListener { Log.d("Firestore", "Solicitação enviada!") }
            .addOnFailureListener { e -> Log.e("Firestore", "Erro ao enviar solicitação", e) }
    }

    fun registerNewUser(user: User, password: String, onComplete: (Boolean, String?) -> Unit) {
        val auth = Firebase.auth
        auth.createUserWithEmailAndPassword(user.email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = task.result?.user?.uid
                    if (userId != null) {
                        db.collection("USERS").document(userId)
                            .set(user)
                            .addOnSuccessListener { onComplete(true, null) }
                            .addOnFailureListener { e -> onComplete(false, e.message) }
                    }
                } else {
                    onComplete(false, task.exception?.message)
                }
            }
    }

    fun fetchActiveRide(userId: String) {
        val userRef = db.collection("USERS").document(userId)

        // Escuta caronas onde o usuário é o MOTORISTA
        db.collection("CARONAS")
            .whereEqualTo("driver_ref", userRef)
            .whereIn("status", listOf("AVAILABLE", "ON_GOING"))
            .addSnapshotListener { snapshot, _ ->
                val doc = snapshot?.documents?.firstOrNull()
                if (doc != null) {
                    val ride = doc.toObject(Ride::class.java)!!
                    ride.id = doc.id
                    _activeRide.value = doc.id to ride
                    fetchSolicitationsForRide(doc.id)
                } else {
                    // Se não for motorista, busca caronas onde ele é PASSAGEIRO
                    db.collection("CARONAS")
                        .whereArrayContains("passenger_refs", userRef)
                        .whereEqualTo("status", "AVAILABLE") // Ou ON_GOING
                        .addSnapshotListener { passengerSnapshot, _ ->
                            val pDoc = passengerSnapshot?.documents?.firstOrNull()
                            if (pDoc != null) {
                                val pRide = pDoc.toObject(Ride::class.java)!!
                                pRide.id = pDoc.id
                                _activeRide.value = pDoc.id to pRide
                            } else {
                                _activeRide.value = null
                            }
                        }
                }
            }
    }

    fun fetchSolicitationsForRide(rideId: String) {
        val rideRef = db.collection("CARONAS").document(rideId)
        db.collection("SOLICITACOES")
            // Escuta tanto PENDING quanto ACCEPTED para garantir que o assento fique ocupado
            .whereEqualTo("ride_id", rideRef)
            .whereIn("status", listOf("PENDING", "ACCEPTED"))
            .addSnapshotListener { snapshot, error ->
                _pendingSolicitations.value =
                    snapshot?.documents?.map { it.id to it.data!! } ?: emptyList()
                if (error != null) {
                    Log.e("Firestore", "Erro ao buscar solicitações", error)
                    return@addSnapshotListener
                }
                // Mapeia os dados incluindo o campo 'selected_seat' necessário para o mapa
                _pendingSolicitations.value =
                    snapshot?.documents?.map { it.id to it.data!! } ?: emptyList()
            }
    }

    fun acceptPassenger(solicitationId: String, rideId: String, passengerId: String) {
        val batch = db.batch()

        val solicitationRef = db.collection("SOLICITACOES").document(solicitationId)
        val rideRef = db.collection("CARONAS").document(rideId)
        val passengerRef = db.collection("USERS").document(passengerId)

        // 1. Atualiza a solicitação atual para ACEITA
        batch.update(solicitationRef, "status", "ACCEPTED")

        // 2. Adiciona o passageiro à lista oficial da carona
        batch.update(rideRef, "passenger_refs", FieldValue.arrayUnion(passengerRef))

        // 3. Cria a coleção "caronas como caroneiro" vinculada ao usuário
        val historyRef = passengerRef.collection("caronas como caroneiro").document(rideId)
        batch.set(
            historyRef, mapOf(
                "ride_ref" to rideRef,
                "timestamp" to FieldValue.serverTimestamp()
            )
        )

        // 4. Marca o usuário como ocupado
        batch.update(passengerRef, "is_busy", true)

        // 5. Deleta outras solicitações pendentes deste usuário
        db.collection("SOLICITACOES")
            .whereEqualTo("passenger_ref", passengerRef)
            .whereEqualTo("status", "PENDING")
            .get()
            .addOnSuccessListener { snapshot ->
                val deleteBatch = db.batch() // Novo batch para as deleções
                for (doc in snapshot.documents) {
                    // Não deleta a que acabamos de aceitar (opcional, já que mudou status)
                    if (doc.id != solicitationId) {
                        deleteBatch.delete(doc.reference)
                    }
                }

                // Primeiro executa o batch de aceitação
                batch.commit().addOnSuccessListener {
                    // Depois executa a limpeza das outras solicitações
                    deleteBatch.commit()
                    Log.d("Firestore", "Passageiro aceito e outras solicitações removidas!")
                }
            }
    }

    fun rejectPassenger(solicitationId: String, passengerId: String) {
        db.collection("SOLICITACOES").document(solicitationId)
            .update("status", "REJECTED")
            .addOnSuccessListener {
                updateUserBusyStatus(passengerId, false)
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
            }
    }

    var rides by mutableStateOf<List<Ride>>(emptyList())

    fun getRideById(id: String): Ride? {
        return _availableRides.value.find { (docId, _) -> docId == id }?.second
    }
}