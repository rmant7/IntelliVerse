package com.calories.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.calories.R
import com.example.shared.presentation.common.ApplicationScaffold
import com.calories.presentation.screens.output.SharedViewModel
import com.calories.presentation.screens.home.HomeScreen
import com.calories.presentation.screens.home.HomeViewModel
import com.calories.presentation.screens.output.ocr.OcrScreenContent
import com.calories.presentation.screens.output.ocr.OcrViewModel
import com.calories.presentation.screens.output.result.ResultScreen
import com.calories.presentation.screens.output.result.ResultViewModel
import timber.log.Timber
import com.example.shared.presentation.AppTopBar


@Composable
fun Navigation(parentNavController: NavHostController, sharedNavViewModel: SharedNavViewModel) {
    val navController = rememberNavController()


    // for each type of screen, keeps the last version of that screen that was visited
    //val lastScreensVersions = rememberSaveable(saver = screenListSaver) { mutableStateListOf() }
    val lastScreensVersions = sharedNavViewModel.lastScreensVersions

    ApplicationScaffold(
        isShowed = true,
        content = {
            NavigationController(
                navController = navController,
                lastScreensVersions,
                sharedNavViewModel
            )
        },
        bottomBar = {
            BottomNavigationBar(navController, lastScreensVersions, sharedNavViewModel)
        },
        topBar = {
            AppTopBar(parentNavController, stringResource(R.string.app_name_calories))
        }
    )

}

