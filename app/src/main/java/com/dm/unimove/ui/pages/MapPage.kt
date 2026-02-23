package com.dm.unimove.ui.pages

import com.dm.unimove.R
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dm.unimove.model.MainViewModel
import com.dm.unimove.model.Ride
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

fun getResizedIcon(context: android.content.Context, resourceId: Int, width: Int, height: Int): BitmapDescriptor {
    val imageBitmap = BitmapFactory.decodeResource(context.resources, resourceId)
    val resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, false)
    return BitmapDescriptorFactory.fromBitmap(resizedBitmap)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPage(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    LaunchedEffect(Unit) {
        viewModel.fetchAvailableRides()
    }

    val context = LocalContext.current
    val rides by viewModel.availableRides
    val RECIFE_FALLBACK = LatLng(-8.0631, -34.8711)
    var selectedRideData by remember { mutableStateOf<Pair<String, Ride>?>(null) }
    val user = FirebaseAuth.getInstance().currentUser

    LaunchedEffect(user) {
        user?.let {
            viewModel.loadUserProfile(it.uid)
        }
    }
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    val isBusy = viewModel.user.value ?.is_busy ?: true
    val camPosState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(RECIFE_FALLBACK, 12f)
    }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            @SuppressLint("MissingPermission")
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val userLocation = LatLng(location.latitude, location.longitude)
                    camPosState.move(CameraUpdateFactory.newLatLngZoom(userLocation, 15f))
                } else {
                    camPosState.move(CameraUpdateFactory.newLatLngZoom(RECIFE_FALLBACK, 12f))
                }
            }
        } else {
            camPosState.move(CameraUpdateFactory.newLatLngZoom(RECIFE_FALLBACK, 12f))
        }
    }

    LaunchedEffect(user) {
        user?.let {
            viewModel.loadUserProfile(it.uid)
            viewModel.fetchUserSolicitations(it.uid)
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = camPosState,
        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
        uiSettings = MapUiSettings(myLocationButtonEnabled = true)
    ) {

        val sizeInPx = (48 * context.resources.displayMetrics.density).toInt() // 48dp transformado em pixels
        val carIcon = remember(context) {
            try {
                getResizedIcon(context, R.drawable.ic_car_marker, sizeInPx, sizeInPx)
            } catch (e: Exception) {
                null
            }
        }

        rides.forEach { (docId, ride) ->
            val position = LatLng(
                ride.starting_point.coordinates.latitude,
                ride.starting_point.coordinates.longitude
            )
            Marker(
                state = MarkerState(position = position),
                // Se carIcon for null, ele usa o marcador padrão do Google automaticamente
                icon = carIcon,
                onClick = {
                    selectedRideData = docId to ride
                    showBottomSheet = true
                    true
                }
            )
        }
    }

    if (showBottomSheet && selectedRideData != null) {
        val (currentRideId, ride) = selectedRideData!!
        val solicitadosIds by viewModel.userSolicitadosIds
        val jaSolicitou = solicitadosIds.contains(currentRideId)

        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) } // Estilo do figma
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Design do Modal similar ao Figma
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Ícone de Perfil Lilás circular
                    Surface(
                        shape = RoundedCornerShape(30.dp),
                        color = Color(0xFFF3E5F5),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp),
                            tint = Color(0xFF9575CD)
                        )
                    }

                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text(
                            text = "Motorista: Disponível",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Destino: ${ride.destination.name}",
                            color = Color(0xFF6200EE), // Roxo do design
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Ponto de partida: ${ride.starting_point.name}",
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )

                val dataHora = ride.date_time?.toDate()?.let {
                    java.text.SimpleDateFormat("dd/MM 'às' HH:mm", java.util.Locale.getDefault()).format(it)
                } ?: "Data não definida"

                Text(
                    text = "$dataHora, ${ride.occasion.name.lowercase().replace("_", " ")}",
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Botão "Mais informações" Azul claro
                Button(
                    onClick = { /* Detalhes */ },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF90CAF9)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("... Mais informações", color = Color.White, fontWeight = FontWeight.Bold)
                }

                // Botão "Agendar carona" Roxo escuro
                Button(
                    enabled = !isBusy && !jaSolicitou,
                    onClick = {
                        if (!isBusy) {
                            user?.let { currentUser ->
                                viewModel.sendRideSolicitation(ride, currentRideId, currentUser.uid)
                                showBottomSheet = false
                                Toast.makeText(context, "Solicitação enviada!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (jaSolicitou) Color.LightGray else Color(0xFF4A148C)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = if (jaSolicitou) Icons.Default.Close else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (jaSolicitou) "Solicitação já enviada" else "Agendar carona",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}