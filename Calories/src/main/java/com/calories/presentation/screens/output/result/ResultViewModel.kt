package com.calories.presentation.screens.output.result

import android.content.Context
import android.net.Uri
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calories.OutputSizeException
import com.calories.UnableToAssistException
import com.example.shared.data.network.gemini_api.client.GeminiApiService
import com.example.shared.data.network.google_cloud.GoogleDisk
import com.example.shared.domain.prompt.options.SolutionLanguageOption
import com.example.shared.domain.usecases.SpeechConverter
import com.example.shared.domain.usecases.TextUtils
import com.example.shared.domain.usecases.ai.client.GeminiUseCaseClient
import com.example.shared.domain.usecases.ai.GeminiUseCase
import com.example.shared.domain.usecases.ai.OpenAiUseCase
import com.example.shared.domain.usecases.AudioPlayer
import com.example.shared.domain.usecases.ImageUtils
import com.example.shared.ads.InterstitialAdUseCase
import com.calories.presentation.screens.output.SharedViewModel
import com.example.shared.presentation.screens.AIService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject


@HiltViewModel
class ResultViewModel @Inject constructor(
    private val imageUtils: ImageUtils,
    private val googleDisk: GoogleDisk,
    private val geminiUseCaseClient: GeminiUseCaseClient,
    private val geminiUseCase: GeminiUseCase,
    private val openAiUseCase: OpenAiUseCase,
    private val interstitialAdUseCase: InterstitialAdUseCase,
    private val speechConverter: SpeechConverter,
    private val audioPlayer: AudioPlayer,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var _sharedViewModel: SharedViewModel? = null
    internal val sharedViewModel: SharedViewModel
        get() = _sharedViewModel
            ?: throw IllegalStateException("SharedViewModel is not initialized")

    /** System instructions and OpenAI prompt*/
    private var selectedLanguage: SolutionLanguageOption
    private var languageInstruction = ""
    var userTask = ""
    private var detailsLevel = ""

    /** Passed image uri, Google Storage URL and GenerativeLanguage URL*/
    var passedImageUri: Uri? = null
    private var imageURL = ""
    private var generativeLanguageURL = ""
    private var filePath = ""
    private val imageInstruction = "When generating a response, prioritize the textual prompt provided over any cues or conflicting information inferred from the image. " +
            "The textual prompt serves as the primary source of instruction, and the image is only supplementary."

    /** Solutions max capacity */
    private var maxSolutionResultsCapacity = 0

    init {
        val selectedLanguageIndex = savedStateHandle.get<Int>("selectedLanguageIndex") ?: 0
        this.selectedLanguage = SolutionLanguageOption.getByIndex(selectedLanguageIndex)
        this.languageInstruction = "Provide answers in ${selectedLanguage.languageName}."

        val locale = Locale.forLanguageTag(selectedLanguage.languageTag)
        speechConverter.setLanguage(locale)
        speechConverter.setOnUtteranceFinished { addFile(it) }

        savedStateHandle.get<String>("userTask")?.let { userTask = it }
        savedStateHandle.get<String>("detailsLevel")?.let { detailsLevel = it }
        val uri = savedStateHandle.get<String>("passedImageUri")
        if (!uri.isNullOrBlank()) {
            passedImageUri = Uri.parse(uri)
        }

        // number of solution paths we (aim to) create
        maxSolutionResultsCapacity = 2
    }

    fun initSharedViewModel(sharedViewModel: SharedViewModel) {
        _sharedViewModel = sharedViewModel
    }

    private fun buildSolvingPrompt(properties: String): String {

        val solvingPrompt = StringBuilder()
            .append("$properties\n\n")
            .append("Given those details above:\n")
            .append("$basicTasks\n")
            .append("3. Show the solution step-by-step and explain the assumptions and reasoning $detailsLevel.\n")
            .append("4. Provide macronutrient details (calories, protein, fat, and carbohydrates) for the foods based on typical nutritional information.")

        return solvingPrompt.toString()
    }


    /** Text to speech converted audio files */
    // Ids for tracking synthesizing process of saved audio files.
    val fileIds = MutableStateFlow<MutableList<String>>(mutableListOf())

    private val _currentSpeedIndex = MutableStateFlow(1)
    val currentSpeedIndex: StateFlow<Int> = _currentSpeedIndex

    fun cycleSpeed(): Int {
        _currentSpeedIndex.value = (_currentSpeedIndex.value + 1) % playbackSpeeds.size
        return _currentSpeedIndex.value
    }

    companion object {
        val playbackSpeeds = listOf(0.5f, 1f, 1.5f, 2f)
    }

    private fun addFile(utteranceId: String?) {
        fileIds.update { files ->
            files.toMutableList().apply {
                utteranceId?.let { add(it) }
            }
        }
    }

    private fun removeFile(utteranceId: String?) {
        fileIds.update {
            fileIds.value.toMutableList().apply {
                this.remove(utteranceId)
            }
        }
    }

    fun getAudioPlayer(): AudioPlayer {
        return audioPlayer
    }

    fun stopUtterance() {
        speechConverter.stopUtterance()
    }

    /** Errors */
    private val _errors = MutableStateFlow<MutableList<Throwable>>(mutableListOf())
    val errors: StateFlow<MutableList<Throwable>> = _errors

    private fun updateErrors(error: Throwable) {
        _errors.update {
            it.apply { it.add(error) }
        }
    }

    private fun clearErrors() {
        _errors.update { mutableListOf() }
    }

    /** Should show error dialog condition */
    private val _shouldShowErrorDialog = MutableStateFlow(false)
    val shouldShowErrorDialog: StateFlow<Boolean> = _shouldShowErrorDialog

    fun updateShouldShowErrorDialog(shouldShowErrorDialog: Boolean) {
        _shouldShowErrorDialog.update { shouldShowErrorDialog }
    }

    /** Interstitial ad count */
    private val _adViewCount = MutableStateFlow(0)
    val adViewCount: StateFlow<Int> = _adViewCount

    private fun updateInterstitialAdViewCount(adViewCount: Int) {
        _adViewCount.update { adViewCount }
    }

    fun increaseInterstitialAdViewCount() {
        var currentVal = adViewCount.value
        val increasedNumber = currentVal + 1
        currentVal = if (increasedNumber >= 2) 0 else increasedNumber
        updateInterstitialAdViewCount(currentVal)
    }

    /** Show ad while waiting for solution results */
    fun showInterstitialAd(context: Context) {
        val isAdShown = interstitialAdUseCase.show(context)
        if (isAdShown) updateShouldShowAd(false)
    }

    private val _shouldShowAd = MutableStateFlow(true)
    val shouldShowAd: StateFlow<Boolean> = _shouldShowAd

    fun updateShouldShowAd(shouldShowAd: Boolean) {
        _shouldShowAd.update { shouldShowAd }
    }

    /** Update solution condition */
    private val _requestSolutionResponse = MutableStateFlow(true)
    val requestSolutionResponse: StateFlow<Boolean> = _requestSolutionResponse

    fun updateRequestSolutionResponse(requestSolutionResponse: Boolean) {
        _requestSolutionResponse.update { requestSolutionResponse }
    }

    /** Solution text direction */
    private val _solutionTextDirection =
        MutableStateFlow(TextUtils.getTextDirection(selectedLanguage))
    val solutionTextDirection: StateFlow<LayoutDirection> = _solutionTextDirection

    fun updateSolutionTextDirection(solutionTextDirection: LayoutDirection) {
        _solutionTextDirection.update { solutionTextDirection }
    }

    /** Selected solution service */
    private val _selectedSolutionService = MutableStateFlow<AIService?>(null)
    val selectedSolutionService: StateFlow<AIService?> = _selectedSolutionService

    fun updateSelectedSolutionService(selectedSolutionService: AIService?) {
        _selectedSolutionService.update { selectedSolutionService }
    }

    private val _solutionResults = MutableStateFlow<Map<AIService, String?>>(emptyMap())
    val solutionResults: StateFlow<Map<AIService, String?>> =
        _solutionResults.asStateFlow()

    private fun clearSolutionResults() {
        _solutionResults.update { emptyMap() }
    }

    private fun updateSolutionResults(
        aiService: AIService,
        resultText: String?
    ) {
        if (_selectedSolutionService.value == null && resultText != null) {
            updateSelectedSolutionService(aiService)
        }
        _solutionResults.update { oldMap ->
            oldMap + (aiService to resultText)
        }
    }

    /** Solution progress */
    private val _solutionProgress = MutableStateFlow(0f)
    val solutionProgress: StateFlow<Float> = _solutionProgress

    private fun updateSolutionProgress(solutionProgress: Float) {
        _solutionProgress.update { solutionProgress }
    }

    fun generateSolutions() = viewModelScope.launch(Dispatchers.IO) {
        clearSolutionResults()
        clearErrors()
        updateSelectedSolutionService(null)
        updateSolutionProgress(0.0f)

        val hasImage = passedImageUri != null

        if (hasImage) {
            val result = googleDisk.upload(passedImageUri!!)
            result.onSuccess { uploadResponse ->
                imageURL = uploadResponse.fileUrl
                filePath = uploadResponse.filePath
                geminiClient(generateServer = true)
                gpt()
            }
            result.onFailure {
                geminiClient(generateServer = false)
                onSolutionResult(Result.failure(UnableToAssistException), AIService.GPT_WITH_IMAGE)
            }
        } else {
            val prompt = buildSolvingPrompt(userTask)
            launch(Dispatchers.IO) {
                val geminiResultClient = geminiUseCaseClient.generateGeminiSolution(
                    prompt = prompt,
                    systemInstruction = "$languageInstruction $resultAtTop",
                    modelName = GeminiApiService.GeminiModel.GEMINI_FLASH_2_0_EXP
                )
                geminiResultClient.onSuccess {
                    onSolutionResult(
                        geminiResultClient,
                        AIService.GEMINI_TWO_WITH_TEXT
                    )
                }
                geminiResultClient.onFailure {
                    // backup- use the server
                    val geminiResultServer = geminiUseCase.processGeminiFile(
                        filePath = "",
                        userTask,
                        detailsLevel,
                        selectedLanguage.locale
                    )
                    geminiResultServer.onSuccess { geminiResult ->
                        onSolutionResult(
                            Result.success(geminiResult.answers),
                            AIService.GEMINI_TWO_WITH_TEXT
                        )
                    }
                    geminiResultServer.onFailure { geminiResult ->
                        onSolutionResult(
                            Result.failure(
                                geminiResult.cause ?: UnableToAssistException
                            ), AIService.GEMINI_TWO_WITH_TEXT
                        )
                    }
                }
            }
            launch(Dispatchers.IO) {
                val openAiResult = openAiUseCase.generateOpenAiSolution(
                    fileURL = null,
                    prompt = prompt,
                    systemInstruction = "$languageInstruction $resultAtTop"
                )
                onSolutionResult(openAiResult, AIService.GPT_WITH_TEXT)
            }
        }
    }

    private fun geminiClient(generateServer: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (generativeLanguageURL.isBlank()) {
            val fileByteArray = imageUtils.convertUriToByteArray(passedImageUri!!)
            if (fileByteArray != null) {
                val uploadResult = geminiUseCaseClient.getUploadedImageUrl(
                    fileByteArray = fileByteArray,
                    fileName = passedImageUri.toString()
                )
                uploadResult.onSuccess { generativeLanguageURL = it }
            }
        }
        lateinit var recognizedProperties: String
        if (sharedViewModel.ocrResults.value[AIService.GEMINI_TWO_WITH_TEXT].isNullOrBlank()) {
            val result = geminiUseCaseClient.generateGeminiSolution(
                generativeLanguageUrl = listOf(generativeLanguageURL),
                prompt = ocrPrompt,
                systemInstruction = "$languageInstruction $resultOnly",
                modelName = GeminiApiService.GeminiModel.GEMINI_FLASH_2_0_EXP
            )
            result.onSuccess {
                sharedViewModel.updateOcrResults(
                    AIService.GEMINI_TWO_WITH_TEXT,
                    it
                )
                recognizedProperties = it
            }
            result.onFailure {
                if (generateServer) {
                    // fallback to generate gemini solution and ocr using the server
                    geminiServer(it)
                } else {
                    onSolutionResult(result, AIService.GEMINI_TWO_WITH_IMAGE)
                }
                return@launch
            }
        } else {
            recognizedProperties = sharedViewModel.ocrResults.value[AIService.GEMINI_TWO_WITH_TEXT]!!
        }
        val clientResult = geminiUseCaseClient.generateGeminiSolution(
            prompt = buildSolvingPrompt(TextUtils.htmlToJsonString(recognizedProperties) + addProperties(userTask)),
            systemInstruction = "$languageInstruction $resultAtTop",
            modelName = GeminiApiService.GeminiModel.GEMINI_FLASH_2_0_EXP
        )
        onSolutionResult(clientResult, AIService.GEMINI_TWO_WITH_IMAGE)
    }

    private suspend fun geminiServer(clientFailure: Throwable) {
        if (filePath.isBlank()) {
            onSolutionResult(
                Result.failure(
                    IllegalStateException("Expected filePath to be not empty")
                ), AIService.GEMINI_TWO_WITH_IMAGE
            )
        }
        val geminiResultServer = geminiUseCase.processGeminiFile(filePath, userTask, detailsLevel, selectedLanguage.locale)
        geminiResultServer.onSuccess { geminiData ->
            sharedViewModel.updateOcrResults(AIService.GEMINI_TWO_WITH_TEXT, geminiData.extractedText)
            onSolutionResult(Result.success(geminiData.answers), AIService.GEMINI_TWO_WITH_IMAGE)
        }
        geminiResultServer.onFailure { serverResult ->
            onSolutionResult(
                Result.failure(
                    serverResult.cause ?: clientFailure.cause ?: UnableToAssistException
                ), AIService.GEMINI_TWO_WITH_IMAGE
            )
        }
    }

    private fun gpt() = viewModelScope.launch(Dispatchers.IO) {
        if (imageURL.isBlank()) {
            onSolutionResult(
                Result.failure(
                    UnableToAssistException
                ), AIService.GPT_WITH_IMAGE
            )
            return@launch
        }
        lateinit var recognizedProperties: String
        if (sharedViewModel.ocrResults.value[AIService.GPT_WITH_TEXT].isNullOrBlank()) {
            val result = openAiUseCase.generateOpenAiSolution(
                fileURL = imageURL,
                prompt = ocrPrompt,
                systemInstruction = "$languageInstruction $resultOnly"
            )
            result.onSuccess { sharedViewModel.updateOcrResults(AIService.GPT_WITH_TEXT, it); recognizedProperties = it }
            result.onFailure { return@launch }
        } else {
            recognizedProperties = sharedViewModel.ocrResults.value[AIService.GPT_WITH_TEXT]!!
        }
        val result = openAiUseCase.generateOpenAiSolution(
            fileURL = null,
            prompt = buildSolvingPrompt(TextUtils.htmlToJsonString(recognizedProperties) + addProperties(userTask)),
            systemInstruction = "$languageInstruction $resultAtTop"
        )
        onSolutionResult(result, AIService.GPT_WITH_IMAGE)
    }

    // When AI solution result fetched
    private fun onSolutionResult(
        result: Result<String>,
        aiService: AIService
    ) {

        if (_solutionResults.value.containsKey(aiService)) {
            return
        }

        var finalResult = result

        finalResult.onSuccess {
            // Filter by symbols amount.
            // But for some tasks Gemini replies in long essays why it can't solve task.
            // And some AI responses with less than 30 symbols can be real solutions.
            if (it.length < 100) {
                finalResult = Result.failure(OutputSizeException)
                return@onSuccess
            }

            // convert markdown to html
            val htmlString = TextUtils.markdownToHtml(it)
            updateSolutionResults(aiService, htmlString)

            // Replace existing or create new audio file.
            val fileName = "solution_${aiService.ordinal}.wav"
            removeFile(fileName)
            speechConverter.synthesizeToFile(it, fileName)
        }

        finalResult.onFailure {
            updateSolutionResults(aiService, null)
            updateErrors(it)
            if (errors.value.size >= maxSolutionResultsCapacity) {
                updateShouldShowErrorDialog(true)
            }
        }

        val progress = solutionResults.value.size.toFloat() / maxSolutionResultsCapacity
        updateSolutionProgress(progress)
    }
}