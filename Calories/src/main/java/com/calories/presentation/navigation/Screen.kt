package com.calories.presentation.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.calories.R
import kotlinx.serialization.Serializable

internal fun sanitize(text: String): String {
    // Allow letters (from all the languages), digits, math symbols, and common punctuation
    return text.replace(Regex("[^\\p{L}\\p{N}\\p{P}\\p{S}\\s%]"), "")
}

@Serializable
sealed class Screen {

    abstract fun createRoute(): String
    abstract fun templateRoute(): String
    abstract val labelId: Int
    abstract val imageVector: Any
    abstract val index: Int

    @Serializable
    data object Home : Screen() {

        private val prefixRoute: String
            get() = "home"

        override fun createRoute(): String = prefixRoute
        override fun templateRoute(): String = prefixRoute

        override val labelId: Int
            get() = R.string.home
        override val imageVector: ImageVector
            get() = Icons.Filled.Home
        override val index: Int
            get() = 0
    }

    @Serializable
    data class Result(
        val passedImageUri: String,
        val passedEditedOcr: String,
        val userTask: String,
        val detailsLevel: String,
        val selectedLanguageIndex: Int,
        val secretShowAd: Boolean
    ) : Screen() {

        override fun createRoute(): String {
            return "$prefixRoute/${Uri.encode(passedImageUri)}/${Uri.encode(passedEditedOcr)}/${
                Uri.encode(
                    sanitize(userTask)
                )
            }/${Uri.encode(detailsLevel)}/$selectedLanguageIndex/$secretShowAd"
        }

        override fun templateRoute(): String {
            return "$prefixRoute/{passedImageUri}/{passedEditedOcr}/{userTask}/{detailsLevel}/{selectedLanguageIndex}/{secretShowAd}"
        }

        override val labelId: Int
            get() = R.string.results
        override val imageVector: Int
            get() = R.drawable.ic_ai_brain

        override val index: Int
            get() = 1

        companion object {
            val index: Int
                get() = 1
            val prefixRoute: String
                get() = "result"
        }
    }

    @Serializable
    data object Ocr : Screen() {

        private val prefixRoute: String
            get() = "ocr"

        override fun createRoute(): String = prefixRoute

        override fun templateRoute(): String = prefixRoute

        override val labelId: Int
            get() = R.string.ocr
        override val imageVector: Int
            get() = R.drawable.ic_document

        override val index: Int
            get() = 2
    }
}

fun MutableList<Screen>.updateOrInsert(screen: Screen) {
    if (screen.index in indices) {
        // Update the existing element
        this[screen.index] = screen
    } else {
        // Insert a new element
        this.add(screen.index, screen)
    }
}

