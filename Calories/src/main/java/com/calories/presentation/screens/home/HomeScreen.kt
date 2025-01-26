package com.calories.presentation.screens.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.lifecycle.viewModelScope
import com.calories.R
import com.calories.presentation.common.dialog.EnlargedImageDialog
import com.calories.presentation.common.dialog.PermissionMessageRationale
import com.calories.presentation.common.dialog.PermissionRequestDialog
import com.calories.presentation.permissions.PermissionSet
import kotlinx.coroutines.launch
import timber.log.Timber


@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToResultScreen: (Uri?, String, String, Int, Boolean) -> Unit
) {

    //val viewModel: HomeViewModel = hiltViewModel()
    val selectedUploadMethodOption = viewModel.selectedUploadMethodOption.collectAsState()
    val isImageEnlarged = viewModel.isImageEnlarged.collectAsState()
    val selectedImageUri = viewModel.selectedUri.collectAsState()

    val context = LocalContext.current
    val dialogQueueList = viewModel.permissionDialogQueueList
    var isHomeScreenUIShowed by remember { mutableStateOf(false) }
    var launchGallery by remember { mutableStateOf<Boolean?>(null) }
    var launchCamera by remember { mutableStateOf<Boolean?>(null) }

    val somethingWentWrongMessage = stringResource(R.string.something_went_wrong)
    val imageFailedToLoad = stringResource(R.string.fail_to_load_Uri)
    val cameraFailedToOpen = stringResource(R.string.camera_fail_to_open)

    val isAppOpened by viewModel.isAppOpened.collectAsState()

    if (!isAppOpened) {
        if (viewModel.openAdUseCase.appOpenAd != null) {
            // If the ad is ready, show it immediately
            viewModel.showAd(context)
        } else {
            // Register a callback to show the ad when it’s ready
            viewModel.openAdUseCase.setAdLoadedCallback {
                viewModel.showAd(context)
            }
        }
        viewModel.markAppOpened()
    }

    /** Permission Launcher */
    val permissionResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { perms ->
            perms.keys.forEach { permission ->
                viewModel.onPermissionResult(
                    permission = permission,
                    isGranted = perms[permission] == true
                )
                if (perms.all { entry -> entry.value }) {
                    if (selectedUploadMethodOption.value == UploadFileMethodOptions.UPLOAD_AN_IMAGE) {
                        launchGallery = true
                    } else if (selectedUploadMethodOption.value == UploadFileMethodOptions.TAKE_A_PICTURE) {
                        launchCamera = true
                    }
                }
                viewModel.updateSelectedUploadMethodOption(UploadFileMethodOptions.NO_OPTION)
            }
        }
    )
    dialogQueueList
        .reversed()
        .forEach { permission ->
            PermissionRequestDialog(
                isPermanentlyDeclined = !shouldShowRequestPermissionRationale(
                    context as Activity,
                    permission
                ),
                onDismiss = viewModel::onDismissPermissionDialog,
                onGoToAppSettings = { context.openAppSettings() },
                onConfirm = {
                    permissionResultLauncher.launch(
                        arrayOf(permission)
                    )
                },
                permissionMessageRationale = when (permission) {
                    Manifest.permission.CAMERA -> {
                        PermissionMessageRationale.CameraPermissionMessage()
                    }

                    Manifest.permission.READ_MEDIA_IMAGES -> {
                        PermissionMessageRationale.ReadMediaPermissionMessage()
                    }

                    Manifest.permission.READ_EXTERNAL_STORAGE -> {
                        PermissionMessageRationale.ReadStoragePermissionMessage()
                    }

                    Manifest.permission.WRITE_EXTERNAL_STORAGE -> {
                        PermissionMessageRationale.WriteStoragePermissionMessage()
                    }

                    else -> return@forEach
                }
            )
        }


    /** Launcher for the Gallery */
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            try {
                viewModel.insertImagesOnTheList(uris)
            } catch (e: Exception) {
                Timber.w(e, "Image wasn't inserted to the list")
                Toast.makeText(context, somethingWentWrongMessage, Toast.LENGTH_SHORT).show()
            }
        }
    )

    LaunchedEffect(key1 = launchGallery) {
        if (launchGallery == true) {
            try {
                galleryLauncher.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            } catch (e: Exception) {
                Timber.w(e, "gallery launcher activity failed")
                Toast.makeText(context, imageFailedToLoad, Toast.LENGTH_SHORT).show()
            }
            launchGallery = null
        }

    }

    /** Launcher for the Camera */
    val cameraLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.viewModelScope.launch {
                    val uri = viewModel.getCameraSavedImageUri()
                    uri?.let { viewModel.insertImagesOnTheList(listOf(it)) } ?: run {
                        Toast.makeText(context, imageFailedToLoad, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    LaunchedEffect(key1 = launchCamera) {
        if (launchCamera == true) {
            val photoUri = viewModel.createImageUri()
            if (photoUri == null) {
                Toast.makeText(context, cameraFailedToOpen, Toast.LENGTH_SHORT).show()
                launchCamera = null
                return@LaunchedEffect // Exit early if URI creation fails
            }
            // instruct the camera where to save the image
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            try {
                cameraLauncher.launch(takePictureIntent)
            } catch (e: Exception) {
                Timber.w(e, "camera launcher activity failed")
                Toast.makeText(context, cameraFailedToOpen, Toast.LENGTH_SHORT).show()
            }
            launchCamera = null
        }
    }

    /** Enlarging the Image */
    if (isImageEnlarged.value) {

        EnlargedImageDialog(
            image = selectedImageUri.value,
            isImageEnlarged = isImageEnlarged.value,
            onDismiss = { viewModel.updateIsImageEnlarged(false) }
        )
        /*val isUriValid = selectedImageUri.value?.let { viewModel.checkUriValidity(it) }
        if (isUriValid == true) {
            EnlargedImageDialog(
                image = selectedImageUri.value,
                isImageEnlarged = isImageEnlarged.value,
                onDismiss = { viewModel.updateIsImageEnlarged(false) }
            )
        } else {
            viewModel.updateIsImageEnlarged(false)
            Toast.makeText(context, corruptedLoadedFile, Toast.LENGTH_SHORT).show()
        }*/
    }

    /** Button Cases */
    when (selectedUploadMethodOption.value) {

        UploadFileMethodOptions.TAKE_A_PICTURE -> {
            val cameraPermissionSet = PermissionSet().getCameraPermissionSet()
            permissionResultLauncher.launch(cameraPermissionSet)
        }

        UploadFileMethodOptions.UPLOAD_AN_IMAGE -> {
            val galleryPermissionSet = PermissionSet().getGalleryPermissionSet()
            permissionResultLauncher.launch(galleryPermissionSet)
        }

        UploadFileMethodOptions.UPLOAD_A_FILE -> {
            viewModel.updateSelectedUploadMethodOption(UploadFileMethodOptions.NO_OPTION)
            Toast.makeText(
                context,
                "Function is not ready yet",
                Toast.LENGTH_SHORT
            ).show()
        }

        UploadFileMethodOptions.PROVIDE_A_LINK -> {
            viewModel.updateSelectedUploadMethodOption(UploadFileMethodOptions.NO_OPTION)
            Toast.makeText(
                context,
                "Function is not ready yet",
                Toast.LENGTH_SHORT
            ).show()
        }

        UploadFileMethodOptions.NO_OPTION -> {
            isHomeScreenUIShowed = true
        }
    }

    BackHandler {}

    /** Home Screen main content */
    HomeScreenContent(
        viewModel = viewModel,
        onNavigateToResultScreen = onNavigateToResultScreen,
    )


}

fun Activity.openAppSettings() {
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).also(::startActivity)
}



