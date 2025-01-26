package com.example.shared.domain.usecases

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SpeechConverter @Inject constructor(
    @ApplicationContext val context: Context
) : TextToSpeech.OnInitListener {

    // Preferred engine
    private object Engine {
        const val PICO = "com.svox.pico"
        const val GOOGLE = "com.google.android.tts"
    }

    private var textToSpeech: TextToSpeech? = null
    private var locale: Locale? = null
    private var onUtteranceFinished: (String?) -> Unit = {}

    fun initialize() {
        textToSpeech = TextToSpeech(context, this, Engine.GOOGLE)
    }

    fun setOnUtteranceFinished(onUtteranceFinished: (String?) -> Unit){
        this.onUtteranceFinished = onUtteranceFinished
    }

    fun isLanguageSupported(locale: Locale): Boolean {
        val result = textToSpeech?.isLanguageAvailable(locale)
        val langAvailableResults = arrayOf(
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
        )
        return langAvailableResults.contains(result)
    }

    fun setLanguage(locale: Locale): Boolean {

        val isLanguageSupported = isLanguageSupported(locale)
        if (isLanguageSupported) {
            if (textToSpeech != null) textToSpeech!!.setLanguage(locale)
            else this.locale = locale // Set language inside onInit()
        }

        return isLanguageSupported
    }


    fun synthesizeToFile(text: String, fileName: String) {
        val dir = context.getExternalFilesDir(null)
        val path = "${dir}/${fileName}"
        textToSpeech?.synthesizeToFile(
            preprocessText(text), null, File(path), fileName
        ) // Async method
        Timber.d("File with path $path will be created.")
    }



    // Stop generating any utterance
    fun stopUtterance() {
        textToSpeech?.stop()
    }


    fun shutdown() {
        onUtteranceFinished = {}
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Timber.d("Successfully initialized SpeechConverter")
            textToSpeech!!.setOnUtteranceProgressListener(
                createUtteranceProgressListener()
            )
            if (locale != null) textToSpeech!!.setLanguage(locale)
        } else {
            Timber.d("SpeechConverter wasn't initialized")
        }
    }

    // Track speaking or synthesizing to file processes.
    private fun createUtteranceProgressListener(): UtteranceProgressListener {
        return object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Timber.d("Started utterance.")
            }

            override fun onDone(utteranceId: String?) {
                Timber.d("Finished utterance.")
                onUtteranceFinished(utteranceId)
            }

            @Deprecated(
                "Deprecated in Java", ReplaceWith(
                    "Timber.d(\"Error while producing utterance.\")",
                    "timber.log.Timber"
                )
            )
            override fun onError(utteranceId: String?) {
            }
        }
    }

    private fun preprocessText(text: String): String {
        // Remove Markdown and LaTeX symbols using regular expressions
        val cleanedText = text
            // Remove LaTeX delimiters for inline and block formulas
            .replace("""\$.*?\$""".toRegex(), "") // Inline math: $...$
            .replace("""\$\$.*?\$\$""".toRegex(), "") // Block math: $$...$$
            // Remove Markdown formatting symbols
            .replace("""[*_~`>|\\]""".toRegex(), "") // *, _, ~, `, >, |, \, [, ]
            .replace("""^#{1,6} """.toRegex(RegexOption.MULTILINE), "") // Headers: #, ##, ###
            .replace("""\[(.*?)]\((.*?)\)""".toRegex(), "$1") // Links: [text](url)
            .replace("""!\[(.*?)]\((.*?)\)""".toRegex(), "$1") // Images: ![alt](url)
            .replace(
                """`{3}.*?`{3}""".toRegex(RegexOption.DOT_MATCHES_ALL),
                ""
            ) // Code blocks: ```code```
            .replace("""`.*?`""".toRegex(), "") // Inline code: `code`
            .replace("""-{3,}""".toRegex(), "") // Horizontal rules: ---
        return cleanedText.trim()
    }

}