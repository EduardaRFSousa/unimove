package com.dm.unimove.ui.pages

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dm.unimove.model.MainViewModel
import com.dm.unimove.model.Occasion
import com.dm.unimove.model.Ride
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavController
import com.dm.unimove.R
import com.dm.unimove.model.RideStatus
import com.dm.unimove.ui.nav.Route
import com.google.firebase.auth.FirebaseAuth

@Composable
fun RideHistoryItem(
    ride: Ride,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Imagem do carro ao lado (usando o ic_car_marker ou similar)
        Image(
            painter = painterResource(id = R.drawable.ic_car_marker),
            contentDescription = "Carro",
            modifier = Modifier.size(70.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Veículo: ${ride.vehicle_model}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = "Destino: ${ride.destination.name}",
                color = Color(0xFF6200EE),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Partida: ${ride.starting_point.name}",
                fontSize = 13.sp,
                color = Color.DarkGray
            )

            val dateStr = ride.date_time?.toDate()?.let {
                SimpleDateFormat("dd/MM 'às' HH:mm", Locale.getDefault()).format(it)
            } ?: ""

            Text(
                text = "$dateStr, ${if(ride.occasion == Occasion.ONE_WAY) "somente ida" else "ida e volta"}",
                fontSize = 13.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF90CAF9),
                modifier = Modifier.clickable {
                    navController.navigate(Route.RideDetails(rideId = ride.id))
                }
            ) {
                Text(
                    text = "••• Mais informações",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun ListPage(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    navController: NavController
) {
    val allRides by viewModel.availableRides
    val solicitadosIds by viewModel.userSolicitadosIds
    val userUid = FirebaseAuth.getInstance().currentUser?.uid

    // 1. CARONA ATUAL
    val currentRide = allRides.filter { (_, ride) ->
        val isActive = ride.status == RideStatus.ON_GOING || ride.status == RideStatus.AVAILABLE

        // Verifica se o usuário logado é o motorista
        val isDriver = ride.driver_ref?.id == userUid

        // Verifica se o usuário logado está na lista de passageiros
        val isPassenger = ride.passenger_refs.any { it.id == userUid }

        isActive && (isDriver || isPassenger)
    }

    // 2. SOLICITAÇÕES (Só aparecem se NÃO houver carona atual)
    val solicitations = if (currentRide.isEmpty()) {
        allRides.filter { (id, _) -> solicitadosIds.contains(id) }
    } else {
        emptyList()
    }

    // 3. CARONAS ANTERIORES
    val pastRides = allRides.filter { (_, ride) ->
        (ride.status == RideStatus.FINISHED || ride.status == RideStatus.CANCELED) &&
                ride.passenger_refs.any { it.id == userUid }
    }

    // Lógica para verificar se a página inteira está vazia
    val isEmpty = currentRide.isEmpty() && solicitations.isEmpty() && pastRides.isEmpty()

    if (isEmpty) {
        EmptyHistoryState(modifier)
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(top = 16.dp)
        ) {
            if (currentRide.isNotEmpty()) {
                item { SectionHeader("Carona em Andamento") }
                items(currentRide) { (_, ride) ->
                    RideHistoryItem(ride = ride,
                        navController = navController)
                    Divider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                }
            }

            if (solicitations.isNotEmpty()) {
                item { SectionHeader("Minhas Solicitações") }
                items(solicitations) { (_, ride) ->
                    RideHistoryItem(ride = ride,
                        navController = navController)
                    Divider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                }
            }

            if (pastRides.isNotEmpty()) {
                item { SectionHeader("Caronas Anteriores") }
                items(pastRides) { (_, ride) ->
                    RideHistoryItem(ride = ride,
                        navController = navController)
                    Divider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_car_marker), // Usando seu ícone de carro
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = Color.LightGray
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Você ainda não possui histórico de caronas!",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Column {
        Text(
            text = title,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF6200EE)
        )
        Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = Color(0xFF6200EE))
    }
}