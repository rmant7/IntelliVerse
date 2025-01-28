package com.calories.presentation.screens.home

import com.example.shared.presentation.ExposedDropBox
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calories.R
import com.example.shared.domain.prompt.options.ExplanationLevelOption
import com.example.shared.domain.prompt.options.SolutionLanguageOption
import com.example.shared.presentation.common.ApplicationScaffold
import com.example.shared.presentation.common.StaticLabelTextField
import com.example.shared.presentation.common.button.RoundIconButton
import com.example.shared.presentation.common.button.UniversalButton
import com.example.shared.presentation.common.image.PictureItem
import java.util.Locale


@Composable
fun HomeScreenContent(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onNavigateToResultScreen: (Uri?, String, String, Int, Boolean) -> Unit
) {
    //val viewModel: HomeViewModel = hiltViewModel()
    val parameterScreenProperties =
        viewModel.parametersPropertiesState.collectAsStateWithLifecycle().value
    val allImageUris = viewModel.allImageUris.collectAsState()
    val selectedImageUri = viewModel.selectedUri.collectAsState()

    val context = LocalContext.current
    val lazyColumnState = rememberLazyListState()

    val selectImageOrTextWarningMessage = stringResource(R.string.select_image_or_text_warning)
    val corruptedUploadedFile = stringResource(R.string.corrupted_loaded_file)
    val userTextTask = viewModel.userTextTask.collectAsState()
    val placeHolderUserTaskText =
        stringResource(R.string.additional_info_TextField_placeholder_text_calories)
    val label = stringResource(R.string.food_properties)
    val secretShowAd = viewModel.secretShowAd.collectAsState()

    fun showWarning(message: String) {
        Toast.makeText(
            context,
            message, Toast.LENGTH_SHORT
        ).show()
    }

    fun checkImageValidity(onNavigate: () -> Unit) {
        when {

            selectedImageUri.value != null -> {

                val isUriValid =
                    viewModel.checkUriValidity(selectedImageUri.value!!)

                if (isUriValid) {
                    viewModel.updateSelectedUri(selectedImageUri.value)
                    onNavigate()
                } else showWarning(corruptedUploadedFile)

            }

            else -> {
                showWarning(selectImageOrTextWarningMessage)
            }
        }

    }

    fun onNextClick(onNavigate: () -> Unit) {
        if (userTextTask.value.isBlank())
            checkImageValidity(onNavigate)
        else onNavigate()
    }

    ApplicationScaffold(
        isShowed = true,
        content = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                state = lazyColumnState
            ) {
                item {
                    Column(
                        modifier = modifier
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        content = {
                            Text(
                                text = stringResource(R.string.Guidance),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * 1.2f,
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f,
                                    fontWeight = FontWeight.Bold
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            )

                            StaticLabelTextField(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp * 1.58f)
                                    .padding(top = 10.dp * 1.58f),
                                value = userTextTask.value,
                                onValueChange = {
                                    viewModel.updateUserTextTask(it)
                                },
                                placeholderText = placeHolderUserTaskText,
                                label = label
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RoundIconButton(
                                        modifier = Modifier.size(40.dp),
                                        icon = R.drawable.ic_camera,
                                        onButtonClick = {
                                            viewModel.updateSelectedUploadMethodOption(
                                                UploadFileMethodOptions.TAKE_A_PICTURE
                                            )
                                        }
                                    )
                                    RoundIconButton(
                                        modifier = Modifier.size(40.dp),
                                        icon = R.drawable.ic_add_image,
                                        onButtonClick = {
                                            viewModel.updateSelectedUploadMethodOption(
                                                UploadFileMethodOptions.UPLOAD_AN_IMAGE
                                            )
                                        }
                                    )
                                }

                                UniversalButton(
                                    modifier = Modifier.wrapContentWidth(),
                                    label = R.string.solve_button_label
                                ) {
                                    viewModel.onSolve()
                                    onNextClick {
                                        onNavigateToResultScreen(
                                            selectedImageUri.value,
                                            userTextTask.value,
                                            parameterScreenProperties.explanationLevel.detailsLevel,
                                            viewModel.getSelectedLanguage(),
                                            secretShowAd.value
                                        )
                                    }
                                }
                            }
                        })
                }

                itemsIndexed(allImageUris.value) { _, imageUri ->
                    val isSelected = imageUri == selectedImageUri.value

                    val imageModifier = Modifier
                        .clickable {
                            val uri = if (!isSelected) imageUri else null
                            viewModel.updateSelectedUri(uri)
                        }
                        .then(
                            if (isSelected) Modifier.border(
                                width = 4.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(16.dp)
                            ) else Modifier
                        )

                    PictureItem(
                        imageModifier = imageModifier,
                        imageUri = imageUri,
                        onEnlarge = { uri ->
                            viewModel.updateSelectedUri(uri)
                            viewModel.updateIsImageEnlarged(true)
                        },
                        onRemove = {
                            viewModel.removeImageFromTheList(imageUri)
                            if (selectedImageUri.value == imageUri)
                                viewModel.updateSelectedUri(null)
                        }
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        ExposedDropBox(
                            maxHeightIn = 400.dp,
                            label = R.string.solution_language_label,
                            selectedOption = parameterScreenProperties.language,
                            options = SolutionLanguageOption.entries.toList(),
                            onOptionSelected = {
                                val locale = Locale.forLanguageTag(it.languageTag)
                                val isLanguageSupported = viewModel.isLanguageSupported(locale)
                                if (!isLanguageSupported) {
                                    AlertDialog.Builder(context)
                                        .setTitle(R.string.install_language)
                                        .setMessage(R.string.language_not_supported)
                                        .setPositiveButton(R.string.install) { _, _ ->
                                            val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        }
                                        .setNegativeButton(R.string.cancel, null)
                                        .show()
                                }
                                viewModel.updateSelectedLanguageOption(it)
                            },
                            optionToString = { option, context -> option.getString(context) }
                        )

                        ExposedDropBox(
                            maxHeightIn = 200.dp,
                            label = R.string.explanations_label,
                            selectedOption = parameterScreenProperties.explanationLevel,
                            options = ExplanationLevelOption.entries.toList(),
                            onOptionSelected = {
                                viewModel.updateSelectedExplanationLevelOption(it)
                            },
                            optionToString = { option, context -> option.getString(context) }
                        )
                    }
                }
            }
        }
    )

}