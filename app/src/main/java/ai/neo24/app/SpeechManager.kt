package ai.neo24.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class SpeechManager(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onState: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private val appContext = context.applicationContext

    private var speechRecognizer: SpeechRecognizer? = null

    /*
     * Verhindert, dass nach einem bereits erfolgreich
     * übermittelten Ergebnis noch ERROR_NO_MATCH angezeigt wird.
     */
    private var resultDelivered = false

    init {
        createRecognizer()
    }

    private fun createRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError(
                "Speech recognition is not available on this device."
            )
            return
        }

        speechRecognizer?.destroy()

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(appContext).apply {

                setRecognitionListener(
                    object : RecognitionListener {

                        override fun onReadyForSpeech(
                            params: Bundle?
                        ) {
                            onState("listening")
                        }

                        override fun onBeginningOfSpeech() {
                            onState("speaking")
                        }

                        override fun onRmsChanged(
                            rmsdB: Float
                        ) = Unit

                        override fun onBufferReceived(
                            buffer: ByteArray?
                        ) = Unit

                        override fun onEndOfSpeech() {
                            onState("processing")
                        }

                        override fun onError(error: Int) {
                            /*
                             * Einige Erkennungsdienste senden nach
                             * einem gültigen onResults()-Aufruf noch
                             * zusätzlich ERROR_NO_MATCH.
                             *
                             * Dieser Fehler wird ignoriert, wenn
                             * bereits ein Ergebnis übermittelt wurde.
                             */
                            if (
                                resultDelivered &&
                                error == SpeechRecognizer.ERROR_NO_MATCH
                            ) {
                                return
                            }

                            onError(errorMessage(error))
                        }

                        override fun onResults(
                            results: Bundle?
                        ) {
                            val alternatives =
                                results?.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                                )

                            val recognizedText =
                                alternatives
                                    ?.firstOrNull()
                                    ?.trim()
                                    .orEmpty()

                            if (recognizedText.isNotEmpty()) {
                                resultDelivered = true
                                onResult(recognizedText)
                            } else {
                                onError(
                                    "No speech was recognized."
                                )
                            }
                        }

                        override fun onPartialResults(
                            partialResults: Bundle?
                        ) = Unit

                        override fun onEvent(
                            eventType: Int,
                            params: Bundle?
                        ) = Unit
                    }
                )
            }
    }

    fun start(
        languageTag: String =
            Locale.getDefault().toLanguageTag()
    ) {
        if (speechRecognizer == null) {
            createRecognizer()
        }

        val recognizer = speechRecognizer

        if (recognizer == null) {
            onError(
                "Speech recognition could not be initialized."
            )
            return
        }

        /*
         * Für jeden neuen Erkennungsvorgang zurücksetzen.
         */
        resultDelivered = false

        val normalizedLanguageTag =
            languageTag
                .trim()
                .replace("_", "-")
                .ifEmpty {
                    Locale.getDefault().toLanguageTag()
                }

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    normalizedLanguageTag
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    normalizedLanguageTag
                )

                putExtra(
                    RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE,
                    false
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )

                /*
                 * Etwas längere Sprechpausen erlauben.
                 *
                 * Hinweis:
                 * Nicht jeder installierte SpeechRecognizer
                 * berücksichtigt diese Werte vollständig.
                 */
                putExtra(
                    RecognizerIntent
                        .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    3000L
                )

                putExtra(
                    RecognizerIntent
                        .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    2500L
                )

                putExtra(
                    RecognizerIntent
                        .EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                    6000L
                )

                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Sprich jetzt"
                )
            }

        try {
            /*
             * Eventuell noch laufende Erkennung zuerst abbrechen.
             */
            recognizer.cancel()

            recognizer.startListening(intent)

        } catch (exception: Exception) {
            onError(
                exception.message
                    ?: "Speech recognition could not be started."
            )
        }
    }

    fun stop() {
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun errorMessage(error: Int): String {
        return when (error) {

            SpeechRecognizer.ERROR_AUDIO ->
                "Das Mikrofon konnte nicht verwendet werden."

            SpeechRecognizer.ERROR_CLIENT ->
                "Die Spracherkennung wurde unterbrochen."

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Die Mikrofonberechtigung fehlt."

            SpeechRecognizer.ERROR_NETWORK ->
                "Bei der Spracherkennung ist ein Netzwerkfehler aufgetreten."

            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Der Spracherkennungsdienst hat nicht rechtzeitig geantwortet."

            SpeechRecognizer.ERROR_NO_MATCH ->
                "Es wurde keine Sprache erkannt."

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "Die Spracherkennung ist bereits aktiv."

            SpeechRecognizer.ERROR_SERVER ->
                "Der Spracherkennungsdienst hat einen Fehler gemeldet."

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "Es wurde keine Spracheingabe erkannt."

            else ->
                "Die Spracherkennung ist fehlgeschlagen. Fehlercode: $error"
        }
    }
}