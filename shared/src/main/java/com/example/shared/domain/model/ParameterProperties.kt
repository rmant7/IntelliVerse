package com.example.shared.domain.model

import com.example.shared.domain.prompt.options.ExplanationLevelOption
import com.example.shared.domain.prompt.options.GradeOption
import com.example.shared.domain.prompt.options.SolutionLanguageOption
import java.util.Locale

/** Only save: grade, language, explanation and description. */
data class ParameterProperties(
    val grade: GradeOption = GradeOption.CLASS_5,
    val language: SolutionLanguageOption = SolutionLanguageOption.fromLocale(Locale.getDefault())
        ?: SolutionLanguageOption.DEFAULT,
    val explanationLevel: ExplanationLevelOption = ExplanationLevelOption.SHORT_EXPLANATION,
    val description: String = ""
)
