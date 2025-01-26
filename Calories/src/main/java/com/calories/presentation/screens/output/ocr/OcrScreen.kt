package com.calories.presentation.screens.output.ocr

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calories.BuildConfig
import com.calories.R
import com.calories.presentation.common.ApplicationScaffold
import com.calories.presentation.common.button.RadioIndexButton
import com.calories.presentation.common.button.TextAlignmentButton
import com.calories.presentation.common.button.UniversalButton
import com.calories.presentation.common.dialog.ErrorAlertDialog
import com.calories.presentation.common.web_view.HtmlTextView
import com.calories.presentation.common.web_view.cleanHtmlStr


@Composable
fun OcrScreenContent(
    viewModel: OcrViewModel,
    onNavigateToResultScreen: () -> Unit
) {

    val selectedOcrService = viewModel.sharedViewModel.selectedOcrService.collectAsState()
    val ocrResults = viewModel.sharedViewModel.ocrResults.collectAsStateWithLifecycle()
    val isWebViewReload = remember { mutableStateOf(false) }
    // Get the height of the IME (keyboard) in pixels
    val imeHeightPx = WindowInsets.ime.getBottom(LocalDensity.current)
    val imeVisible = imeHeightPx > 0

    val focusManager = LocalFocusManager.current
    val webView: MutableState<WebView?> = remember { mutableStateOf(null) }
    val isOcrEdited = remember { mutableStateOf(false) }

    val ocrError = viewModel.error.collectAsState()
    val shouldShowErrorMessage = remember { mutableStateOf(true) }

    val textDirection = viewModel.ocrTextDirection.collectAsState()
    val recognizedTextLabel = stringResource(R.string.recognized_text_value)

    BackHandler {}

    ApplicationScaffold(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    focusManager.clearFocus() // Clear focus only when tapping outside
                }
            },
        isShowed = true,
        content = {

            if (ocrError.value != null && shouldShowErrorMessage.value) {
                ErrorAlertDialog(
                    onDismissRequest = {
                        shouldShowErrorMessage.value = false
                    },
                    onConfirmation = {
                        shouldShowErrorMessage.value = false
                        viewModel.updateError(null)
                        onNavigateToResultScreen()
                    },
                    errors = listOf(ocrError.value!!),
                    icon = Icons.Default.Info
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (imeVisible) 0.65f else 0.7f)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                val content = ocrResults.value[selectedOcrService.value]
                if (!content.isNullOrBlank()) {
                    HtmlTextView(
                        modifier = Modifier.fillMaxSize().clip(RectangleShape),
                        title = recognizedTextLabel + if (BuildConfig.DEBUG) " (${selectedOcrService.value})" else "",
                        htmlContent = content,
                        isEditable = true,
                        onValueChange = {
                            viewModel.sharedViewModel.updateOcrResults(
                                selectedOcrService.value!!,
                                it
                            )
                        },
                        isReload = isWebViewReload.value,
                        textDirection = textDirection.value,
                        onWebViewCreated = { createdWebView ->
                            webView.value = createdWebView // Capture the WebView instance
                        },
                        onEdit = { isOcrEdited.value = true }
                    )
                    isWebViewReload.value = false

                } else CircularProgressIndicator(modifier = Modifier.size(80.dp))
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(ocrResults.value
                    .filter { (_, value) -> value.isNotBlank() }
                    .keys.toList()
                ) { aiService ->
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RadioIndexButton(
                            isSelected = aiService == selectedOcrService.value,
                            isEnabled = true,
                            onRadioButtonClick = {
                                if (aiService != selectedOcrService.value) {
                                    // remember edited text so far
                                    webView.value?.evaluateJavascript("getContent()") { latestValue ->
                                        viewModel.sharedViewModel.updateOcrResults(
                                            selectedOcrService.value!!,
                                            cleanHtmlStr(latestValue)
                                        )
                                        viewModel.sharedViewModel.updateSelectedOcrService(aiService)
                                        // maybe required?
                                        isWebViewReload.value = true
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextAlignmentButton(
                    layoutDirection = textDirection.value!!,
                    onUpdate = {
                        viewModel.updateOcrTextDirection(it)
                    }
                )

                UniversalButton(
                    modifier = Modifier.wrapContentWidth(),
                    label = R.string.solve_button_label,
                    enabled = isOcrEdited.value
                ) {
                    webView.value?.evaluateJavascript("getContent()") { latestValue ->
                        val newOcrResult = cleanHtmlStr(latestValue)
                        // update all the ocr results to reflect the new edited ocr
                        // should consider from now on displaying only one of them
                        ocrResults.value.keys.forEach { aiService ->
                            viewModel.sharedViewModel.updateOcrResults(aiService, newOcrResult)
                        }
                        onNavigateToResultScreen() // Navigate with the latest value
                    }
                }
            }

        })
}