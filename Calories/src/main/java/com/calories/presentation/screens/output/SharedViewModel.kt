package com.calories.presentation.screens.output

import androidx.lifecycle.ViewModel
import com.example.shared.domain.usecases.TextUtils
import com.calories.presentation.screens.output.result.AIService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

class SharedViewModel@Inject constructor(): ViewModel() {

    /** Selected solution service */
    private val _selectedOcrService = MutableStateFlow<AIService?>(null)
    val selectedOcrService: StateFlow<AIService?> = _selectedOcrService

    fun updateSelectedOcrService(selectedSolutionService: AIService?) {
        _selectedOcrService.update { selectedSolutionService }
    }

    /** OCR results */
    private val _ocrResults = MutableStateFlow<Map<AIService, String>>(emptyMap())
    val ocrResults: StateFlow<Map<AIService, String>> =
        _ocrResults.asStateFlow()

    fun updateOcrResults(
        aiService: AIService,
        resultText: String
    ) {
        if (resultText.isNotBlank()) {
            val htmlString = TextUtils.markdownToHtml(resultText)
            if (_selectedOcrService.value == null) {
                updateSelectedOcrService(aiService)
            }
            _ocrResults.update { oldMap ->
                oldMap + (aiService to htmlString)
            }
        }
    }

    private fun parseMarkdownTableToHtml(markdownTable: String): String {
        try {
            val rows = markdownTable.trim().split("\n")
            if (rows.size < 2) return "" // Ensure we have at least a header and one row

            val header = rows[0].split("|").map { it.trim() }.filter { it.isNotEmpty() }
            val dataRows = rows.drop(2) // Skip header separator row

            val headerHtml = header.joinToString(separator = "") { "<th>$it</th>" }
            val rowsHtml = dataRows.joinToString(separator = "") { row ->
                val cells = row.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                "<tr>${cells.joinToString(separator = "") { "<td>$it</td>" }}</tr>"
            }

            return """
        <table border="1" style="width:100%; border-collapse: collapse; text-align: left;">
            <tr>$headerHtml</tr>
            $rowsHtml
        </table>
    """.trimIndent()
        } catch (e: Exception) {
            return ""
        }
    }

}