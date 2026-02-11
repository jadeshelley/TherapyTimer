package com.example.therapytimer.util

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class TextToSpeechManager(private val context: Context, private val preferencesManager: PreferencesManager? = null) {
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var selectedVoice: Voice? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        initializeTTS()
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Get all available TTS engines installed on the device
     */
    fun getInstalledEngines(): List<TtsEngineInfo> {
        val engines = mutableListOf<TtsEngineInfo>()
        
        val samsungEngine = TtsEngineInfo(
            "com.samsung.SMT",
            "Samsung TTS",
            "Samsung's text-to-speech engine"
        )
        val googleEngine = TtsEngineInfo(
            "com.google.android.tts",
            "Google TTS",
            "Google's text-to-speech engine (recommended)"
        )
        
        // Auto/default option
        engines.add(TtsEngineInfo(
            packageName = "auto",
            name = "Auto (Best Available)",
            description = "Automatically select the best available engine"
        ))
        
        // Add engines that are installed
        if (isPackageInstalled("com.samsung.SMT")) {
            engines.add(samsungEngine)
        }
        if (isPackageInstalled("com.google.android.tts")) {
            engines.add(googleEngine)
        }
        
        return engines
    }
    
    /**
     * Helper class to store TTS engine information
     */
    data class TtsEngineInfo(
        val packageName: String,
        val name: String,
        val description: String
    )
    
    /** Log tag for TTS debugging - filter Logcat by "TTS" to see these logs */
    companion object {
        private const val DEBUG_TAG = "TTS"
    }

    /**
     * Get the engine package to use, respecting user's saved preference
     */
    private fun resolveEnginePackage(): String? {
        val savedEngine = preferencesManager?.getTtsEnginePackage()
        Log.d(DEBUG_TAG, "[DEBUG] resolveEnginePackage: savedPref='$savedEngine'")
        
        // If user has explicitly selected an engine, use it
        if (savedEngine != null && savedEngine != "auto") {
            Log.d(DEBUG_TAG, "Using user-selected engine: $savedEngine")
            if (isPackageInstalled(savedEngine)) {
                return savedEngine
            } else {
                Log.w("TTS", "User-selected engine $savedEngine not installed, falling back to auto")
            }
        }
        
        // Auto mode: Try to determine best engine
        val samsungEngine = "com.samsung.SMT"
        val googleEngine = "com.google.android.tts"

        val result = when {
            Build.MANUFACTURER.equals("samsung", ignoreCase = true) && isPackageInstalled(samsungEngine) -> {
                Log.d(DEBUG_TAG, "Auto-selected Samsung TTS on Samsung device")
                samsungEngine
            }
            isPackageInstalled(googleEngine) -> {
                Log.d(DEBUG_TAG, "Auto-selected Google TTS")
                googleEngine
            }
            else -> {
                Log.d(DEBUG_TAG, "No specific engine found, using system default")
                null
            }
        }
        Log.d(DEBUG_TAG, "[DEBUG] resolveEnginePackage: result='$result' (null=system default)")
        return result
    }

    private fun initializeTTS(onReady: (() -> Unit)? = null) {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false

        // Use a consistent engine so installed voices appear in our list.
        val enginePackage = resolveEnginePackage()
        Log.d(DEBUG_TAG, "[DEBUG] initializeTTS: requesting engine='${enginePackage ?: "(system default)"}'")

        textToSpeech = if (enginePackage != null) {
            TextToSpeech(context, { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech?.setLanguage(Locale.getDefault())
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(DEBUG_TAG, "[DEBUG] initializeTTS: FAILED - language not supported")
                    } else {
                        selectBestVoice()
                        isInitialized = true
                        val voiceCount = textToSpeech?.voices?.size ?: 0
                        Log.d(DEBUG_TAG, "[DEBUG] initializeTTS: SUCCESS engine='$enginePackage' voicesReported=$voiceCount currentVoice=${selectedVoice?.name}")
                    }
                    mainHandler.post { onReady?.invoke() }
                } else {
                    Log.e(DEBUG_TAG, "[DEBUG] initializeTTS: FAILED engine='$enginePackage' status=$status")
                    mainHandler.post { onReady?.invoke() }
                }
            }, enginePackage)
        } else {
            TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech?.setLanguage(Locale.getDefault())
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(DEBUG_TAG, "[DEBUG] initializeTTS: FAILED - language not supported (system default)")
                    } else {
                        selectBestVoice()
                        isInitialized = true
                        val voiceCount = textToSpeech?.voices?.size ?: 0
                        Log.d(DEBUG_TAG, "[DEBUG] initializeTTS: SUCCESS engine=(system default) voicesReported=$voiceCount currentVoice=${selectedVoice?.name}")
                    }
                    mainHandler.post { onReady?.invoke() }
                } else {
                    Log.e(DEBUG_TAG, "[DEBUG] initializeTTS: FAILED engine=(system default) status=$status")
                    mainHandler.post { onReady?.invoke() }
                }
            }
        }
    }

    /**
     * Recreates the TTS engine so it picks up newly installed voices (e.g. neural voices
     * downloaded via system TTS settings). Call this before opening the voice picker so
     * getAvailableVoices() returns the current system list.
     */
    fun refreshVoicesForPicker(onReady: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            mainHandler.post(onReady)
            return
        }
        initializeTTS(onReady)
    }

    private fun selectBestVoice() {
        if (textToSpeech == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return

        try {
            // Check if user has selected a voice in preferences
            val savedVoiceName = preferencesManager?.getTtsVoiceName()
            if (savedVoiceName != null && savedVoiceName != "Default") {
                val voices = textToSpeech?.voices
                voices?.find { it.name == savedVoiceName }?.let { voice ->
                    textToSpeech?.voice = voice
                    selectedVoice = voice
                    Log.d(DEBUG_TAG, "Using saved voice: ${voice.name}")
                    return
                }
            }

            // Auto-select best available voice - prefer neural/natural voices (English or Spanish only)
            val allVoices = textToSpeech?.voices
            if (allVoices != null && allVoices.isNotEmpty()) {
                // Filter to English and Spanish only
                val voices = allVoices.filter { voice ->
                    val language = voice.locale.language.lowercase()
                    language == "en" || language == "es"
                }
                
                if (voices.isNotEmpty()) {
                    // Prioritize neural voices (most natural sounding)
                    val preferredVoice = voices.firstOrNull { voice ->
                        // Google TTS neural voices often have these patterns
                        voice.name.contains("neural", ignoreCase = true) ||
                        voice.name.contains("en-us-neural", ignoreCase = true) ||
                        voice.name.contains("en-gb-neural", ignoreCase = true) ||
                        voice.name.contains("Wavenet", ignoreCase = true) ||
                        voice.name.contains("WaveNet", ignoreCase = true)
                    } ?: voices.firstOrNull { voice ->
                        // Then enhanced/high quality voices
                        voice.name.contains("enhanced", ignoreCase = true) ||
                        voice.quality >= Voice.QUALITY_HIGH
                    } ?: voices.firstOrNull { voice ->
                        // Then any high quality voice
                        voice.quality >= Voice.QUALITY_NORMAL
                    } ?: voices.first()

                    textToSpeech?.voice = preferredVoice
                    selectedVoice = preferredVoice
                    Log.d(DEBUG_TAG, "Selected voice: ${preferredVoice.name}, Quality: ${preferredVoice.quality}")
                }
            }
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "Error selecting voice", e)
        }
    }

    fun getAvailableVoices(): List<Voice> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val allVoices = textToSpeech?.voices?.toList() ?: emptyList()
            val engineName = getCurrentEngineName()
            Log.d(DEBUG_TAG, "[DEBUG] getAvailableVoices: engine='$engineName' totalFromEngine=${allVoices.size} names=${allVoices.take(8).map { it.name }}")
            
            // Filter to only English and Spanish voices
            val filteredVoices = allVoices.filter { voice ->
                val language = voice.locale.language.lowercase()
                language == "en" || language == "es"
            }
            
            Log.d(DEBUG_TAG, "[DEBUG] getAvailableVoices: filtered en/es count=${filteredVoices.size} names=${filteredVoices.take(8).map { it.name }}")
            filteredVoices.forEach { voice ->
                val isNeural = voice.name.contains("neural", ignoreCase = true) || voice.name.contains("wavenet", ignoreCase = true)
                Log.d(DEBUG_TAG, "  voice: ${voice.name} locale=${voice.locale} quality=${voice.quality} neural=$isNeural")
            }
            
            // Sort by language (English first), then by quality (neural/high quality first)
            filteredVoices.sortedWith(compareBy<Voice> { voice ->
                val language = voice.locale.language.lowercase()
                when (language) {
                    "en" -> 0  // English first
                    "es" -> 1  // Spanish second
                    else -> 2
                }
            }.thenByDescending { voice ->
                when {
                    voice.name.contains("neural", ignoreCase = true) -> 3
                    voice.name.contains("Wavenet", ignoreCase = true) || voice.name.contains("WaveNet", ignoreCase = true) -> 3
                    voice.name.contains("enhanced", ignoreCase = true) -> 2
                    voice.quality >= Voice.QUALITY_HIGH -> 1
                    else -> 0
                }
            })
        } else {
            emptyList()
        }
    }
    
    fun getVoiceGender(voice: Voice): String {
        val name = voice.name.lowercase()
        return when {
            name.contains("female", ignoreCase = true) || 
            name.contains("woman", ignoreCase = true) ||
            name.contains("f-", ignoreCase = true) -> "Female"
            name.contains("male", ignoreCase = true) || 
            name.contains("man", ignoreCase = true) ||
            name.contains("m-", ignoreCase = true) -> "Male"
            else -> "Unknown"
        }
    }
    
    fun getVoiceLanguage(voice: Voice): String {
        val locale = voice.locale
        val language = locale.language.lowercase()
        val country = locale.country.lowercase()
        return when {
            language == "en" -> when (country) {
                "us" -> "English (US)"
                "gb" -> "English (UK)"
                else -> "English"
            }
            language == "es" -> when (country) {
                "es" -> "Spanish (Spain)"
                "mx" -> "Spanish (Mexico)"
                "us" -> "Spanish (US)"
                else -> "Spanish"
            }
            else -> locale.displayLanguage
        }
    }
    
    fun hasNeuralVoices(): Boolean {
        return getAvailableVoices().any { voice ->
            voice.name.contains("neural", ignoreCase = true) ||
            voice.name.contains("Wavenet", ignoreCase = true) ||
            voice.name.contains("WaveNet", ignoreCase = true) ||
            voice.name.contains("wavenet", ignoreCase = true)
        }
    }
    
    /**
     * Opens system or engine TTS settings so the user can install/download voices.
     * Tries several intents because TTS_SETTINGS is not available on all devices (e.g. some Samsung/Android 15).
     */
    fun openTtsSettingsToDownloadVoices() {
        // 1. Try Google TTS "install voice data" first – works when Google TTS is installed
        if (isPackageInstalled("com.google.android.tts")) {
            try {
                val installIntent = android.content.Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    setPackage("com.google.android.tts")
                }
                context.startActivity(installIntent)
                Log.d(DEBUG_TAG, "Opened Google TTS install voice data")
                return
            } catch (e: Exception) {
                Log.d(DEBUG_TAG, "Google TTS install intent not available: ${e.message}")
            }
        }
        // 2. Try standard TTS settings (not available on all devices)
        try {
            val settingsIntent = android.content.Intent("android.settings.TTS_SETTINGS").apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(settingsIntent)
            Log.d(DEBUG_TAG, "Opened system TTS settings")
            return
        } catch (e: Exception) {
            Log.d(DEBUG_TAG, "TTS_SETTINGS not available: ${e.message}")
        }
        // 3. Open Accessibility settings – user can tap "Text-to-speech" or "Speech"
        try {
            val accessibilityIntent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(accessibilityIntent)
            android.widget.Toast.makeText(context, "Tap Text-to-speech or Speech to manage voices", android.widget.Toast.LENGTH_LONG).show()
            Log.d(DEBUG_TAG, "Opened Accessibility settings")
            return
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "Error opening Accessibility settings", e)
        }
        // 4. Fallback: general Settings
        try {
            val generalIntent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(generalIntent)
            android.widget.Toast.makeText(context, "Go to Accessibility → Text-to-speech to install voices", android.widget.Toast.LENGTH_LONG).show()
            Log.d(DEBUG_TAG, "Opened general Settings")
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "Error opening settings", e)
            android.widget.Toast.makeText(context, "Could not open TTS settings. Go to Settings → Accessibility → Text-to-speech", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Set the preferred TTS engine package. Returns the actual engine that will be used
     * (which might differ if the requested engine is not available)
     */
    fun setEnginePackage(enginePackage: String, onReady: (() -> Unit)? = null): String {
        val actualEngine = when {
            enginePackage == "auto" -> {
                preferencesManager?.setTtsEnginePackage("auto")
                "auto"
            }
            isPackageInstalled(enginePackage) -> {
                preferencesManager?.setTtsEnginePackage(enginePackage)
                enginePackage
            }
            else -> {
                Log.w(DEBUG_TAG, "[DEBUG] setEnginePackage: engine '$enginePackage' not installed, falling back to auto")
                preferencesManager?.setTtsEnginePackage("auto")
                "auto"
            }
        }
        Log.d(DEBUG_TAG, "[DEBUG] setEnginePackage: userSelected='$enginePackage' savedAndUsing='$actualEngine'")
        // Reinitialize TTS with the new engine
        initializeTTS(onReady)
        return actualEngine
    }
    
    fun getCurrentEnginePackage(): String {
        return preferencesManager?.getTtsEnginePackage() ?: "auto"
    }

    fun setVoice(voice: Voice?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            selectedVoice = voice
            if (voice != null && textToSpeech != null) {
                textToSpeech?.voice = voice
                preferencesManager?.setTtsVoiceName(voice.name)
                Log.d(DEBUG_TAG, "Voice set to: ${voice.name}")
            } else {
                preferencesManager?.setTtsVoiceName(null)
                // Re-select best voice when setting to auto
                selectBestVoice()
            }
        }
    }

    fun getCurrentVoice(): Voice? = selectedVoice
    
    fun getCurrentEngineName(): String {
        val enginePackage = resolveEnginePackage()
        return when (enginePackage) {
            "com.samsung.SMT" -> "Samsung TTS"
            "com.google.android.tts" -> "Google TTS"
            null -> "System Default"
            else -> enginePackage
        }
    }

    fun speak(text: String) {
        if (!isInitialized || textToSpeech == null) {
            Log.w(DEBUG_TAG, "[DEBUG] speak: TTS not initialized, skipping: '$text'")
            return
        }

        try {
            textToSpeech?.stop()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val params = android.os.Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_STREAM, "STREAM_NOTIFICATION")
                }
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, null)
            } else {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
            }
            Log.d(DEBUG_TAG, "[DEBUG] speak: text='$text' voice='${selectedVoice?.name ?: "default"}' engine='${getCurrentEngineName()}'")
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "[DEBUG] speak: error", e)
        }
    }

    fun speakNumber(number: Int) {
        speak(number.toString())
    }

    fun previewVoice(voice: Voice?, previewText: String = "1") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && textToSpeech != null && voice != null) {
            try {
                // Temporarily set the voice for preview (don't save to preferences)
                val originalVoice = selectedVoice
                textToSpeech?.voice = voice
                
                // Speak the preview text
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val params = android.os.Bundle().apply {
                        putString(TextToSpeech.Engine.KEY_PARAM_STREAM, "STREAM_NOTIFICATION")
                    }
                    textToSpeech?.speak(previewText, TextToSpeech.QUEUE_FLUSH, params, null)
                } else {
                    textToSpeech?.speak(previewText, TextToSpeech.QUEUE_FLUSH, null)
                }
                
                // Restore original voice after a delay (voice will finish speaking)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (originalVoice != null && textToSpeech != null) {
                        textToSpeech?.voice = originalVoice
                    }
                }, 2000) // Wait 2 seconds for speech to complete
                
                Log.d(DEBUG_TAG, "Previewing voice: ${voice.name}")
            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "Error previewing voice", e)
            }
        }
    }

    fun release() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
    }
}
