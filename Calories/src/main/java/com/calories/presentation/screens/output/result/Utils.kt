package com.calories.presentation.screens.output.result

import com.example.shared.domain.usecases.TextUtils

internal fun addProperties(tasks: String, additional: Boolean): String {
    if (tasks.isBlank()) return ""
    val cleanedJsonStr = TextUtils.htmlToJsonString(tasks)
    return if (additional) {
        "Additional properties are: $cleanedJsonStr."
    } else {
        "Properties are: $cleanedJsonStr."
    }
}

val ocrPrompt: String
    get() = "List the identified properties of the food detected in the image (e.g., number of items, weight, size, calorie density) and their actual values which may help solve the following tasks:\n" +
            "$basicTasks\n" +
            "Do not provide explanations or solutions- only list the food types and their properties and corresponding values, which may help you solve these tasks. $clearFormatting"

val noSummaries: String
    get() = "Do not provide descriptive summaries."

val basicTasks: String
    get() = "- Estimate the total calories of the food present.\n" +
            "- Estimate the actual calories that will be digested, accounting for:\n" +
            "   - Digestion efficiency for each food type.\n" +
            "   - The combined effect of the food types on digestion efficiency (e.g., how the combination of foods might impact absorption)."

val resultAtTop: String
    get() = "Present the results themselves clearly and concisely at the top."

val clearFormatting: String
    get() = "Use proper formatting to ensure readability."