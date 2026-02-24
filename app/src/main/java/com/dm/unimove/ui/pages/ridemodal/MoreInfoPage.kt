package com.dm.unimove.ui.pages.ridemodal

import android.widget.Toast
import com.dm.unimove.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dm.unimove.model.MainViewModel
import com.dm.unimove.model.PaymentType
import com.dm.unimove.model.Ride
import com.dm.unimove.ui.nav.Route
import com.dm.unimove.ui.theme.CustomColors
import com.google.firebase.auth.FirebaseAuth

// ─────────────────────────────────────────────
// COMPONENTES COMPARTILHADOS
// ─────────────────────────────────────────────

@Composable
fun SeatIcon(
    label: String,
    occupant: Any?,
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    onCLick: () -> Unit
) {
    val isOccupied = occupant != null
    val imageRes = if (isOccupied) R.drawable.occupied_seat else R.drawable.empty_seat

    Image(
        painter = painterResource(id = imageRes),
        contentDescription = label,
        colorFilter = if (isSelected) ColorFilter.tint(Color.Green, BlendMode.SrcAtop) else null,
        modifier = modifier
            .clickable(enabled = !isOccupied) { onCLick() }
    )
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(16.dp), shape = CircleShape, color = color) {}
        Spacer(Modifier.width(8.dp))
        Text(text = label, fontSize = 14.sp, color = Color.Gray)
    }
}

// ─────────────────────────────────────────────
// MAPA INTERATIVO DO CARRO
// ─────────────────────────────────────────────

/**
 * Layout do carro baseado no wireframe:
 *
 *   [Motorista esq.]   [Carona frente dir.]
 *   [trás-esq.] [trás-meio] [trás-dir.]
 *
 * O motorista fica sempre no lado ESQUERDO (posição brasileira: volante à esquerda).
 */
@Composable
fun InteractiveCarMap(
    ride: Ride,
    viewModel: MainViewModel,
    navController: NavController,
    isReadOnly: Boolean = false,
    onSeatSelected: (String) -> Unit
) {
    var selectedSeatKey by remember { mutableStateOf<String?>(null) }
    val pendingSolicitations by viewModel.pendingSolicitations

    val requestedSeats = remember(pendingSolicitations) {
        pendingSolicitations.mapNotNull { it.second["selected_seat"] as? String }.toSet()
    }

    Box(modifier = Modifier.size(280.dp, 400.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = R.drawable.car_detailspage),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        // ── Assentos Traseiros ──
        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val trasKeys = listOf("carona-trás-esquerda", "carona-trás-meio", "carona-trás-direita")
            trasKeys.forEach { key ->
                val isEffectivelyOccupied = ride.seats_map[key] != null || requestedSeats.contains(key)

                SeatIcon(
                    label = key,
                    occupant = if (isEffectivelyOccupied) "reserved" else null,
                    modifier = Modifier.size(50.dp),
                    isSelected = selectedSeatKey == key,
                    onCLick = {
                        if (!isReadOnly && !isEffectivelyOccupied) {
                            selectedSeatKey = key
                            viewModel.selectSeat(key)
                            onSeatSelected(key)
                        }
                    }
                )
            }
        }

        // ── Assento do Motorista (Agora na ESQUERDA - BottomStart) ──
        SeatIcon(
            label = "motorista",
            occupant = ride.driver_ref,
            modifier = Modifier
                .align(Alignment.BottomStart) // Alinha na esquerda embaixo
                .padding(start = 70.dp, bottom = 130.dp)
                .size(50.dp),
            isSelected = false,
            onCLick = {}
        )

        // ── Carona Frente (Agora na DIREITA - BottomEnd) ──
        val frontOccupied = ride.seats_map["carona-frente"] != null || requestedSeats.contains("carona-frente")
        SeatIcon(
            label = "carona-frente",
            occupant = if (frontOccupied) "reserved" else null,
            modifier = Modifier
                .align(Alignment.BottomEnd) // Alinha na direita embaixo
                .padding(end = 65.dp, bottom = 130.dp)
                .size(50.dp),
            isSelected = selectedSeatKey == "carona-frente",
            onCLick = {
                if (!isReadOnly && !frontOccupied) {
                    selectedSeatKey = "carona-frente"
                    viewModel.selectSeat("carona-frente")
                    onSeatSelected("carona-frente")
                }
            }
        )
    }
}

