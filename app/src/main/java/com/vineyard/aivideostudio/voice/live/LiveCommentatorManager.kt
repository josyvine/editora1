package com.vineyard.aivideostudio.voice.live

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vineyard.aivideostudio.core.common.DispatcherProvider
import com.vineyard.aivideostudio.core.result.AppError
import com.vineyard.aivideostudio.core.result.AppResult
import com.vineyard.aivideostudio.data.preferences.Preferences
import com.vineyard.aivideostudio.processing.logger.ProcessingLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the headless background WebView runtime that connects to the Gemini Multimodal Live API.
 */
class LiveCommentatorManager(
    private val context: Context,
    private val preferences: Preferences,
    private val dispatcherProvider: DispatcherProvider,
    private val logger: ProcessingLogger
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private val isWebViewReady = AtomicBoolean(false)
    private var pageLoadedDeferred: CompletableDeferred<Boolean>? = null

    private var activeFileOutputStream: FileOutputStream? = null
    private var commentaryDeferred: CompletableDeferred<AppResult<File>>? = null
    private val isSessionActive = AtomicBoolean(false)

    /**
     * Initializes the off-screen Headless WebView instance on the main UI thread.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun initialize(): AppResult<Unit> = withContext(dispatcherProvider.main) {
        if (webView != null && isWebViewReady.get()) {
            return@withContext AppResult.Success(Unit)
        }

        pageLoadedDeferred = CompletableDeferred()

        try {
            val newWebView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = true
                settings.allowContentAccess = true

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            logger.d("LiveCommentatorWebView", "[${it.messageLevel()}] ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                        }
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        logger.i("LiveCommentatorManager", "Headless Live Commentator engine loaded successfully: $url")
                        isWebViewReady.set(true)
                        pageLoadedDeferred?.complete(true)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        val errorDescription = error?.description?.toString() ?: "Unknown WebView Error"
                        logger.e("LiveCommentatorManager", "Failed loading engine: $errorDescription")
                        pageLoadedDeferred?.complete(false)
                    }
                }

                // Bind JavaScript Interface
                addJavascriptInterface(
                    LiveBridgeInterface(
                        apiKeyProvider = { fetchApiKeySync() },
                        modelIdProvider = { fetchModelIdSync() },
                        voiceNameProvider = { fetchVoiceNameSync() },
                        listener = object : LiveCommentaryListener {
                            override fun onConnecting() {
                                logger.i("LiveCommentator", "Establishing Gemini Live Bidi WebSocket connection...")
                            }

                            override fun onConnected() {
                                logger.i("LiveCommentator", "Gemini Live Bidi session authenticated and connected.")
                            }

                            override fun onAudioChunkReceived(base64PcmData: String) {
                                handleIncomingAudioChunk(base64PcmData)
                            }

                            override fun onCommentaryText(text: String) {
                                logger.d("LiveCommentatorTranscript", text)
                            }

                            override fun onCommentaryFinished() {
                                handleCommentaryCompletion()
                            }

                            override fun onError(errorMessage: String) {
                                handleCommentaryError(errorMessage)
                            }

                            override fun onDiagnostic(message: String, category: String) {
                                logger.d("LiveDiagnostic[$category]", message)
                            }
                        }
                    ),
                    "AndroidInterface"
                )
            }

            webView = newWebView
            newWebView.loadUrl("file:///android_asset/live_commentator_engine.html")

            val loaded = withTimeoutOrNull(15_000L) {
                pageLoadedDeferred?.await()
            } ?: false

            if (loaded) {
                AppResult.Success(Unit)
            } else {
                AppResult.Error(AppError.InitializationError("Timed out waiting for Live Commentator WebView initialization."))
            }
        } catch (e: Exception) {
            logger.e("LiveCommentatorManager", "Initialization failed with exception: ${e.message}", e)
            AppResult.Error(AppError.InitializationError(e.message ?: "Failed to initialize Live Commentator WebView"))
        }
    }

    /**
     * Synthesizes expressive voiceover commentary using the connected Live WebSocket model.
     *
     * @param scriptText The commentary script containing emotional cue tags (e.g. [SCREAMING], [LOUD HYPE]).
     * @param personaPrompt The director persona instructions.
     * @param outputPcmFile Target file where raw 24kHz 16-bit mono Little-Endian PCM audio will be written.
     */
    suspend fun generateLiveCommentary(
        scriptText: String,
        personaPrompt: String,
        outputPcmFile: File
    ): AppResult<File> = withContext(dispatcherProvider.io) {
        if (!isWebViewReady.get() || webView == null) {
            val initResult = initialize()
            if (initResult is AppResult.Error) {
                return@withContext AppResult.Error(initResult.error)
            }
        }

        if (isSessionActive.getAndSet(true)) {
            return@withContext AppResult.Error(AppError.PipelineError("A live commentary session is already active."))
        }

        // Prepare target output file
        outputPcmFile.parentFile?.mkdirs()
        if (outputPcmFile.exists()) {
            outputPcmFile.delete()
        }

        try {
            activeFileOutputStream = FileOutputStream(outputPcmFile, false)
        } catch (e: Exception) {
            isSessionActive.set(false)
            return@withContext AppResult.Error(AppError.StorageError("Cannot create output PCM file: ${e.message}"))
        }

        val deferred = CompletableDeferred<AppResult<File>>()
        commentaryDeferred = deferred

        // Dispatch session start inside WebView on Main thread
        withContext(dispatcherProvider.main) {
            val escapedScript = escapeForJavascript(scriptText)
            val escapedPersona = escapeForJavascript(personaPrompt)
            val jsCall = "window.startCommentarySession('$escapedScript', '$escapedPersona');"
            webView?.evaluateJavascript(jsCall, null)
        }

        // Wait for generation with safety timeout of 120 seconds
        val result = withTimeoutOrNull(120_000L) {
            deferred.await()
        } ?: run {
            stopCurrentSessionSync()
            AppResult.Error(AppError.NetworkTimeout("Gemini Live Commentary stream timed out after 120 seconds."))
        }

        isSessionActive.set(false)
        return@withContext result
    }

    /**
     * Appends incoming decoded Base64 PCM audio bytes directly into the open output file.
     */
    private fun handleIncomingAudioChunk(base64PcmData: String) {
        try {
            val rawBytes = Base64.decode(base64PcmData, Base64.DEFAULT)
            synchronized(this) {
                activeFileOutputStream?.write(rawBytes)
            }
        } catch (e: Exception) {
            logger.e("LiveCommentatorManager", "Failed writing PCM audio chunk: ${e.message}", e)
        }
    }

    /**
     * Invoked when the Live model turn is complete and audio streaming is finished.
     */
    private fun handleCommentaryCompletion() {
        synchronized(this) {
            try {
                activeFileOutputStream?.flush()
                activeFileOutputStream?.close()
                activeFileOutputStream = null
            } catch (e: Exception) {
                logger.e("LiveCommentatorManager", "Error closing output stream: ${e.message}")
            }
        }

        commentaryDeferred?.let { def ->
            if (def.isActive) {
                // Signal success with the recorded file
                def.complete(AppResult.Success(File("").apply { /* Target file reference maintained by caller */ }))
            }
        }
    }

    /**
     * Invoked when an error is returned by the Live WebSocket engine.
     */
    private fun handleCommentaryError(errorMessage: String) {
        logger.e("LiveCommentatorManager", "Live Commentary Engine error: $errorMessage")
        synchronized(this) {
            try {
                activeFileOutputStream?.close()
                activeFileOutputStream = null
            } catch (e: Exception) {
                // ignore
            }
        }

        commentaryDeferred?.let { def ->
            if (def.isActive) {
                def.complete(AppResult.Error(AppError.AiGenerationError(errorMessage)))
            }
        }
        isSessionActive.set(false)
    }

    private fun stopCurrentSessionSync() {
        mainHandler.post {
            webView?.evaluateJavascript("window.stopCommentarySession();", null)
        }
        synchronized(this) {
            try {
                activeFileOutputStream?.close()
                activeFileOutputStream = null
            } catch (e: Exception) {
                // ignore
            }
        }
        isSessionActive.set(false)
    }

    /**
     * Disposes the WebView and closes any open streams.
     */
    fun release() {
        stopCurrentSessionSync()
        mainHandler.post {
            webView?.stopLoading()
            webView?.clearHistory()
            webView?.destroy()
            webView = null
            isWebViewReady.set(false)
        }
    }

    private fun escapeForJavascript(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", "\\t")
    }

    private fun fetchApiKeySync(): String {
        return kotlinx.coroutines.runBlocking(dispatcherProvider.io) {
            try {
                preferences.geminiApiKey.first()
            } catch (e: Exception) {
                ""
            }
        }
    }

    private fun fetchModelIdSync(): String {
        return kotlinx.coroutines.runBlocking(dispatcherProvider.io) {
            try {
                val configuredModel = preferences.selectedLiveModel.first()
                if (configuredModel.isNotBlank()) configuredModel else "gemini-2.5-flash-native-audio-dialog"
            } catch (e: Exception) {
                "gemini-2.5-flash-native-audio-dialog"
            }
        }
    }

    private fun fetchVoiceNameSync(): String {
        return kotlinx.coroutines.runBlocking(dispatcherProvider.io) {
            try {
                val voice = preferences.commentaryVoice.first()
                if (voice.isNotBlank()) voice else "Puck"
            } catch (e: Exception) {
                "Puck"
            }
        }
    }
}