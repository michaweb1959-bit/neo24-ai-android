package ai.neo24.app

import android.webkit.JavascriptInterface

class WebBridge(
    private val onStartSpeechInput: () -> Unit,
    private val onStopSpeechInput: () -> Unit,
    private val onSpeakText: (String, String?) -> Unit,
    private val onSetLanguage: (String) -> Unit,
    private val onStopSpeaking: () -> Unit,
    private val onClearConversationInput: () -> Unit
) {

    @JavascriptInterface
    fun startSpeechInput() {
        onStartSpeechInput()
    }

    @JavascriptInterface
    fun stopSpeechInput() {
        onStopSpeechInput()
    }

    @JavascriptInterface
    fun speakText(
        text: String,
        languageTag: String?
    ) {
        onSpeakText(
            text,
            languageTag
        )
    }

    @JavascriptInterface
    fun setLanguage(
        languageTag: String
    ) {
        onSetLanguage(
            languageTag
        )
    }

    @JavascriptInterface
    fun stopSpeaking() {
        onStopSpeaking()
    }

    @JavascriptInterface
    fun clearConversationInput(): String {
        onClearConversationInput()
        return "native-ok"
    }
}