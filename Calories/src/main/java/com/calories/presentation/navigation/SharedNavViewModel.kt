package com.calories.presentation.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavBackStackEntry
import com.calories.presentation.screens.home.HomeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SharedNavViewModel @Inject constructor() : ViewModel() {
    var lastMathRoute: String? = null
    // for each type of screen, keeps the last version of that screen that was visited
    val lastScreensVersions = mutableStateListOf<Screen>()
}