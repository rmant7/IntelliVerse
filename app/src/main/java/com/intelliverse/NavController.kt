package com.intelliverse

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.intelliverse.presentation.StartScreen
import com.schoolkiller.presentation.navigation.SharedNavViewModel


@Composable
fun Navigation() {
    val navController = rememberNavController()
    val sharedNavViewModel = viewModel<SharedNavViewModel>()
    val sharedNavViewModelCalories = viewModel<com.calories.presentation.navigation.SharedNavViewModel>()
    NavHost(navController = navController, startDestination = "start") {
        composable("start") { StartScreen(navController) }
        composable("schoolKiller") {
            com.schoolkiller.presentation.navigation.Navigation(
                navController,
                sharedNavViewModel
            )
        }
        composable("calories") {
            com.calories.presentation.navigation.Navigation(
                navController,
                sharedNavViewModelCalories
            )
        }
    }
}