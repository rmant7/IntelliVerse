package com.example.shared.data.network.google_cloud

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.shared.domain.usecases.ImageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class UploadResponse(
    @SerialName("file_url") val fileUrl: String,
    @SerialName("file_path") val filePath: String,
    @SerialName("extracted_text") val extractedText: String,
    @SerialName("mime_type") val mimeType: String
) {
    companion object {
        fun fromJson(json: String): UploadResponse {
            return Json.decodeFromString(serializer(), json)
        }
    }
}

@Singleton
class GoogleDisk @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageUtils: ImageUtils
) {
    private val uploadEndpoint = "https://schoolkiller-backend.ue.r.appspot.com/upload"
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(300, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private fun getMimeType(uri: Uri): String? {
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.getType(uri)
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            )
        }
    }

    suspend fun upload(imageUri: Uri): Result<UploadResponse> {
        val byteArray = imageUri.let { imageUtils.convertUriToByteArray(it) }
            ?: return Result.failure(NullPointerException("Image URI is null"))

        val mimeType = getMimeType(imageUri)
            ?: return Result.failure(IllegalArgumentException("Cannot determine MIME type for the file"))

        return try {
            val multipartBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)

            val mediaType = mimeType.toMediaTypeOrNull()
            val requestBody = mediaType?.let { byteArray.toRequestBody(it) }
            if (requestBody == null) {
                return Result.failure(IllegalArgumentException("Invalid media type or request body"))
            }

            val fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "unknown"
            val fileName = "file.$fileExtension"
            multipartBodyBuilder.addFormDataPart("file", fileName, requestBody)

            val body = multipartBodyBuilder.build()

            val request = Request.Builder()
                .url(uploadEndpoint)
                .post(body)
                .build()

            val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }

            parseResponse(response)

        } catch (e: Exception) {
            Timber.e(e, "Exception occurred during file upload")
            Result.failure(e)
        }
    }
    private fun parseResponse(response: okhttp3.Response): Result<UploadResponse> {
        return when (response.code) {
            200 -> {
                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    Result.failure(Exception("Empty response body"))
                } else {
                    val uploadResponse = UploadResponse.fromJson(responseBody)
                    Timber.d("File uploaded successfully: ${uploadResponse.fileUrl}")
                    Result.success(uploadResponse)
                }
            }

            400 -> {
                val errorResponse = response.body?.string() ?: "No files uploaded"
                Timber.e("Error 400: $errorResponse")
                Result.failure(Exception(errorResponse))
            }

            500 -> {
                val errorResponse = response.body?.string() ?: "Internal Server Error"
                Timber.e("Error 500: $errorResponse")
                Result.failure(Exception(errorResponse))
            }

            else -> {
                val errorResponse = response.body?.string() ?: "Unexpected error"
                Timber.e("Error ${response.code}: $errorResponse")
                Result.failure(Exception("Unexpected response: $errorResponse"))
            }
        }
    }
}
