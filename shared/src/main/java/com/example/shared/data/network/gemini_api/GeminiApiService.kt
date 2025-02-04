package com.example.shared.data.network.gemini_api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import javax.inject.Inject
import java.util.concurrent.TimeUnit


@Serializable
data class GeminiProcessResponse(
    val extracted_text: String,
    val answers: String
)

class GeminiApiService @Inject constructor() {

    private val processFileEndpoint = "https://schoolkiller-backend.ue.r.appspot.com/process_file"
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(300, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    fun processFile(
        filePath: String,
        description: String,
        explanationLevel: String,
        resultLanguage: String
    ): Result<GeminiProcessResponse> {
        return try {
            val jsonPayload = """
            {
                "file_path": "$filePath",
                "description": "$description",
                "explanation_level": "$explanationLevel",
                "result_language": "$resultLanguage"
            }
            """.trimIndent()

            val requestBody = jsonPayload.toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(processFileEndpoint)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()

            parseResponse(response)

        } catch (e: Exception) {
            Timber.e(e, "Exception occurred during file processing")
            Result.failure(e)
        }
    }

    private fun parseResponse(response: okhttp3.Response): Result<GeminiProcessResponse> {
        return when (response.code) {
            200 -> {
                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    Result.failure(Exception("Empty response body"))
                } else {
                    val processResponse = Json.decodeFromString<GeminiProcessResponse>(responseBody)
                    Timber.d("File processed successfully!")
                    Result.success(processResponse)
                }
            }
            400 -> {
                val errorResponse = response.body?.string() ?: "Bad Request"
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

