package com.dm.unimove.ui.pages.ridemodal

import com.dm.unimove.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.dm.unimove.model.Ride
import com.dm.unimove.model.MainViewModel

@Composable
fun SeatIcon(
    label: String,
    occupant: Any?, // Usando Any? para aceitar DocumentReference ou nulo
    modifier: Modifier,
    isSelected: Boolean,
    onCLick: () -> Unit
) {
    val isOccupied = occupant != null
    val imageRes = if (isOccupied) R.drawable.occupied_seat else R.drawable.empty_seat

    Image(
        painter = painterResource(id = imageRes),
        contentDescription = label,
        // Aplica um filtro verde se estiver selecionado
        colorFilter = if (isSelected) ColorFilter.tint(Color.Green, BlendMode.SrcAtop) else null,
        modifier = modifier
            .size(60.dp)
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

@Composable
fun InteractiveCarMap(
    ride: Ride, // CORREÇÃO: Definir o tipo explicitamente
    viewModel: MainViewModel, // Adicionado para acessar o selectSeat
    navController: NavController, // Adicionado para navegação
    isReadOnly: Boolean = false, // Valor padrão
    onSeatSelected: (String) -> Unit
) {
    var selectedSeatKey by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.size(320.dp, 450.dp), contentAlignment = Alignment.Center) {
        // 1. Base do Carro
        Image(
            painter = painterResource(id = R.drawable.car_detailspage),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Sobreposição
        // Motorista (Sempre ocupado)
        SeatIcon(
            label = "motorista",
            occupant = ride.driver_ref,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 75.dp, top = 160.dp),
            isSelected = false,
            onCLick = {}
        )

        // Carona Frente
        SeatIcon(
            label = "carona-frente",
            occupant = ride.seats_map["carona-frente"],
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 75.dp, top = 160.dp),
            isSelected = selectedSeatKey == "carona-frente",
            onCLick = {
                if (!isReadOnly) {
                    selectedSeatKey = "carona-frente"
                    viewModel.selectSeat("carona-frente") // Salva no VM
                    navController.navigate("confirmation/carona-frente") // Navega
                }
            }
        )

        // Assentos Traseiros
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val trasKeys = listOf("carona-trás-esquerda", "carona-trás-meio", "carona-trás-direita")
            trasKeys.forEach { key ->
                SeatIcon(
                    label = key,
                    occupant = ride.seats_map[key],
                    modifier = Modifier,
                    isSelected = selectedSeatKey == key,
                    onCLick = {
                        if (!isReadOnly) {
                            selectedSeatKey = key
                            viewModel.selectSeat(key)
                            navController.navigate("confirmation/${key}")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun RideSchedulePage(ride: Ride, navController: NavController, viewModel: MainViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Selecione seu assento", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF6200EE))

        InteractiveCarMap(
            ride = ride,
            viewModel = viewModel,
            navController = navController,
            isReadOnly = false,
            onSeatSelected = { seatKey ->
                viewModel.selectSeat(seatKey) // Salva a escolha
                navController.navigate("confirmation/${seatKey}") // Navega
            }
        )
    }
}