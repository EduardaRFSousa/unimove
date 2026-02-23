package com.dm.unimove.ui.pages.ridemodal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dm.unimove.model.MainViewModel
import com.dm.unimove.ui.theme.CustomColors
import com.dm.unimove.model.PaymentType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreInfoPage(ride: Unit, navController: NavController, viewModel: MainViewModel) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Mais informações",
                        color = CustomColors.BrightPurple,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally

        ) {
            // Cabeçalho fixado com dados reais
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                /*TODO: Supondo que você tenha esse Composable ProfileImage
                * ProfileImage() */
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = "Motorista disponível",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Text(text = "Modelo: ${ride.vehicle_model}", color = Color(0xFF6200EE))
                    // Formatação do valor amigável
                    val valorText =
                        if (ride.payment_type == PaymentType.FREE) "Cortesia" else "R$ ${ride.ride_value}"
                    Text(text = "Valor: $valorText", fontWeight = FontWeight.SemiBold)
                }
            }

            InteractiveCarMap(ride = ride,
                viewModel = viewModel, // Adicione este parâmetro
                navController = navController, // Adicione este parâmetro
                isReadOnly = true,
                onSeatSelected = {})

            // Legenda Colorida do Figma
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                LegendItem("Ocupado", Color(0xFF424242)) // Cinza
                LegendItem("Disponível", Color(0xFF9575CD)) // Roxo
            }
        }
    }
}
