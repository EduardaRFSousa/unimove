package com.dm.unimove.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.dm.unimove.model.MainViewModel
import com.dm.unimove.ui.pages.MapPage
import com.dm.unimove.ui.pages.ListPage
import com.dm.unimove.ui.pages.RidePage
import com.dm.unimove.ui.pages.menu.CreateRidePage
import com.dm.unimove.ui.pages.ridemodal.MoreInfoPage
import com.dm.unimove.ui.pages.ridemodal.RideSchedulePage

@Composable
fun MainNavHost(
    navController: NavHostController,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(navController, startDestination = Route.Map) {

        composable<Route.Map> {
            // ✅ Passa o navController do host para o MapPage
            MapPage(viewModel = viewModel, navController = navController)
        }

        composable<Route.Ride> {
            RidePage(viewModel = viewModel, navController = navController)
        }

        composable<Route.List> {
            ListPage(viewModel = viewModel, navController = navController)
        }

        composable<Route.CreateRide> {
            CreateRidePage(viewModel = viewModel, navController = navController)
        }

        composable<Route.RideDetails> { backStackEntry ->
            val details = backStackEntry.toRoute<Route.RideDetails>()
            val ride = viewModel.getRideById(details.rideId)

            if (ride != null) {
                MoreInfoPage(ride = ride, navController = navController, viewModel = viewModel)
            }
        }

        composable<Route.RideSchedule> { backStackEntry ->
            val schedule = backStackEntry.toRoute<Route.RideSchedule>()
            val ride = viewModel.getRideById(schedule.rideId)

            if (ride != null) {
                RideSchedulePage(ride = ride, navController = navController, viewModel = viewModel)
            }
        }
    }
}