package com.calories.presentation.screens.output.result

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.BidiFormatter
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calories.BuildConfig
import com.calories.R
import com.example.shared.domain.usecases.TextUtils
import com.example.shared.presentation.common.ApplicationScaffold
import com.calories.presentation.common.MediaPlayer
import com.example.shared.presentation.common.button.RadioIndexButton
import com.example.shared.presentation.common.button.RoundIconButton
import com.example.shared.presentation.common.button.TextAlignmentButton
import com.example.shared.presentation.common.dialog.ErrorAlertDialog
import com.example.shared.presentation.common.web_view.HtmlTextView
import com.example.shared.presentation.common.web_view.cleanHtmlStr


@Composable
fun ResultScreen(
    viewModel: ResultViewModel,
    secretShowAd: Boolean,
    onNavigateToOcrScreen: () -> Unit,
) {
    val solutionResults = viewModel.solutionResults.collectAsStateWithLifecycle()
    val ocrResults = viewModel.sharedViewModel.ocrResults.collectAsStateWithLifecycle()
    val requestSolutionResponse = viewModel.requestSolutionResponse.collectAsState()

    val solutionTextDirection = viewModel.solutionTextDirection.collectAsState()

    val selectedSolutionService = viewModel.selectedSolutionService.collectAsState()
    val selectedOcrService = viewModel.sharedViewModel.selectedOcrService.collectAsState()
    val aiSolution by remember {
        derivedStateOf {
            solutionResults.value.getOrDefault(
                selectedSolutionService.value, ""
            ) ?: ""
        }
    }
    val ocrResult by remember {
        derivedStateOf {
            TextUtils.htmlToJsonString(ocrResults.value.getOrDefault(selectedOcrService.value, ""))
        }
    }
    val solutionProgress = viewModel.solutionProgress.collectAsState()

    val context = LocalContext.current
    val isWebViewReload = remember { mutableStateOf(false) }
    val webView: MutableState<WebView?> = remember { mutableStateOf(null) }

    val invalidSolutionText = stringResource(
        R.string.error_gemini_solution_result_extraction
    )
    val solutionIsNotSelected = stringResource(R.string.no_solution_chosen)
    val solutionsGenerationProgress = stringResource(R.string.progress_bar_hint_text_value)

    val solutionTextLabel = stringResource(R.string.solution_text_value)
    val recognizedTextLabel = stringResource(R.string.recognized_properties)
    val taskTextLabel = stringResource(R.string.user_task_value)
    val solvedBySchoolKiller = stringResource(R.string.solved_by_schoolkiller)

    val shouldShowErrorDialog = viewModel.shouldShowErrorDialog.collectAsState()
    val errors = viewModel.errors.collectAsState()

    // ad views count, ad plays every 2 clicks or on first try
    val shouldShowAd = viewModel.shouldShowAd.collectAsState()
    val interstitialAdViewCount = viewModel.adViewCount.collectAsState()

    val textToSpeechAudioFiles = viewModel.fileIds.collectAsState()
    val audioPlayer = viewModel.getAudioPlayer()

    var showShareDialog by remember { mutableStateOf(false) }
    var shareSolution by remember { mutableStateOf(true) }
    var shareImage by remember { mutableStateOf(true) }
    var shareOcrResult by remember { mutableStateOf(true) }
    var shareUserTask by remember { mutableStateOf(true) }


    if (requestSolutionResponse.value) {
        viewModel.stopUtterance() // stop generating speech
        audioPlayer.pause()
        audioPlayer.setNewFile(null)
        audioPlayer.changeTimeStamp(0f)

        isWebViewReload.value = true

        viewModel.generateSolutions()

        // Result is fetched and this block wouldn't run until new try request from user
        viewModel.updateRequestSolutionResponse(false)
    }

    /** Conditions to show ad:
     * - There are no solutions yet.
     * - It's user's first try or second "Try again" button's click. */
    if (secretShowAd && solutionResults.value.isEmpty()
        && shouldShowAd.value && interstitialAdViewCount.value == 0
    ) {
        viewModel.showInterstitialAd(context = context)
    }

    BackHandler {}

    fun textToShare(
        userTask: String?,
        ocrResult: String?,
        solution: String?
    ): String {
        val stringBuilder = StringBuilder()
        val bidiFormatter = BidiFormatter.getInstance()
        val delimiter = bidiFormatter.unicodeWrap("=".repeat(15))

        if (!userTask.isNullOrBlank()) {
            stringBuilder.append("$taskTextLabel:\n ${userTask.trim()}")
        }

        if (!ocrResult.isNullOrBlank()) {
            if (stringBuilder.isNotEmpty()) stringBuilder.append("\n$delimiter\n")
            stringBuilder.append("$recognizedTextLabel:\n ${ocrResult.trim()}")
        }

        if (!solution.isNullOrBlank()) {
            if (stringBuilder.isNotEmpty()) stringBuilder.append("\n$delimiter\n")
            stringBuilder.append("$solutionTextLabel:\n $solution")
        }
        stringBuilder.append("\n$delimiter\n$solvedBySchoolKiller: https://play.google.com/store/apps/details?id=com.calories")

        return stringBuilder.toString().trim()
    }

    fun evalTextAndShare(
        userTask: String?,
        ocrResult: String?,
        webView: WebView?,
        share: (String) -> Unit
    ) {
        if (webView == null) {
            share(textToShare(userTask, ocrResult, null))
        } else {
            webView.evaluateJavascript(
                "getPlainMathAndUrls()"
            ) { plainText ->
                val text = textToShare(userTask, ocrResult, cleanHtmlStr(plainText))
                share(text)
            }
        }
    }

    ApplicationScaffold(
        isShowed = true,
        content = {

            if (shouldShowErrorDialog.value) {

                fun onErrorFound() {
                    viewModel.updateShouldShowErrorDialog(false)
                }

                ErrorAlertDialog(
                    onDismissRequest = {
                        onErrorFound()
                    },
                    onConfirmation = {
                        onErrorFound()
                    },
                    errors = errors.value.toList(),
                    icon = Icons.Default.Info
                )
            }

            // Indicator how many AI responses were received.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    modifier = Modifier.padding(0.dp, 10.dp),
                    text = solutionsGenerationProgress,
                    fontSize = 20.sp
                )
                LinearProgressIndicator(
                    progress = { solutionProgress.value },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.65f),
                contentAlignment = Alignment.Center
            ) {

                val content = solutionResults.value[selectedSolutionService.value]
                val isEnabled = !content.isNullOrBlank()

                if (isEnabled) {
                    val index = solutionResults.value
                        .filter { (_, value) -> value != null }
                        .keys.indexOf(selectedSolutionService.value)

                    HtmlTextView(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RectangleShape),
                        htmlContent = content ?: invalidSolutionText,
                        isEditable = false,
                        textDirection = solutionTextDirection.value,
                        isReload = isWebViewReload.value,
                        title = "$solutionTextLabel ${index + 1}:${if (BuildConfig.DEBUG) " (${selectedSolutionService.value})" else ""}",
                        onWebViewCreated = { createdWebView ->
                            webView.value = createdWebView // Capture the WebView instance
                        }
                    )
                    isWebViewReload.value = false
                } else {
                    Text(
                        text = if (solutionProgress.value == 1f) {
                            invalidSolutionText
                        } else {
                            solutionIsNotSelected
                        },
                        fontSize = 20.sp
                    )
                }

            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(solutionResults.value
                    .filter { (_, value) -> value != null }
                    .keys.toList()
                ) { aiService ->
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RadioIndexButton(
                            isSelected = aiService == selectedSolutionService.value,
                            isEnabled = true,
                            onRadioButtonClick = {
                                if (selectedSolutionService.value != aiService) {
                                    viewModel.updateSelectedSolutionService(aiService)
                                    isWebViewReload.value = true
                                }
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {

                RoundIconButton(
                    icon = R.drawable.retry_svg,
                    onButtonClick = {
                        viewModel.updateRequestSolutionResponse(true)
                        viewModel.updateShouldShowAd(true)
                        viewModel.increaseInterstitialAdViewCount()
                    }
                )

                TextAlignmentButton(
                    layoutDirection = solutionTextDirection.value,
                    onUpdate = {
                        viewModel.updateSolutionTextDirection(it)
                    }
                )

                RoundIconButton(
                    icon = R.drawable.ic_document,
                    isEnabled = ocrResult.isNotBlank(),
                    onButtonClick = {
                        // send converted URL
                        onNavigateToOcrScreen()
                    }
                )

                val itemsToShare by remember {
                    derivedStateOf {
                        var tempCount = 0
                        if (viewModel.passedImageUri != null) tempCount++
                        if (viewModel.userTask.isNotBlank()) tempCount++
                        if (aiSolution.isNotBlank()) tempCount++
                        if (ocrResult.isNotBlank()) tempCount++
                        tempCount
                    }
                }

                RoundIconButton(
                    icon = Icons.Default.Share,
                    isEnabled = itemsToShare > 0,
                    onButtonClick = {
                        if (itemsToShare > 1) {
                            showShareDialog = true
                        } else {
                            evalTextAndShare(
                                viewModel.userTask,
                                ocrResult,
                                webView.value
                            ) {
                                shareContent(it, viewModel.passedImageUri, context)
                            }
                        }
                    }
                )

                if (showShareDialog) {
                    AlertDialog(
                        onDismissRequest = { showShareDialog = false },
                        title = { Text(stringResource(R.string.choose_sharing)) },
                        text = {
                            Column {
                                if (viewModel.userTask.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = shareUserTask,
                                            onCheckedChange = { shareUserTask = it }
                                        )
                                        Text(text = stringResource(R.string.share_user_task))
                                    }
                                }
                                if (ocrResult.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = shareOcrResult,
                                            onCheckedChange = { shareOcrResult = it }
                                        )
                                        Text(text = stringResource(R.string.share_ocr_result))
                                    }
                                }
                                if (aiSolution.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = shareSolution,
                                            onCheckedChange = { shareSolution = it }
                                        )
                                        Text(text = stringResource(R.string.share_solution))
                                    }
                                }
                                if (viewModel.passedImageUri != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = shareImage,
                                            onCheckedChange = { shareImage = it }
                                        )
                                        Text(text = stringResource(R.string.share_image))
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                showShareDialog = false
                                if (itemsToShare != 0) {
                                    val userTask = if (shareUserTask) viewModel.userTask else null
                                    val ocrText = if (shareOcrResult) ocrResult else null
                                    val imageUri = if (shareImage) viewModel.passedImageUri else null
                                    evalTextAndShare(
                                        userTask,
                                        ocrText,
                                        if (shareSolution) webView.value else null
                                    ) {
                                        shareContent(it, imageUri, context)
                                    }
                                }
                            }) {
                                Text(stringResource(R.string.share))
                            }
                        },
                        dismissButton = {
                            Button(onClick = { showShareDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }
            }

            val id = "solution_${selectedSolutionService.value?.ordinal}.wav"
            val filePath = "/storage/emulated/0/Android/data/com.calories/files/${id}"
            val isEnabled = textToSpeechAudioFiles.value.contains(id)
            MediaPlayer(
                viewModel,
                filePath = if (isEnabled) filePath else null,
                audioPlayer = audioPlayer,
                isEnabled = isEnabled
            )

        }
    )
}

private fun shareContent(text: String, imageUri: Uri?, context: Context) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        if (imageUri != null) {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "Shared Image", imageUri)
        }
        if (text.isNotBlank()) {
            if (imageUri == null) {
                type = "text/plain"
            }
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }
    require(shareIntent.type != null) { "No Uri nor text is supplied for sharing" }
    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
}