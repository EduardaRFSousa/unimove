package com.dm.unimove.ui.pages.menu

import android.R.attr.text
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dm.unimove.model.Location
import com.dm.unimove.model.MainViewModel
import com.dm.unimove.model.Occasion
import com.dm.unimove.model.PaymentType
import com.dm.unimove.model.Ride
import com.dm.unimove.model.RideStatus
import com.dm.unimove.ui.theme.CustomColors
import com.dm.unimove.ui.theme.Montserrat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.android.gms.maps.model.LatLng

@Composable
fun LocationSelector(
    label: String,
    locationName: String,
    onNameChange: (String) -> Unit,
    currentLatLng: LatLng,
    isLocationSet: Boolean,
    showError: Boolean,
    onLocationSelected: (LatLng) -> Unit
) {
    var showMapDialog by remember { mutableStateOf(false) }

    Column {
        OutlinedTextField(
            value = locationName,
            onValueChange = onNameChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            isError = showError,
            placeholder = { Text("Ex: Entrada do IFPE") },
            supportingText = {
                if (isLocationSet) {
                    Text(
                        "📍 Coordenadas capturadas: ${"%.4f".format(currentLatLng.latitude)}, ${"%.4f".format(currentLatLng.longitude)}",
                        color = Color(0xFF4CAF50)
                    )
                } else {
                    Text("Clique no ícone ao lado para marcar no mapa")
                }
            },
            // Ícone que indica que o mapa pode ser aberto
            trailingIcon = {
                IconButton(onClick = { showMapDialog = true }) {
                    Icon(
                        imageVector = if (isLocationSet) Icons.Default.Check else Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = if (isLocationSet) Color(0xFF4CAF50)
                        else if (showError) MaterialTheme.colorScheme.error
                        else CustomColors.BrightPurple
                    )
                }
            }
        )

        if (showMapDialog) {
            MapSelectionDialog(
                initialLatLng = currentLatLng,
                onDismiss = { showMapDialog = false },
                onLocationConfirmed = {
                    onLocationSelected(it)
                    showMapDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRidePage(viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current

    var startLocationName by remember { mutableStateOf("") }
    var startCoords by remember { mutableStateOf(GeoPoint(0.0, 0.0)) }
    var startLatLng by remember { mutableStateOf(LatLng(-8.0476, -34.8770)) } // Recife como padrão

    var destLocationName by remember { mutableStateOf("") }
    var destCoords by remember { mutableStateOf(GeoPoint(0.0, 0.0)) }
    var destLatLng by remember { mutableStateOf(LatLng(-8.0476, -34.8770)) }

    var isStartLatLngSet by remember { mutableStateOf(false) } // Flag de validação
    var isDestLatLngSet by remember { mutableStateOf(false) }

    var selectedTimestamp by remember { mutableStateOf<Timestamp?>(null) }
    var selectedOccasion by remember { mutableStateOf(Occasion.ONE_WAY) }
    var selectedPayment by remember { mutableStateOf(PaymentType.FREE) }
    var rideValue by remember { mutableStateOf("0.0") }

    var totalSeats by remember { mutableStateOf("4") }
    var vehicleModel by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var attemptPublish by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Criar carona",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        color = CustomColors.BrightPurple,
                        fontSize = 20.sp,
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
        // CORREÇÃO: O LazyColumn deve envolver TODOS os items até o final da página
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // CORREÇÃO: Usando o padding do Scaffold
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                LocationSelector(
                    label = "Ponto de Partida",
                    locationName = startLocationName,
                    onNameChange = { startLocationName = it },
                    currentLatLng = startLatLng,
                    isLocationSet = isStartLatLngSet,
                    showError = attemptPublish && (startLocationName.isBlank() || !isStartLatLngSet),
                    onLocationSelected = {
                        startLatLng = it
                        isStartLatLngSet = true
                    }
                )
            }

            item {
                LocationSelector(
                    label = "Ponto de Destino",
                    locationName = destLocationName,
                    onNameChange = { destLocationName = it },
                    currentLatLng = destLatLng,
                    isLocationSet = isDestLatLngSet,
                    showError = attemptPublish && (destLocationName.isBlank() || !isDestLatLngSet),
                    onLocationSelected = {
                        destLatLng = it
                        isDestLatLngSet = true
                    }
                )
            }

            item {
                val dateError = attemptPublish && selectedTimestamp == null

                // Manter os diálogos aqui em cima para o botão conseguir acessá-los
                if (showDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showDatePicker = false
                                showTimePicker = true
                            }) { Text("Confirmar Data") }
                        }
                    ) { DatePicker(state = datePickerState) }
                }

                if (showTimePicker) {
                    AlertDialog(
                        onDismissRequest = { showTimePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val calendar = java.util.Calendar.getInstance()
                                datePickerState.selectedDateMillis?.let { calendar.timeInMillis = it }
                                calendar.set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                                calendar.set(java.util.Calendar.MINUTE, timePickerState.minute)
                                selectedTimestamp = Timestamp(calendar.time)
                                showTimePicker = false
                            }) { Text("OK") }
                        },
                        title = { Text("Selecione o Horário") },
                        text = { TimePicker(state = timePickerState) }
                    )
                }

                // O ÚNICO Botão de Data e Hora
                Column {
                    Button(
                        onClick = { showDatePicker = true }, // Agora ele volta a abrir o diálogo
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF1F0F5),
                            contentColor = if (dateError) Color.Red else Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = if (dateError) BorderStroke(2.dp, Color.Red) else null
                    ) {
                        val displayDate = selectedTimestamp?.toDate()?.let {
                            java.text.SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", java.util.Locale.getDefault()).format(it)
                        } ?: "Selecionar Data e Hora"

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(displayDate)
                        }
                    }

                    if (dateError) {
                        Text(
                            text = "Campo obrigatório",
                            color = Color.Red,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                    }
                }
            }

            // SWITCH DE OCASIÃO (Ida e Volta / Somente Ida)
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Occasion.entries.forEach { occ ->
                        val label = when (occ) {
                            Occasion.ONE_WAY -> "Somente Ida"
                            Occasion.ROUND_TRIP -> "Ida e Volta"
                        }
                        Button(
                            onClick = { selectedOccasion = occ },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedOccasion == occ) Color(0xFFE8E0FF) else Color.White,
                                contentColor = Color.Black
                            ),
                            shape = if (occ == Occasion.ONE_WAY)
                                RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                            else RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                            border = BorderStroke(1.dp, Color.LightGray)
                        ) {
                            if (selectedOccasion == occ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(label)
                        }
                    }
                }
            }

            // SWITCH DE PAGAMENTO (Cortesia / A negociar / A pagar)
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PaymentType.entries.forEach { pay ->
                        val label = when (pay) {
                            PaymentType.FREE -> "Cortesia"
                            PaymentType.PAY -> "A pagar"
                            PaymentType.NEGOTIABLE -> "A negociar"
                        }
                        Button(
                            onClick = { selectedPayment = pay },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedPayment == pay) Color(0xFFE8E0FF) else Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, Color.LightGray)
                        ) {
                            Text(label, fontSize = 12.sp)
                        }
                    }
                }

                if (selectedPayment == PaymentType.PAY) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rideValue,
                        onValueChange = { rideValue = it },
                        label = { Text("Valor (R$)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = vehicleModel,
                    onValueChange = { vehicleModel = it },
                    label = { Text("Modelo do Veículo") },
                    isError = attemptPublish && vehicleModel.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = totalSeats,
                    onValueChange = { totalSeats = it },
                    label = { Text("Total de Vagas") },
                    isError = attemptPublish && totalSeats.isBlank(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição/Recados") },
                    isError = attemptPublish && description.isBlank(),
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
            }

            item {
                Button(
                    onClick = {
                        attemptPublish = true // Isso dispara a borda vermelha em todos os componentes vazios

                        val isFormValid = startLocationName.isNotBlank() &&
                                destLocationName.isNotBlank() &&
                                isStartLatLngSet &&
                                isDestLatLngSet &&
                                vehicleModel.isNotBlank() &&
                                selectedTimestamp != null

                        if (!isFormValid) {
                            Toast.makeText(context, "Por favor, preencha todos os campos e selecione os locais no mapa!", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        val userUid = FirebaseAuth.getInstance().currentUser?.uid
                        if (userUid != null && viewModel.canUserStartNewActivity()) {
                            viewModel.updateUserBusyStatus(userUid, true)
                            val newRide = Ride(
                                driver_ref = FirebaseFirestore.getInstance().collection("USERS")
                                    .document(userUid),
                                starting_point = Location(
                                    startLocationName,
                                    GeoPoint(startLatLng.latitude, startLatLng.longitude)
                                ),
                                destination = Location(
                                    destLocationName,
                                    GeoPoint(destLatLng.latitude, destLatLng.longitude)
                                ),
                                date_time = selectedTimestamp ?: Timestamp.now(),
                                occasion = selectedOccasion,
                                payment_type = selectedPayment,
                                ride_value = rideValue.toDoubleOrNull() ?: 0.0,
                                total_seats = totalSeats.toIntOrNull() ?: 0,
                                vehicle_model = vehicleModel,
                                description = description,
                                status = RideStatus.AVAILABLE
                            )
                            viewModel.createNewRide(newRide)
                            navController.popBackStack()
                        } else {
                            Toast.makeText(context, "Você já possui uma carona em andamento!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CustomColors.BrightPurple)
                ) {
                    Text("Publicar Carona", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        } // FIM DO LazyColumn
    }
}