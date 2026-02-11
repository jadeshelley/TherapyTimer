package com.example.therapytimer.util

import android.content.Context
import android.util.Log
import android.os.Handler
import android.os.Looper
import kotlin.text.RegexOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/** State of the Vosk model load. UI can show a loading screen until Ready. */
sealed class VoskModelLoadState {
    object Loading : VoskModelLoadState()
    object CopyingFromAssets : VoskModelLoadState()
    object Ready : VoskModelLoadState()
    data class Failed(val message: String) : VoskModelLoadState()
}

/**
 * Voice recognition using Vosk (on-device, no Google/network).
 * Continuous listening: no session restarts, so no gaps when you say "start".
 * The model is loaded from app assets (bundled with the app); no network use.
 */
class VoiceRecognitionManager(private val context: Context) {
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    /** Current text from speech recognition (partial or final). Empty when idle or when nothing heard. */
    private val _hearingText = MutableStateFlow("")
    val hearingText: StateFlow<String> = _hearingText.asStateFlow()

    private val _modelLoadState = MutableStateFlow<VoskModelLoadState>(VoskModelLoadState.Loading)
    val modelLoadState: StateFlow<VoskModelLoadState> = _modelLoadState.asStateFlow()

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioThread: Thread? = null
    private var audioRecord: android.media.AudioRecord? = null
    private val isActive = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    // Debouncing to prevent duplicate triggers
    private var lastProcessedCommand: String? = null
    private var lastProcessedTime: Long = 0
    private val DEBOUNCE_TIME_MS = 250L

