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
import com.example.shared.domain.usecases.ai.OpenAiUseCase
import com.example.shared.domain.usecases.AudioPlayer
import com.example.shared.domain.usecases.ImageUtils
import com.example.shared.ads.InterstitialAdUseCase
import com.calories.presentation.screens.output.SharedViewModel
import com.example.shared.domain.prompt.options.ExplanationLevelOption
import com.example.shared.presentation.screens.AIService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject


@HiltViewModel
class ResultViewModel @Inject constructor(
    private val imageUtils: ImageUtils,
    private val googleDisk: GoogleDisk,
    private val geminiUseCaseClient: GeminiUseCaseClient,
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
    private var passedProperties: String = ""
    private var filePath = ""

    /** Solutions max capacity */
    private var maxSolutionResultsCapacity = 0
    private val geminiAttempts: AtomicInteger = AtomicInteger(0)

    init {
        val selectedLanguageIndex = savedStateHandle.get<Int>("selectedLanguageIndex") ?: 0
        this.selectedLanguage = SolutionLanguageOption.getByIndex(selectedLanguageIndex)
        this.languageInstruction = "Provide answers in ${selectedLanguage.languageName}."

        val locale = Locale.forLanguageTag(selectedLanguage.languageTag)
        speechConverter.setLanguage(locale)
        speechConverter.setOnUtteranceFinished { addFile(it) }

        savedStateHandle.get<String>("userTask")?.let { userTask = it }
        savedStateHandle.get<String>("detailsLevel")?.let { detailsLevel = it }
        savedStateHandle.get<String>("passedEditedOcr")?.let { passedProperties = it }
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
            .append("You have the following properties: $properties\n\n")
            .append("Given these properties:\n")
            .append("$basicTasks\n")
            .append(
                when (detailsLevel) {
                    ExplanationLevelOption.DETAILED_EXPLANATION.detailsLevel -> {
                        "- Show the solutions step-by-step and explain the assumptions and reasoning $detailsLevel.\n"
                    }
                    ExplanationLevelOption.NO_EXPLANATION.detailsLevel -> {
                        "- Provide final solutions only.\n"
                    }
                    ExplanationLevelOption.SHORT_EXPLANATION.detailsLevel -> {
                        "- Provide solutions briefly.\n"
                    }
                    else -> { throw IllegalStateException("Illegal details level inside ResultViewModel") }
                }
            )
            .append("- Provide macronutrient details (protein, fat, and carbohydrates), micronutrient details (vitamins, minerals) for the foods based on typical nutritional information.\n")
            .append("$resultAtTop $clearFormatting")

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
            geminiAttempts.set(1)
            geminiWithinApp()
            val result = googleDisk.upload(passedImageUri!!)
            result.onSuccess { uploadResponse ->
                imageURL = uploadResponse.fileUrl
                filePath = uploadResponse.filePath
                gpt()
            }
            result.onFailure {
                onSolutionResult(Result.failure(UnableToAssistException), AIService.GPT)
            }
        } else {
            geminiAttempts.set(2)
            val prompt = buildSolvingPrompt(userTask)
            launch(Dispatchers.IO) {
                val geminiResultClient = geminiUseCaseClient.generateGeminiSolution(
                    prompt = prompt,
                    systemInstruction = languageInstruction,
                    modelName = GeminiApiService.GeminiModel.GEMINI_FLASH_2_0_EXP
                )
                onSolutionResult(geminiResultClient, AIService.GEMINI)
            }
            geminiServer("$prompt $languageInstruction")
            launch(Dispatchers.IO) {
                val openAiResult = openAiUseCase.generateOpenAiSolution(
                    fileURL = null,
                    prompt = prompt,
                    systemInstruction = languageInstruction
                )
                onSolutionResult(openAiResult, AIService.GPT)
            }
        }
    }

    private fun geminiWithinApp() = viewModelScope.launch(Dispatchers.IO) {
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
        var recognizedProperties = ""
        if (passedProperties.isBlank() && sharedViewModel.ocrResults.value[AIService.GEMINI].isNullOrBlank()) {
            val result = geminiUseCaseClient.generateGeminiSolution(
                generativeLanguageUrl = listOf(generativeLanguageURL),
                prompt = ocrPrompt,
                systemInstruction = "$languageInstruction $noSummaries",
                modelName = GeminiApiService.GeminiModel.GEMINI_FLASH_2_0_EXP
            )
            result.onSuccess {
                sharedViewModel.updateOcrResults(
                    AIService.GEMINI,
                    it,
                    override = false
                )
                recognizedProperties = it
            }
            result.onFailure {
                onSolutionResult(result, AIService.GEMINI)
                return@launch
            }
        } else {
            recognizedProperties = passedProperties.ifBlank {
                sharedViewModel.ocrResults.value[AIService.GEMINI]!!
            }
        }
        val clientResult = geminiUseCaseClient.generateGeminiSolution(
            prompt = buildSolvingPrompt(
                passedProperties.ifBlank {
                    "${TextUtils.htmlToJsonString(recognizedProperties)} ${
                        addProperties(
                            userTask,
                            recognizedProperties.isNotBlank()
                        )
                    }"
                }
            ),
            systemInstruction = languageInstruction,
            modelName = GeminiApiService.GeminiModel.GEMINI_FLASH_2_0_EXP
        )
        onSolutionResult(clientResult, AIService.GEMINI)
    }

    /**
     * API call to Gemini using server.
     */
    private fun geminiServer(prompt: String) = viewModelScope.launch(Dispatchers.IO) {

        val endPoint = "https://moc.pythonanywhere.com/converse"
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(300, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .build()

        // JSON payload
        val jsonObject = JSONObject()
        jsonObject.put("text", prompt)

        val requestBody = jsonObject.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(endPoint)
            .post(requestBody)
            .build()

        val response: Response = httpClient.newCall(request).execute()

        if (response.isSuccessful) {
            val responseBody = response.body?.string()
            val result = responseBody?.let { JSONObject(responseBody).getString("response") }
            if (result != null) {
                onSolutionResult(Result.success(result), AIService.GEMINI)
            }
            else {
                onSolutionResult(Result.failure(com.example.shared.UnableToAssistException), AIService.GEMINI)
            }
        } else {
            onSolutionResult(Result.failure(com.example.shared.UnableToAssistException), AIService.GEMINI)
        }
    }

    private fun gpt() = viewModelScope.launch(Dispatchers.IO) {
        if (imageURL.isBlank()) {
            onSolutionResult(
                Result.failure(
                    UnableToAssistException
                ), AIService.GPT
            )
            return@launch
        }
        var recognizedProperties = ""
        if (passedProperties.isBlank() && sharedViewModel.ocrResults.value[AIService.GPT].isNullOrBlank()) {
            val result = openAiUseCase.generateOpenAiSolution(
                fileURL = imageURL,
                prompt = ocrPrompt,
                systemInstruction = "$languageInstruction $noSummaries"
            )
            result.onSuccess { sharedViewModel.updateOcrResults(AIService.GPT, it, override = false); recognizedProperties = it }
            result.onFailure { onSolutionResult(result, AIService.GPT); return@launch }
        } else {
            recognizedProperties = passedProperties.ifBlank {
                sharedViewModel.ocrResults.value[AIService.GPT]!!
            }
        }
        val result = openAiUseCase.generateOpenAiSolution(
            fileURL = null,
            prompt = buildSolvingPrompt(
                passedProperties.ifBlank {
                    "${TextUtils.htmlToJsonString(recognizedProperties)} ${
                        addProperties(
                            userTask,
                            recognizedProperties.isNotBlank()
                        )
                    }"
                }
            ),
            systemInstruction = languageInstruction
        )
        onSolutionResult(result, AIService.GPT)
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
            if (aiService == AIService.GEMINI) {
                val remainedAttempts = geminiAttempts.decrementAndGet()
                if (remainedAttempts == 0) {
                    // all gemini attempts have failed
                    updateSolutionResults(aiService, null)
                }
            } else {
                updateSolutionResults(aiService, null)
            }
            /*
            // maxSolutionResultsCapacity is not maintained
            updateErrors(it)
            if (errors.value.size >= maxSolutionResultsCapacity) {
                updateShouldShowErrorDialog(true)
            }*/
        }

        val progress = solutionResults.value.size.toFloat() / maxSolutionResultsCapacity
        updateSolutionProgress(progress)
    }
}