package ai.neo24.app

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsManager(context: Context) {

    private var textToSpeech: TextToSpeech? = null
    private var ready = false

    init {
        textToSpeech =
            TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS

                if (ready) {
                    textToSpeech?.apply {
                        language = Locale.getDefault()
                        setSpeechRate(1.0f)
                        setPitch(1.0f)
                    }
                }
            }
    }

    fun speak(
        text: String,
        languageTag: String?
    ) {
        val engine = textToSpeech

        if (!ready || engine == null) {
            return
        }

        val cleanText = text.trim()

        if (cleanText.isEmpty()) {
            return
        }

        val requestedLocale =
            if (languageTag.isNullOrBlank()) {
                Locale.getDefault()
            } else {
                Locale.forLanguageTag(languageTag)
            }

        val languageResult =
            engine.setLanguage(requestedLocale)

        if (
            languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            engine.language = Locale.getDefault()
        }

        engine.stop()

        splitIntoChunks(cleanText).forEachIndexed { index, chunk ->
            engine.speak(
                chunk,
                if (index == 0) {
                    TextToSpeech.QUEUE_FLUSH
                } else {
                    TextToSpeech.QUEUE_ADD
                },
                Bundle(),
                "neo24-tts-$index"
            )
        }
    }

    fun stop() {
        textToSpeech?.stop()
    }

    fun destroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        ready = false
    }

    private fun splitIntoChunks(text: String): List<String> {
        val maximumLength =
            TextToSpeech
                .getMaxSpeechInputLength()
                .coerceAtMost(3500)

        if (text.length <= maximumLength) {
            return listOf(text)
        }

        val chunks = mutableListOf<String>()
        var remaining = text.trim()

        while (remaining.isNotEmpty()) {
            if (remaining.length <= maximumLength) {
                chunks.add(remaining)
                break
            }

            val candidate =
                remaining.substring(0, maximumLength)

            val possiblePositions =
                listOf(
                    candidate.lastIndexOf(". "),
                    candidate.lastIndexOf("! "),
                    candidate.lastIndexOf("? "),
                    candidate.lastIndexOf("\n"),
                    candidate.lastIndexOf(" ")
                )

            val splitPosition =
                possiblePositions
                    .maxOrNull()
                    ?.takeIf { it > maximumLength / 2 }
                    ?: maximumLength - 1

            chunks.add(
                remaining
                    .substring(0, splitPosition + 1)
                    .trim()
            )

            remaining =
                remaining
                    .substring(splitPosition + 1)
                    .trim()
        }

        return chunks
    }
}