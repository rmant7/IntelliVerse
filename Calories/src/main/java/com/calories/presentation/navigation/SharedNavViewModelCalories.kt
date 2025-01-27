package com.calories.presentation.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SharedNavViewModelCalories @Inject constructor() : ViewModel() {
    var lastMathRoute: String? = null
    // for each type of screen, keeps the last version of that screen that was visited
    val lastScreensVersions = mutableStateListOf<Screen>()
}