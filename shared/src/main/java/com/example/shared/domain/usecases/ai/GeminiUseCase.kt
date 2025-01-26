package com.example.shared.domain.usecases.ai

import com.example.shared.data.network.gemini_api.GeminiApiService
import javax.inject.Inject

class GeminiUseCase @Inject constructor(
    private val geminiApiService: GeminiApiService
) {

    suspend fun processGeminiFile(
        filePath: String,
        description: String,
        explanationLevel: String,
        resultLanguage: String
    ): Result<GeminiResult> {
        return geminiApiService.processFile(filePath, description, explanationLevel, resultLanguage)
            .mapCatching { response ->
                GeminiResult(
                    extractedText = response.extracted_text,
                    answers = response.answers
                )
            }
    }

    data class GeminiResult(
        val extractedText: String,
        val answers: String
    )
}