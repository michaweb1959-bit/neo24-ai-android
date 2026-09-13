package ai.neo24.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var speechManager: SpeechManager
    private lateinit var ttsManager: TtsManager

    private lateinit var loginDialogManager: LoginDialogManager
    private lateinit var webBridge: WebBridge

    private var speechRecognitionPending = false
    private var passwordLoginSelected = false

    private var filePathCallback:
            android.webkit.ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null

    private val fileChooserLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            val callback = filePathCallback

            if (callback == null) {
                return@registerForActivityResult
            }

            val resultUris: Array<Uri>? =
                if (result.resultCode == RESULT_OK) {
                    val data = result.data

                    when {
                        data?.clipData != null -> {
                            val clipData = data.clipData!!

                            val localUris =
                                mutableListOf<Uri>()

                            for (index in 0 until clipData.itemCount) {
                                val sourceUri =
                                    clipData.getItemAt(index).uri

                                val localUri =
                                    copySelectedFileToCache(
                                        sourceUri
                                    )

                                if (localUri != null) {
                                    localUris.add(localUri)
                                }
                            }

                            if (localUris.isNotEmpty()) {
                                localUris.toTypedArray()
                            } else {
                                null
                            }
                        }

                        data?.data != null -> {
                            val localUri =
                                copySelectedFileToCache(
                                    data.data!!
                                )

                            if (localUri != null) {
                                arrayOf(localUri)
                            } else {
                                null
                            }
                        }

                        cameraPhotoUri != null -> {
                            arrayOf(cameraPhotoUri!!)
                        }

                        else -> null
                    }
                } else {
                    null
                }

            callback.onReceiveValue(resultUris)
            filePathCallback = null
            cameraPhotoUri = null

            if (::webView.isInitialized) {
                webView.postDelayed(
                    {
                        cleanupUploadCache()
                    },
                    5 * 60 * 1000L
                )
            }
        }

    private var currentLanguageTag =
        Locale.getDefault().toLanguageTag()

    private val microphonePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted && speechRecognitionPending) {
                speechRecognitionPending = false

                speechManager.start(
                    currentLanguageTag
                )
            } else {
                speechRecognitionPending = false

                sendSpeechErrorToWeb(
                    getString(
                        R.string.microphone_permission_denied
                    )
                )
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        cleanupUploadCache()

        enableEdgeToEdge()

        webView = WebView(this)
        setContentView(webView)

        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->

            val systemBars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                            WindowInsetsCompat.Type.displayCutout()
                )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        createNativeManagers()
        createLoginDialogManager()
        configureWebView()
        createWebBridge()
        configureBackButton()

        val restored =
            savedInstanceState != null &&
                    webView.restoreState(savedInstanceState) != null

        val googleLoginHandled =
            handleGoogleLoginDeepLink(
                intent
            )

        if (
            !restored &&
            !googleLoginHandled
        ) {
            webView.loadUrl(
                getString(
                    R.string.account_url
                )
            )
        }
    }


    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(intent)

        setIntent(intent)

        if (::webView.isInitialized) {
            handleGoogleLoginDeepLink(
                intent
            )
        }
    }


    private fun handleGoogleLoginDeepLink(
        intent: Intent?
    ): Boolean {

        val uri =
            intent?.data
                ?: return false

        if (
            uri.scheme != "neo24" ||
            uri.host != "google-login-complete"
        ) {
            return false
        }

        val completionUrl =
            uri.getQueryParameter(
                "completion_url"
            )
                ?.trim()
                .orEmpty()

        if (completionUrl.isEmpty()) {

            Toast.makeText(
                this,
                "Die Google-Anmeldung konnte nicht abgeschlossen werden.",
                Toast.LENGTH_LONG
            ).show()

            return true
        }

        val completionUri =
            try {
                Uri.parse(
                    completionUrl
                )
            } catch (_: Exception) {

                Toast.makeText(
                    this,
                    "Ungültige Neo24-Anmeldeadresse.",
                    Toast.LENGTH_LONG
                ).show()

                return true
            }

        if (
            !isAllowedNeo24Url(
                completionUri
            )
        ) {

            Toast.makeText(
                this,
                "Die Google-Anmeldung hat eine ungültige Adresse geliefert.",
                Toast.LENGTH_LONG
            ).show()

            return true
        }

        passwordLoginSelected = false

        if (::loginDialogManager.isInitialized) {
            loginDialogManager.dismiss()
        }

        webView.loadUrl(
            completionUrl
        )

        return true
    }


    private fun createNativeManagers() {
        speechManager =
            SpeechManager(
                context = this,

                onResult = { text ->
                    sendSpeechResultToWeb(text)
                },

                onState = { state ->
                    sendSpeechStateToWeb(state)
                },

                onError = { message ->
                    sendSpeechErrorToWeb(message)
                }
            )

        ttsManager =
            TtsManager(this)
    }

      private fun createLoginDialogManager() {
        loginDialogManager =
            LoginDialogManager(
                activity = this,

                onGoogleLoginRequested = {
                    startGoogleSignIn()
                },

                onPasswordLoginRequested = {
                    passwordLoginSelected = true
                }
            )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false

            mixedContentMode =
                WebSettings.MIXED_CONTENT_NEVER_ALLOW

            allowFileAccess = false
            allowContentAccess = true

            cacheMode =
                WebSettings.LOAD_NO_CACHE
        }

        webView.clearCache(true)
        webView.clearHistory()

        webView.webChromeClient =
            object : WebChromeClient() {

                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback:
                    android.webkit.ValueCallback<Array<Uri>>,
                    fileChooserParams:
                    FileChooserParams
                ): Boolean {

                    this@MainActivity.filePathCallback
                        ?.onReceiveValue(null)

                    this@MainActivity.filePathCallback =
                        filePathCallback

                    val contentIntent =
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(
                                Intent.CATEGORY_OPENABLE
                            )

                            type = "*/*"

                            putExtra(
                                Intent.EXTRA_MIME_TYPES,
                                arrayOf(
                                    "image/jpeg",
                                    "image/png",
                                    "image/webp",
                                    "application/pdf"
                                )
                            )
                        }

                    val cameraFile =
                        java.io.File.createTempFile(
                            "neo24_camera_",
                            ".jpg",
                            cacheDir
                        )

                    cameraPhotoUri =
                        FileProvider.getUriForFile(
                            this@MainActivity,
                            "${packageName}.fileprovider",
                            cameraFile
                        )

                    val cameraIntent =
                        Intent(
                            MediaStore.ACTION_IMAGE_CAPTURE
                        ).apply {
                            putExtra(
                                MediaStore.EXTRA_OUTPUT,
                                cameraPhotoUri
                            )

                            addFlags(
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }

                    val chooserIntent =
                        Intent.createChooser(
                            contentIntent,
                            "Foto oder Dokument auswählen"
                        ).apply {
                            putExtra(
                                Intent.EXTRA_INITIAL_INTENTS,
                                arrayOf(cameraIntent)
                            )
                        }

                    fileChooserLauncher.launch(
                        chooserIntent
                    )

                    return true
                }
            }

        webView.webViewClient =
            object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    return handleRequestedUrl(
                        request.url
                    )
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    url: String
                ): Boolean {
                    return handleRequestedUrl(
                        Uri.parse(url)
                    )
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {
                    super.onPageFinished(view, url)

                    inspectLoginState(
                        view = view,
                        url = url
                    )

                    installNewConversationHandler(view)
                }
            }
    }

    private fun copySelectedFileToCache(
        sourceUri: Uri
    ): Uri? {
        return try {
            val resolver = contentResolver

            var fileName = "neo24_upload"

            resolver.query(
                sourceUri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME
                    )

                    if (nameColumn >= 0) {
                        val name = cursor.getString(nameColumn)

                        if (!name.isNullOrBlank()) {
                            fileName = name
                        }
                    }
                }
            }

            val safeName = fileName.replace(
                Regex("[^A-Za-z0-9._-]"),
                "_"
            )

            val uploadDirectory =
                java.io.File(
                    cacheDir,
                    "neo24_upload_${System.currentTimeMillis()}"
                ).apply {
                    mkdirs()
                }

            val targetFile =
                java.io.File(
                    uploadDirectory,
                    safeName
                )

            resolver.openInputStream(sourceUri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            FileProvider.getUriForFile(
                this@MainActivity,
                "${packageName}.fileprovider",
                targetFile
            )
        } catch (exception: Exception) {
            android.util.Log.e(
                "Neo24FileChooser",
                "Failed to copy selected file",
                exception
            )

            null
        }
    }

    private fun cleanupUploadCache() {
        try {
            cacheDir.listFiles()
                ?.filter { file ->
                    file.name.startsWith("neo24_upload_") ||
                            file.name.startsWith("neo24_camera_") ||
                            file.name.startsWith("neo24_")
                }
                ?.forEach { file ->
                    if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                }
        } catch (exception: Exception) {
            android.util.Log.w(
                "Neo24Cache",
                "Could not clean upload cache",
                exception
            )
        }
    }

    private fun createWebBridge() {
        webBridge =
            WebBridge(
                onStartSpeechInput = {
                    requestSpeechInput()
                },

                onStopSpeechInput = {
                    runOnUiThread {
                        speechManager.stop()
                    }
                },

                onSpeakText = { text, languageTag ->
                    runOnUiThread {
                        ttsManager.speak(
                            text,
                            languageTag
                        )
                    }
                },

                onSetLanguage = { languageTag ->
                    runOnUiThread {
                        currentLanguageTag =
                            normalizeLanguageTag(
                                languageTag
                            )
                    }
                },

                onStopSpeaking = {
                    runOnUiThread {
                        ttsManager.stop()
                    }
                },

                onClearConversationInput = {
                    runOnUiThread {
                        clearConversationInputInWebView()
                    }
                }
            )

        webView.addJavascriptInterface(
            webBridge,
            "Neo24Android"
        )
    }
     private fun inspectLoginState(
        view: WebView,
        url: String
    ) {
        if (!isLoginArea(url)) {
            passwordLoginSelected = false
            loginDialogManager.dismiss()
            return
        }

        view.evaluateJavascript(
            """
            (() => {
                const body = document.body;

                return Boolean(
                    body &&
                    body.classList.contains("logged-in")
                );
            })();
            """.trimIndent()
        ) { result ->

            val isLoggedIn =
                result
                    ?.trim()
                    ?.equals(
                        "true",
                        ignoreCase = true
                    ) == true

            runOnUiThread {
                when {
                    isLoggedIn -> {
                        passwordLoginSelected = false
                        loginDialogManager.dismiss()

                    }

                    passwordLoginSelected -> {
                        loginDialogManager.dismiss()
                    }

                    else -> {
                        loginDialogManager.show()
                    }
                }
            }
        }
    }

    private fun isLoginArea(
        url: String?
    ): Boolean {
        val uri =
            try {
                Uri.parse(
                    url.orEmpty()
                )
            } catch (_: Exception) {
                return false
            }

        val scheme =
            uri.scheme
                ?.lowercase(Locale.ROOT)

        val host =
            uri.host
                ?.lowercase(Locale.ROOT)

        val path =
            uri.path
                ?.lowercase(Locale.ROOT)
                .orEmpty()

        val isNeo24 =
            scheme == "https" &&
                    (
                            host == "neo24.ai" ||
                                    host?.endsWith(
                                        ".neo24.ai"
                                    ) == true
                            )

        val isAccountOrLoginPage =
            path == "/account" ||
                    path == "/account/" ||
                    path.contains(
                        "/membership-account"
                    ) ||
                    path.contains("/login")

        return isNeo24 &&
                isAccountOrLoginPage
    }

    private fun startGoogleSignIn() {
        val uri =
            Uri.parse(
                "https://neo24.ai/account/wp-admin/admin-post.php?action=neo24_fdroid_google_start"
            )

        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                )
            )
        } catch (_: Exception) {
            loginDialogManager.showGoogleError()

            Toast.makeText(
                this,
                "Der Browser für die Google-Anmeldung konnte nicht geöffnet werden.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestSpeechInput() {
        runOnUiThread {
            val permissionGranted =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

            if (permissionGranted) {
                speechManager.start(
                    currentLanguageTag
                )
            } else {
                speechRecognitionPending = true

                microphonePermissionLauncher.launch(
                    Manifest.permission.RECORD_AUDIO
                )
            }
        }
    }

    private fun normalizeLanguageTag(
        languageTag: String
    ): String {
        val normalized =
            languageTag
                .trim()
                .replace("_", "-")

        if (normalized.isEmpty()) {
            return Locale
                .getDefault()
                .toLanguageTag()
        }

        return when (
            normalized
                .lowercase(Locale.ROOT)
                .substringBefore("-")
        ) {
            "de" -> "de-DE"
            "en" -> "en-US"
            "fr" -> "fr-FR"
            "es" -> "es-ES"
            "it" -> "it-IT"
            "pt" -> "pt-PT"
            "nl" -> "nl-NL"
            "pl" -> "pl-PL"
            "cs" -> "cs-CZ"
            "sv" -> "sv-SE"
            "da" -> "da-DK"
            "no", "nb" -> "nb-NO"
            "fi" -> "fi-FI"
            "tr" -> "tr-TR"
            "ro" -> "ro-RO"
            "hu" -> "hu-HU"
            "id" -> "id-ID"
            "ru" -> "ru-RU"
            "uk" -> "uk-UA"
            "el" -> "el-GR"
            "ar" -> "ar-SA"
            "he", "iw" -> "he-IL"
            "zh" -> "zh-CN"
            "ja" -> "ja-JP"
            "ko" -> "ko-KR"
            else -> normalized
        }
    }

    private fun sendSpeechResultToWeb(
        text: String
    ) {
        val quotedText =
            JSONObject.quote(text)

        runOnUiThread {
            if (!::webView.isInitialized) {
                return@runOnUiThread
            }

            webView.evaluateJavascript(
                """
                (() => {
                    if (
                        typeof window.neo24AndroidSpeechResult ===
                        "function"
                    ) {
                        window.neo24AndroidSpeechResult(
                            $quotedText
                        );
                    }
                })();
                """.trimIndent(),
                null
            )
        }
    }

    private fun sendSpeechStateToWeb(
        state: String
    ) {
        val quotedState =
            JSONObject.quote(state)

        runOnUiThread {
            if (!::webView.isInitialized) {
                return@runOnUiThread
            }

            webView.evaluateJavascript(
                """
                (() => {
                    if (
                        typeof window.neo24AndroidSpeechState ===
                        "function"
                    ) {
                        window.neo24AndroidSpeechState(
                            $quotedState
                        );
                    }
                })();
                """.trimIndent(),
                null
            )
        }
    }

    private fun sendSpeechErrorToWeb(
        message: String
    ) {
        val quotedMessage =
            JSONObject.quote(message)

        runOnUiThread {
            if (!::webView.isInitialized) {
                return@runOnUiThread
            }

            webView.evaluateJavascript(
                """
                (() => {
                    if (
                        typeof window.neo24AndroidSpeechError ===
                        "function"
                    ) {
                        window.neo24AndroidSpeechError(
                            $quotedMessage
                        );
                    } else {
                        alert($quotedMessage);
                    }
                })();
                """.trimIndent(),
                null
            )
        }
    }
    private fun clearConversationInputInWebView() {
        if (!::webView.isInitialized) {
            return
        }

        speechManager.stop()
        ttsManager.stop()

        webView.clearFocus()
        webView.clearFormData()

        webView.evaluateJavascript(
            """
        (() => {
            sessionStorage.removeItem(
                "neo24_account_previous_response_id"
            );

            window.neo24PreviousResponseId = null;

            const input =
                document.getElementById("prompt");

            if (input) {
                input.blur();
                input.value = "";
                input.defaultValue = "";
                input.textContent = "";
            }

            const conversation =
            document.getElementById("conversation");

            if (conversation) {
            conversation.innerHTML = "";
            }

            const copyButton =
                document.getElementById("copyButton");

            if (copyButton) {
                copyButton.style.display = "none";
            }

            const feedbackActions =
                document.getElementById("feedbackActions");

            if (feedbackActions) {
                feedbackActions.style.display = "none";
            }

            const feedbackStatus =
                document.getElementById("feedbackStatus");

            if (feedbackStatus) {
                feedbackStatus.style.display = "none";
            }
        })();
        """.trimIndent(),
            null
        )

        webView.postDelayed(
            {
                webView.evaluateJavascript(
                    """
                (() => {
                    const input =
                        document.getElementById("prompt");

                    return input
                        ? JSON.stringify(input.value)
                        : "Eingabefeld nicht gefunden";
                })();
                """.trimIndent()
                ) { result ->

                    Toast.makeText(
                        this,
                        "Wert nach 1 Sekunde: $result",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            1000L
        )
    }
    private fun installNewConversationHandler(
        view: WebView
    ) {
        view.evaluateJavascript(
            """
        (() => {
            const button =
                document.getElementById("newChatButton");

            if (!button) {
                return false;
            }

            /*
             * Verhindert, dass der Handler bei jedem Laden
             * mehrfach installiert wird.
             */
            if (button.dataset.androidResetInstalled === "true") {
                return true;
            }

            button.dataset.androidResetInstalled = "true";

            button.addEventListener(
                "click",
                event => {
                    event.preventDefault();
                    event.stopImmediatePropagation();

                    sessionStorage.removeItem(
                        "neo24_account_previous_response_id"
                    );

                    window.neo24PreviousResponseId = null;

                    const prompt =
                        document.getElementById("prompt");

                    if (prompt) {
                        prompt.blur();
                        prompt.value = "";
                        prompt.defaultValue = "";
                        prompt.textContent = "";

                        prompt.dispatchEvent(
                            new Event(
                                "input",
                                { bubbles: true }
                            )
                        );

                        prompt.dispatchEvent(
                            new Event(
                                "change",
                                { bubbles: true }
                            )
                        );
                    }

                    const conversation =
                        document.getElementById("conversation");

                    if (conversation) {
                        conversation.innerHTML = "";
                    }

                    const copyButton =
                        document.getElementById("copyButton");

                    if (copyButton) {
                        copyButton.style.display = "none";
                    }

                    const feedbackActions =
                        document.getElementById("feedbackActions");

                    if (feedbackActions) {
                        feedbackActions.style.display = "none";
                    }

                    const feedbackStatus =
                        document.getElementById("feedbackStatus");

                    if (feedbackStatus) {
                        feedbackStatus.style.display = "none";
                    }

                    /*
                     * Android-WebView zur Neuzeichnung zwingen.
                     */
                    document.body.offsetHeight;
                },
                true
            );

            return true;
        })();
        """.trimIndent(),
            null
        )
    }
    private fun handleRequestedUrl(
        uri: Uri
    ): Boolean {
        if (
            uri.scheme == "neo24" &&
            uri.host == "new-conversation"
        ) {
            runOnUiThread {
                clearConversationInputInWebView()
            }

            return true
        }

        return if (isAllowedNeo24Url(uri)) {
            false
        } else {
            openExternalUrl(uri)
            true
        }
    }

    private fun isAllowedNeo24Url(
        uri: Uri
    ): Boolean {
        val scheme =
            uri.scheme
                ?.lowercase(Locale.ROOT)

        val host =
            uri.host
                ?.lowercase(Locale.ROOT)

        return scheme == "https" &&
                (
                        host == "neo24.ai" ||
                                host?.endsWith(
                                    ".neo24.ai"
                                ) == true
                        )
    }

    private fun openExternalUrl(
        uri: Uri
    ) {
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                )
            )
        } catch (_: Exception) {
            Toast.makeText(
                this,
                getString(
                    R.string.external_app_not_found
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun configureBackButton() {
        onBackPressedDispatcher.addCallback(
            this,
            object :
                OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {
                    when {
                        loginDialogManager.isShowing -> {
                            passwordLoginSelected = true
                            loginDialogManager.dismiss()
                        }

                        webView.canGoBack() -> {
                            webView.goBack()
                        }

                        else -> {
                            finish()
                        }
                    }
                }
            }
        )
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        webView.saveState(outState)

        super.onSaveInstanceState(
            outState
        )
    }

    override fun onDestroy() {
        if (::loginDialogManager.isInitialized) {
            loginDialogManager.destroy()
        }

        if (::speechManager.isInitialized) {
            speechManager.destroy()
        }

        if (::ttsManager.isInitialized) {
            ttsManager.destroy()
        }

        if (::webView.isInitialized) {
            webView.apply {
                stopLoading()

                removeJavascriptInterface(
                    "Neo24Android"
                )

                removeAllViews()
                destroy()
            }
        }
        super.onDestroy()
    }
}
