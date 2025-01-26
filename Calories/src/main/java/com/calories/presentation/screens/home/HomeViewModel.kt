package com.calories.presentation.screens.home

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shared.data.repositories.DataStoreRepository
import com.example.shared.data.repositories.DeleteFileRepository
import com.example.shared.data.repositories.SaveFileRepository
import com.example.shared.domain.model.ParameterProperties
import com.example.shared.domain.prompt.options.ExplanationLevelOption
import com.example.shared.domain.prompt.options.SolutionLanguageOption
import com.example.shared.domain.usecases.SpeechConverter
import com.example.shared.ads.OpenAdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val saveFileRepository: SaveFileRepository,
    private val deleteFileRepository: DeleteFileRepository,
    internal val openAdUseCase: OpenAdUseCase,
    private val dataStoreRepository: DataStoreRepository,
    private val speechConverter: SpeechConverter
) : ViewModel() {

    private val _isAppOpened = MutableStateFlow(false)
    val isAppOpened: StateFlow<Boolean> = _isAppOpened

    fun markAppOpened() {
        _isAppOpened.value = true
    }

    /* A secret way to disable ads- click the solve button 3 times
    in a short period of time.
    Mainly applicable if no image/user task was chosen, because otherwise you will
    be immediately directed to the ResultScreen. */
    private val _firstSolveClick = MutableStateFlow<Long?>(null)

    private val _secondSolveClick = MutableStateFlow<Long?>(null)

    private val _secretShowAd = MutableStateFlow(true)
    val secretShowAd: StateFlow<Boolean> = _secretShowAd

    fun onSolve() {
        val currTime = System.currentTimeMillis() / 1000
        if (_firstSolveClick.value == null || currTime - _firstSolveClick.value!! > 3) {
            _firstSolveClick.value = currTime
            _secondSolveClick.value = null
        } else {
            if (_secondSolveClick.value == null) {
                _secondSolveClick.value = currTime
            } else {
                toggleAd()
                _firstSolveClick.value = null
                _secondSolveClick.value = null
            }
        }
    }

    private fun toggleAd() {
        _secretShowAd.value = !_secretShowAd.value
    }

    fun isLanguageSupported(locale: Locale): Boolean = speechConverter.isLanguageSupported(locale)

    private val _userTextTask = MutableStateFlow("")
    val userTextTask: StateFlow<String> = _userTextTask

    private val _parametersPropertiesState = MutableStateFlow(ParameterProperties())
    val parametersPropertiesState: StateFlow<ParameterProperties> = _parametersPropertiesState
        .onStart {
            /** This is like the init block */
            readLanguageOptionState()
            readExplanationLevelOptionState()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ParameterProperties()
        )

    fun updateUserTextTask(userTextTask: String) {
        _userTextTask.update { userTextTask }
    }

    fun showAd(context: Context) {
        openAdUseCase.showOpenAppAd(context)
    }

    /** All images uris user selected from gallery */
    private val _allImageUris = MutableStateFlow<MutableList<Uri>>(mutableListOf())
    val allImageUris: StateFlow<MutableList<Uri>> = _allImageUris

    fun insertImagesOnTheList(newImages: List<Uri>?) {
        if (newImages.isNullOrEmpty()) return

        _allImageUris.update {
            _allImageUris.value.toMutableList().apply {
                if (this.isEmpty()) updateSelectedUri(newImages[0])
                this.addAll(newImages)
            }
        }
    }

    fun removeImageFromTheList(imageToDelete: Uri) {
        _allImageUris.update {
            _allImageUris.value.toMutableList().apply {
                this.remove(imageToDelete)
                if (this.isEmpty()) updateSelectedUri(null)
            }
        }
    }

    /** The last image uri user selected in HomeScreen */
    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri

    fun updateSelectedUri(selectedUri: Uri?) {
        _selectedUri.update { selectedUri }
    }

    /** SelectedUploadMethod: pick from gallery, take a picture etc */
    private val _selectedUploadMethodOption = MutableStateFlow(UploadFileMethodOptions.NO_OPTION)
    val selectedUploadMethodOption: StateFlow<UploadFileMethodOptions> = _selectedUploadMethodOption

    fun updateSelectedUploadMethodOption(selectedUploadMethodOption: UploadFileMethodOptions) {
        if (this.selectedUploadMethodOption.value != selectedUploadMethodOption)
            _selectedUploadMethodOption.update { selectedUploadMethodOption }
    }

    /** Should show dialog with enlarged image view or not */
    private val _isImageEnlarged = MutableStateFlow(false)
    val isImageEnlarged: StateFlow<Boolean> = _isImageEnlarged

    fun updateIsImageEnlarged(isImageEnlarged: Boolean) {
        _isImageEnlarged.update { isImageEnlarged }
    }

    /** Multiple permissions request */
    val permissionDialogQueueList = mutableStateListOf<String>()
    fun onDismissPermissionDialog() {
        permissionDialogQueueList.removeAt(0)
    }

    fun onPermissionResult(
        permission: String,
        isGranted: Boolean
    ) {
        if (!isGranted && !permissionDialogQueueList.contains(permission)) {
            permissionDialogQueueList.add(permission)
        }
    }

    suspend fun saveImage(uri: Uri, bitmap: Bitmap): Uri? {
        return withContext(Dispatchers.IO) {
            saveFileRepository.saveImage(uri, bitmap)
        }
    }

    suspend fun createImageUri(): Uri? {
        return withContext(Dispatchers.IO) {
            saveFileRepository.createImageUri()
        }
    }

    suspend fun getCameraSavedImageUri(): Uri? {
        return withContext(Dispatchers.IO) { saveFileRepository.getCameraSavedImageUri() }
    }

    fun checkUriValidity(uri: Uri): Boolean {
        return deleteFileRepository.checkUriValidity(uri)
    }

    fun updateSelectedLanguageOption(newLanguageSelection: SolutionLanguageOption) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(language = newLanguageSelection)
        }
        persistLanguageOptionState(newLanguageSelection)
    }

    fun updateSelectedExplanationLevelOption(newExplanationLevelSelection: ExplanationLevelOption) {
        _parametersPropertiesState.update { currentState ->
            currentState.copy(explanationLevel = newExplanationLevelSelection)
        }
        persistExplanationLevelOptionState(newExplanationLevelSelection)
    }

    private fun persistLanguageOptionState(solutionLanguageOption: SolutionLanguageOption) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistLanguageOptionState(languageOption = solutionLanguageOption)
        }
    }

    private fun persistExplanationLevelOptionState(explanationLevelOption: ExplanationLevelOption) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.persistExplanationLevelOptionState(explanationLevelOption = explanationLevelOption)
        }
    }

    private fun readLanguageOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readLanguageOptionState
                .map { SolutionLanguageOption.valueOf(it) }
                .catch { exception ->
                    Timber.e(exception, "Error reading language option state")
                    updateSelectedLanguageOption(SolutionLanguageOption.DEFAULT)
                }
                .collect { languageOption ->
                    updateSelectedLanguageOption(languageOption)
                }
        }
    }


    private fun readExplanationLevelOptionState() {
        viewModelScope.launch {
            dataStoreRepository.readExplanationLevelOptionState
                .map { ExplanationLevelOption.valueOf(it) }
                .catch { exception ->
                    Timber.e(exception, "Error reading explanation level option state")
                    updateSelectedExplanationLevelOption(ExplanationLevelOption.SHORT_EXPLANATION)
                }
                .collect { explanationLevelOption ->
                    updateSelectedExplanationLevelOption(explanationLevelOption)
                }
        }
    }

    fun getSelectedLanguage(): Int {
        return parametersPropertiesState.value.language.arrayIndex
    }

}