// ─────────────────────────────────────────────
// MORE INFO PAGE — Layout igual ao wireframe
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreInfoPage(
    ride: Ride,
    navController: NavController,
    viewModel: MainViewModel
) {
    LaunchedEffect(ride.id) {
        viewModel.fetchActiveRide(ride.id)
    }
    LaunchedEffect(ride.id) {
        viewModel.fetchSolicitationsForRide(ride.id)
    }
    var driverName by remember { mutableStateOf("Carregando...") }
    LaunchedEffect(ride.driver_ref) {
        ride.driver_ref?.get()?.addOnSuccessListener { snapshot ->
            driverName = snapshot.getString("name") ?: "Motorista Desconhecido"
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Mais informações",
                        color = CustomColors.BrightPurple,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Cabeçalho: ícone + nome + modelo + valor (horizontal, igual wireframe) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar circular
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF3E5F5),
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = Color(0xFF9575CD),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Dados do motorista — coluna à direita do avatar
                Column {
                    Text(
                        text = driverName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp
                    )
                    Text(
                        text = "Modelo do carro:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        color = Color(0xFF6200EE)
                    )
                    Text(
                        text = ride.vehicle_model.ifBlank { "Não informado" },
                        fontSize = 20.sp,
                        color = Color.DarkGray
                    )
                    val valorText = when (ride.payment_type) {
                        PaymentType.FREE -> "Cortesia"
                        PaymentType.NEGOTIABLE -> "A negociar"
                        PaymentType.PAY -> "R$ ${"%.2f".format(ride.ride_value)}"
                    }
                    Text(
                        text = "Valor: $valorText",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Mapa do carro (somente leitura) ──
            InteractiveCarMap(
                ride = ride,
                viewModel = viewModel,
                navController = navController,
                isReadOnly = true,
                onSeatSelected = {}
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Legenda ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                LegendItem("Ocupado", Color(0xFF424242))
                LegendItem("Disponível", Color(0xFF9575CD))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Card de Descrição (igual wireframe) ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Descrição:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF6200EE)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ride.description.ifBlank { "Nenhuma." },
                        fontSize = 14.sp,
                        color = Color.DarkGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────
// RIDE SCHEDULE PAGE
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideSchedulePage(
    ride: Ride,
    navController: NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val user = FirebaseAuth.getInstance().currentUser
    var showConfirmModal by remember { mutableStateOf(false) }

    // Estado que controla o filtro verde no mapa
    var selectedSeatKeyForMap by remember { mutableStateOf<String?>(null) }

    var driverName by remember { mutableStateOf("Carregando...") }
    LaunchedEffect(ride.driver_ref) {
        ride.driver_ref?.get()?.addOnSuccessListener { snapshot ->
            driverName = snapshot.getString("name") ?: "Motorista Desconhecido"
        }
    }

    // Se o modal fechar, limpamos o filtro verde do mapa
    LaunchedEffect(showConfirmModal) {
        if (!showConfirmModal) {
            selectedSeatKeyForMap = null
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Selecione seu assento",
                        color = CustomColors.BrightPurple,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()), // Adicionado scroll para caber tudo
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Cabeçalho idêntico ao MoreInfoPage ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF3E5F5),
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = Color(0xFF9575CD),
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = driverName, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                    Text(
                        text = "Modelo do carro:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        color = Color(0xFF6200EE)
                    )
                    Text(
                        text = ride.vehicle_model.ifBlank { "Não informado" },
                        fontSize = 20.sp,
                        color = Color.DarkGray
                    )
                    val valorText = when (ride.payment_type) {
                        PaymentType.FREE -> "Cortesia"
                        PaymentType.NEGOTIABLE -> "A negociar"
                        PaymentType.PAY -> "R$ ${"%.2f".format(ride.ride_value)}"
                    }
                    Text(text = "Valor: $valorText", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Mapa Interativo ──
            // Passamos o estado local 'selectedSeatKeyForMap' para controlar o brilho verde
            Box(modifier = Modifier.size(280.dp, 400.dp), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.car_detailspage),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )

                // Passageiros Traseiros
                Row(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val trasKeys = listOf("carona-trás-esquerda", "carona-trás-meio", "carona-trás-direita")
                    trasKeys.forEach { key ->
                        SeatIcon(
                            label = key,
                            occupant = ride.seats_map[key],
                            modifier = Modifier.size(50.dp),
                            isSelected = selectedSeatKeyForMap == key,
                            onCLick = {
                                selectedSeatKeyForMap = key
                                showConfirmModal = true
                            }
                        )
                    }
                }

                // Motorista
                SeatIcon(
                    label = "motorista",
                    occupant = ride.driver_ref,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 70.dp, bottom = 130.dp).size(50.dp),
                    isSelected = false,
                    onCLick = {}
                )

                // Carona Frente
                SeatIcon(
                    label = "carona-frente",
                    occupant = ride.seats_map["carona-frente"],
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 65.dp, bottom = 130.dp).size(50.dp),
                    isSelected = selectedSeatKeyForMap == "carona-frente",
                    onCLick = {
                        selectedSeatKeyForMap = "carona-frente"
                        showConfirmModal = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Legenda
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                LegendItem("Ocupado", Color(0xFF424242))
                LegendItem("Disponível", Color(0xFF9575CD))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Card de Descrição idêntico ao MoreInfoPage ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Descrição:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF6200EE))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = ride.description.ifBlank { "Nenhuma." }, fontSize = 14.sp, color = Color.DarkGray)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showConfirmModal && selectedSeatKeyForMap != null) {
        ModalBottomSheet(
            onDismissRequest = { showConfirmModal = false },
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Confirmar Assento", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF6200EE))
                Spacer(Modifier.height(12.dp))
                Text("Você selecionou o assento:", fontSize = 16.sp, color = Color.Gray)
                Text(
                    selectedSeatKeyForMap!!.replace("-", " ").uppercase(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = Color.Black
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        user?.let { currentUser ->
                            viewModel.sendRideSolicitation(
                                ride = ride,
                                rideId = ride.id,
                                passengerId = currentUser.uid,
                                tempSelectedSeat = selectedSeatKeyForMap!!
                            )
                            showConfirmModal = false
                            Toast.makeText(context, "Solicitação enviada!", Toast.LENGTH_SHORT).show()
                            navController.popBackStack(Route.Map, inclusive = false)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A148C)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Confirmar Agendamento", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { showConfirmModal = false }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Escolher outro lugar", color = Color.Gray)
                }
            }
        }
    }
}