    var onNumberDetected: ((Int) -> Unit)? = null
    var onNextDetected: (() -> Unit)? = null
    var onDoneDetected: (() -> Unit)? = null
    var onRestartDetected: (() -> Unit)? = null
    var onResetDetected: (() -> Unit)? = null
    var onStartDetected: (() -> Unit)? = null
    /** Called whenever recognition produces non-empty text (partial or final). */
    var onRecognizedText: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "VoiceRecognition"
        /** Path inside assets to the Vosk model folder (must be bundled with the app). */
        const val VOSK_MODEL_ASSET_PATH = "model-en-us"
        private const val SAMPLE_RATE = 16000
        /** Minimum buffer size for AudioRecord (bytes). */
        private const val MIN_BUFFER_BYTES = 8192
        /** Read chunk size in samples. Smaller = lower latency, more CPU. 128ms at 16 kHz. */
        private const val READ_CHUNK_SAMPLES = 2048
    }

    init {
        loadModelInBackground()
    }

    private fun loadModelInBackground() {
        Thread {
            try {
                _modelLoadState.value = VoskModelLoadState.Loading
                val assetPath = VOSK_MODEL_ASSET_PATH
                val modelDir = File(context.filesDir, assetPath)

                val hasInAssets = try {
                    context.assets.list(assetPath)?.isNotEmpty() == true
                } catch (_: Exception) {
                    false
                }

                when {
                    hasInAssets -> {
                        if (!modelDir.exists() || modelDir.list()?.isEmpty() == true) {
                            _modelLoadState.value = VoskModelLoadState.CopyingFromAssets
                            Log.d(TAG, "Copying Vosk model from assets to ${modelDir.absolutePath}...")
                            copyAssetFolder(assetPath, modelDir)
                        }
                    }
                    modelDir.exists() && !modelDir.list().isNullOrEmpty() -> {
                        // Already copied from assets in a previous run
                    }
                    else -> {
                        _modelLoadState.value = VoskModelLoadState.Failed("Voice model not found. Reinstall the app.")
                        Log.e(TAG, "Vosk model not in assets. Bundle model-en-us in app assets for voice recognition.")
                        return@Thread
                    }
                }

                if (!modelDir.exists() || modelDir.list()?.isEmpty() == true) {
                    val msg = "Model files missing at ${modelDir.absolutePath}"
                    _modelLoadState.value = VoskModelLoadState.Failed(msg)
                    Log.e(TAG, msg)
                    return@Thread
                }
                if (!hasValidModelStructure(modelDir)) {
                    val listing = modelDir.list()?.joinToString(", ") ?: "empty"
                    val msg = "Model folder missing conf/am structure. Contents: $listing"
                    _modelLoadState.value = VoskModelLoadState.Failed(msg)
                    Log.e(TAG, "Invalid model structure at ${modelDir.absolutePath}: $listing")
                    return@Thread
                }
                _modelLoadState.value = VoskModelLoadState.Loading
                Log.d(TAG, "Loading Vosk model from ${modelDir.absolutePath}...")
                model = Model(modelDir.absolutePath)
                Log.d(TAG, "Vosk model loaded successfully")
                _modelLoadState.value = VoskModelLoadState.Ready
            } catch (e: Throwable) {
                val msg = e.message?.take(200) ?: (e.javaClass.simpleName + ": Unknown error")
                _modelLoadState.value = VoskModelLoadState.Failed(msg)
                Log.e(TAG, "Failed to load Vosk model: ${e.javaClass.simpleName}", e)
            }
        }.start()
    }

    /** Vosk expects conf/ and/or am/ (and graph/) in the model directory. */
    private fun hasValidModelStructure(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val names = dir.list() ?: return false
        return names.any { name -> File(dir, name).isDirectory && name in listOf("conf", "am", "graph") }
    }

    private fun copyAssetFolder(assetPath: String, destDir: File) {
        destDir.mkdirs()
        context.assets.list(assetPath)?.forEach { name ->
            val subAsset = "$assetPath/$name"
            val destFile = File(destDir, name)
            try {
                val list = context.assets.list(subAsset)
                if (list.isNullOrEmpty()) {
                    context.assets.open(subAsset).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    copyAssetFolder(subAsset, destFile)
                }
            } catch (_: Exception) { }
        }
    }

    fun startListening() {
        if (model == null) {
            Log.w(TAG, "Cannot start: Vosk model not loaded yet. Wait and try again.")
            return
        }
        if (isActive.getAndSet(true)) {
            Log.d(TAG, "Already listening")
            return
        }
        Log.d(TAG, "startListening() - starting Vosk recognition thread")
        _isListening.value = true
        _hearingText.value = ""
        startRecognitionThread()
    }

    private fun startRecognitionThread() {
        val m = model ?: return
        recognizer = Recognizer(m, SAMPLE_RATE.toFloat())

        val minBufferBytes = android.media.AudioRecord.getMinBufferSize(SAMPLE_RATE, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
        val recordBufferBytes = (minBufferBytes.coerceAtLeast(MIN_BUFFER_BYTES) / 2) * 2

        try {
            audioRecord = android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                recordBufferBytes
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission required", e)
            isActive.set(false)
            _isListening.value = false
            return
        }

        if (audioRecord?.state != android.media.AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            audioRecord?.release()
            audioRecord = null
            isActive.set(false)
            _isListening.value = false
            return
        }

        // Small read chunks (128 ms) so Vosk gets audio often and can return results with lower latency
        val readSamples = READ_CHUNK_SAMPLES.coerceAtMost(recordBufferBytes / 2)
        val shortBuffer = ShortArray(readSamples)
        val byteBuffer = ByteBuffer.allocate(readSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
        val rec = recognizer!!

        audioThread = Thread {
            audioRecord?.startRecording()
            Log.d(TAG, "Vosk recognition thread running (continuous)")
            while (isActive.get() && audioRecord?.recordingState == android.media.AudioRecord.RECORDSTATE_RECORDING) {
                val n = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                if (n > 0) {
                    byteBuffer.clear()
                    for (i in 0 until n) byteBuffer.putShort(shortBuffer[i])
                    val bytes = byteBuffer.array()
                    val len = n * 2
                    if (rec.acceptWaveForm(bytes, len)) {
                        val result = rec.result
                        parseAndProcessResult(result)
                    } else {
                        val partial = rec.partialResult
                        parseAndProcessResult(partial)
                    }
                }
                if (n < 0) break
            }
            // Final result if we were stopped mid-utterance
            if (isActive.get()) {
                val finalResult = rec.finalResult
                parseAndProcessResult(finalResult)
            }
            runOnMain {
                stopInternal()
            }
        }.apply { start() }
    }

    private fun parseAndProcessResult(jsonStr: String?) {
        if (jsonStr.isNullOrBlank()) return
        try {
            val obj = JSONObject(jsonStr)
            // Final results use "text"; partial (live) results use "partial"
            val text = obj.optString("text", "").trim()
            val partial = obj.optString("partial", "").trim()
            val displayText = text.ifEmpty { partial }
            handler.post {
                _hearingText.value = displayText
                // Only log final results so we don't get 5–9 duplicate entries per phrase (partials + final)
                if (text.isNotEmpty()) {
                    onRecognizedText?.invoke(text)
                    processVoiceCommand(text.lowercase())
                }
            }
        } catch (_: Exception) { }
    }

    private fun runOnMain(block: () -> Unit) {
        handler.post(block)
    }

    private fun stopInternal() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioThread = null
        recognizer = null
        _isListening.value = false
        _hearingText.value = ""
    }

    fun stopListening() {
        isActive.set(false)
        // Thread will exit and call stopInternal(); if we're not in the thread, release here
        handler.post {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        }
        _isListening.value = false
    }

    private fun processVoiceCommand(text: String) {
        if (text.isBlank()) return
        val currentTime = System.currentTimeMillis()
        Log.d(TAG, "Processing voice command: '$text'")
        if (text == lastProcessedCommand && (currentTime - lastProcessedTime) < DEBOUNCE_TIME_MS) {
            Log.d(TAG, "Ignoring duplicate command (debounced)")
            return
        }

        if (text.contains(Regex("\\brestart\\b"))) {
            lastProcessedCommand = text
            lastProcessedTime = currentTime
            Log.d(TAG, "Detected 'restart' command")
            onRestartDetected?.invoke()
            return
        }

        // Start: match as word so "one start", "twenty start", "3 start" etc. all work (number doesn't matter)
        if (text.contains(Regex("\\b(start|starts|star|stat)\\b", RegexOption.IGNORE_CASE))) {
            lastProcessedCommand = text
            lastProcessedTime = currentTime
            Log.d(TAG, "Detected 'start' command")
            onStartDetected?.invoke()
            return
        }

        // Next: match as word so "one next" / "two next" advance rep
        if (text.contains(Regex("\\b(next|nex)\\b", RegexOption.IGNORE_CASE))) {
            lastProcessedCommand = text
            lastProcessedTime = currentTime
            Log.d(TAG, "Detected 'next' command")
            onNextDetected?.invoke()
            return
        }

        if (text.contains(Regex("\\bdone\\b"))) {
            lastProcessedCommand = text
            lastProcessedTime = currentTime
            Log.d(TAG, "Detected 'done' command")
            onDoneDetected?.invoke()
            return
        }

        if (text.contains(Regex("\\breset\\b"))) {
            lastProcessedCommand = text
            lastProcessedTime = currentTime
            Log.d(TAG, "Detected 'reset' command")
            onResetDetected?.invoke()
            return
        }
    }

    fun destroy() {
        isActive.set(false)
        handler.post {
            audioRecord?.release()
            audioRecord = null
            recognizer = null
            model?.close()
            model = null
            _isListening.value = false
        }
    }
}