@Composable
private fun NavigationController(
    navController: NavHostController,
    lastScreensVersions: MutableList<Screen>,
    sharedNavViewModel: SharedNavViewModel
) {

    val resultViewModelState: MutableState<NavBackStackEntry?> = remember {
        mutableStateOf(null)
    }

    val ocrViewModelState: MutableState<NavBackStackEntry?> = remember {
        mutableStateOf(null)
    }

    val sharedViewModelState: MutableState<NavBackStackEntry?> = remember {
        mutableStateOf(null)
    }

    val homeViewModel = hiltViewModel<HomeViewModel>()

    NavHost(
        navController = navController,
        startDestination =  /*sharedNavViewModel.lastMathRoute ?:*/  Screen.Home.createRoute(),
        // Default fadeIn() and fadeOut() effects might slow down transition
        // animation a little bit, but release apk is still much faster than debug one.
        enterTransition = {
            EnterTransition.None
        },
        exitTransition = {
            ExitTransition.None
        }
    ) {

        composable(Screen.Home.createRoute()) {

            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToResultScreen = { uri, userTask, detailsLevel, selectedLanguageIndex, secretShowAd ->
                    // removes the option to navigate to the ocr screen, as it becomes invalid
                    // when new results are computed
                    val ocrScreen = lastScreensVersions.find { it is Screen.Ocr }
                    ocrScreen?.let { lastScreensVersions.remove(it) }
                    ocrViewModelState.value = null

                    val encodedUri = uri?.toString() ?: ""
                    val nextScreen = Screen.Result(encodedUri, passedEditedOcr = "", userTask, detailsLevel, selectedLanguageIndex, secretShowAd)
                    val route = nextScreen.createRoute()
                    sharedNavViewModel.lastMathRoute = route
                    navController.navigate(route)
                    /** Update result view model state */
                    val backStackEntry = navController.getBackStackEntry(route)
                    resultViewModelState.value = backStackEntry
                    sharedViewModelState.value = backStackEntry
                }
            )
            lastScreensVersions.updateOrInsert(Screen.Home)
        }

        composable(
            route = "${Screen.Result.prefixRoute}/{passedImageUri}/{passedEditedOcr}/{userTask}/{detailsLevel}/{selectedLanguageIndex}/{secretShowAd}",
            arguments = listOf(
                navArgument("passedImageUri") { type = NavType.StringType },
                navArgument("passedEditedOcr") { type = NavType.StringType },
                navArgument("userTask") { type = NavType.StringType },
                navArgument("detailsLevel") { type = NavType.StringType },
                navArgument("selectedLanguageIndex") { type = NavType.IntType },
                navArgument("secretShowAd") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("passedImageUri") ?: ""
            val passedEditedOcr = backStackEntry.arguments?.getString("passedEditedOcr") ?: ""
            val userTask = backStackEntry.arguments?.getString("userTask") ?: ""
            val detailsLevel = backStackEntry.arguments?.getString("detailsLevel") ?: ""
            val selectedLanguageIndex =
                backStackEntry.arguments?.getInt("selectedLanguageIndex") ?: 0
            val secretShowAd = backStackEntry.arguments?.getBoolean("secretShowAd") ?: true

            val currScreen = Screen.Result(uri, passedEditedOcr, userTask, detailsLevel, selectedLanguageIndex, secretShowAd)
            if (resultViewModelState.value == null || sharedViewModelState.value == null) {
                Timber.d("restoring resultViewModelState and sharedViewModelState")
                val currentBackStackEntry = navController.currentBackStackEntry
                if (currentBackStackEntry == null) {
                    Timber.d("currentBackStackEntry is null, fallback to navigate to HomeScreen")
                    // not sure this code is even reachable, it's the fallback for the fallback
                    navController.navigate(Screen.Home.createRoute())
                    return@composable
                }
                resultViewModelState.value = currentBackStackEntry
                sharedViewModelState.value = currentBackStackEntry
            }

            val route = Screen.Ocr.createRoute()
            sharedNavViewModel.lastMathRoute = route
            ResultScreen(
                /** Scope viewModel to the saved state */
                viewModel = hiltViewModel<ResultViewModel>(resultViewModelState.value!!).apply {
                    initSharedViewModel(hiltViewModel<SharedViewModel>(sharedViewModelState.value!!))
                },
                secretShowAd,
                onNavigateToOcrScreen = {
                    navController.navigate(route)
                    /** Update ocr view model state */
                    ocrViewModelState.value = navController.getBackStackEntry(route)
                }
            )
            lastScreensVersions.updateOrInsert(currScreen)
        }

        composable(Screen.Ocr.createRoute()) {
            val currScreen = Screen.Ocr
            if (ocrViewModelState.value == null || sharedViewModelState.value == null) {
                Timber.d("restoring ocrViewModelState and sharedViewModelState")
                val currentBackStackEntry = navController.currentBackStackEntry
                if (currentBackStackEntry == null) {
                    Timber.d("currentBackStackEntry is null, fallback to navigate to HomeScreen")
                    // not sure this code is even reachable, it's the fallback for the fallback
                    navController.navigate(Screen.Home.createRoute())
                    return@composable
                }
                ocrViewModelState.value = currentBackStackEntry
                sharedViewModelState.value = currentBackStackEntry
            }

            OcrScreenContent(
                viewModel = hiltViewModel<OcrViewModel>(ocrViewModelState.value!!).apply {
                    initSharedViewModel(hiltViewModel<SharedViewModel>(sharedViewModelState.value!!))
                },
                onNavigateToResultScreen = {
                    val resultScreen = lastScreensVersions[Screen.Result.index]
                    val route = resultScreen.createRoute()
                    sharedNavViewModel.lastMathRoute = route
                    navController.navigate(route)
                    resultViewModelState.value = navController.getBackStackEntry(route)
                }
            )
            lastScreensVersions.updateOrInsert(currScreen)
        }
    }

}

// Using Material 3 instead of Material for less apk size.
@Composable
fun BottomNavigationItem(
    label: String = "",
    icon: Any,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {

    val iconPainter:Painter = when (icon) {
        is Int -> painterResource(icon)
        is ImageVector -> rememberVectorPainter(icon)
        else -> rememberVectorPainter(Icons.Default.Clear)
    }

    IconButton(
        modifier = Modifier.size(35.dp),
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = label,
            modifier = Modifier.fillMaxSize(),
            tint = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }

}

@Composable
fun BottomNavigationBar(
    navController: NavHostController,
    lastScreensVersions: MutableList<Screen>,
    sharedNavViewModel: SharedNavViewModel
) {

    val currentDestination = navController.currentBackStackEntryAsState().value?.destination?.route
    // for devices with low API level, padding is required
    val insets = WindowInsets.systemBars
    val bottomPadding = with(LocalDensity.current) { insets.getBottom(this).toDp() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        lastScreensVersions.forEach { screen ->
            val isSelected = currentDestination == screen.templateRoute()
            val route = screen.createRoute()
            sharedNavViewModel.lastMathRoute = route
            BottomNavigationItem(
                icon = screen.imageVector,
                isSelected = isSelected,
                onClick = {
                    if (!isSelected) {
                        navController.navigate(route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }

}