package com.calories.presentation.screens.output.result

internal fun addProperties(properties: String): String {
    if (properties.isBlank()) return ""
    return " Additional properties: $properties."
}

/*val ocrPrompt: String
    get() = "Create a table with rows for each type of food in the image, " +
            "and columns for (1) amount of items of that food and (2) average size of that item (small/medium/large). " +
            "If there is no food in the image, show nothing."*/

val ocrPrompt: String
    get() = "List the identified properties (e.g., number of items, weight, size, calorie density) and their actual values that may help:\n" +
            "$basicTasks\n" +
            "Separate food types with two blank lines for better reading. Do not provide explanations or solutions- only list the food types and their relevant properties with values."

val resultOnly: String
    get() = "Do not provide descriptive summaries."

val basicTasks: String
    get() = "1. Estimate the total calories of the food present.\n" +
            "2. Estimate the actual calories that will be digested, accounting for:\n" +
            "   - Digestion efficiency for each food type.\n" +
            "   - The combined effect of the food types on digestion efficiency (e.g., how the combination of foods might impact absorption)."

val resultAtTop: String
    get() = "Present the results clearly and concisely at the top, only then explanations."