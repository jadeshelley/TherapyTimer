package com.example.therapytimer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.therapytimer.ui.screens.CustomSplashScreen
import com.example.therapytimer.ui.screens.InstructionsScreen
import com.example.therapytimer.ui.screens.ModelLoadingScreen
import com.example.therapytimer.ui.screens.SettingsScreen
import com.example.therapytimer.ui.screens.TimerScreen
import com.example.therapytimer.ui.theme.TherapyTimerTheme
import com.example.therapytimer.billing.BillingManager
import com.example.therapytimer.util.PreferencesManager
import com.example.therapytimer.util.SoundPlayer
import com.example.therapytimer.util.VoskModelLoadState
import com.example.therapytimer.util.VoiceRecognitionManager
import com.example.therapytimer.viewmodel.TimerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: TimerViewModel by viewModels()
    /** Created in onCreate so model loading starts immediately; callbacks set when permission granted. */
    private lateinit var voiceRecognitionManager: VoiceRecognitionManager
    private var soundPlayer: SoundPlayer? = null
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var billingManager: BillingManager
    private var audioManager: AudioManager? = null
    /** Saved notification volume before we mute; -1 means not saved. Restored on unmute/exit. */
    private var savedNotificationVolume: Int = -1
    /** Saved media volume before app changed it; -1 means not saved. Restored on pause/destroy. */
    private var savedMediaVolumeBeforeApp: Int = -1
    private var isVoiceControlEnabled: Boolean = true  // Restored from preferences in onCreate
    private var hasReachedMainScreen: Boolean = false
    private var pendingPlayFinished: Boolean = false
    /** Guard: only allow one routine-complete "finished" sound per completion; cleared when user starts/resets. */
    private var routineCompleteSoundPlayed: Boolean = false
    /** Start the count this many ms after notification starts (give notification time to finish). */
    private val notificationToCountDelayMs = 700L
    /** True only when the timer screen is visible; voice is processed only when this is true. */
    private var isOnTimerScreen: Boolean = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeVoiceRecognition()
        }
    }

    /** How long the system splash (app icon on teal) stays visible. Custom slide sequence starts after this. */
    private val systemSplashDisplayMs = 2000L

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashStartTime = System.currentTimeMillis()
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            System.currentTimeMillis() - splashStartTime < systemSplashDisplayMs
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        preferencesManager = PreferencesManager(this)
        billingManager = BillingManager(this, preferencesManager)
        soundPlayer = SoundPlayer(this, preferencesManager)
        voiceRecognitionManager = VoiceRecognitionManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        isVoiceControlEnabled = preferencesManager.getVoiceControlEnabled()

        // Apply app preferred media volume (50% first run; remember if user changes it). Save device level to restore on exit.
        applyAppMediaVolume()

        // Restore saved mode and routine (survives app reboot)
        viewModel.setBasicMode(preferencesManager.getIsBasicMode())
        viewModel.setBasicModeDuration(preferencesManager.getBasicModeDuration())
        if (!preferencesManager.getIsBasicMode()) {
            preferencesManager.getCurrentRoutine()?.let { viewModel.setExerciseRoutine(it) }
        }

        // Stop voice recognition 1s before timer ends so we don't hear the beep
        viewModel.onTimerOneSecondLeft = {
            runOnUiThread {
                if (isVoiceControlEnabled) {
                    voiceRecognitionManager.stopListening()
                }
            }
        }

        // Set up timer completion: play notification, then count (Revoicer 1–20) after short delay. Restart listening right away so user can speak soon.
        viewModel.onTimerComplete = { countJustCompleted ->
            runOnUiThread {
                if (isVoiceControlEnabled) voiceRecognitionManager.startListening()
            }
            if (countJustCompleted in 1..20) {
                soundPlayer?.playNotificationSound()
                Handler(Looper.getMainLooper()).postDelayed({
                    soundPlayer?.playCountNumber(countJustCompleted) {
                        if (pendingPlayFinished && !routineCompleteSoundPlayed) {
                            pendingPlayFinished = false
                            routineCompleteSoundPlayed = true
                            soundPlayer?.playRandomFinished()
                        }
                    }
                }, notificationToCountDelayMs)
            } else {
                // Count > 20: notification only, then routine-complete/voice
                soundPlayer?.playNotificationSound {
                    if (pendingPlayFinished && !routineCompleteSoundPlayed) {
                        pendingPlayFinished = false
                        routineCompleteSoundPlayed = true
                        Handler(Looper.getMainLooper()).postDelayed({
                            soundPlayer?.playRandomFinished()
                        }, 500)
                    }
                }
            }
        }

        // Routine complete: if from timer, play "finished" after the last rep's count; if from Done button/voice, play now
        viewModel.onRoutineComplete = { fromTimerCompletion ->
            runOnUiThread {
                if (fromTimerCompletion) {
                    pendingPlayFinished = true
                } else {
                    if (!routineCompleteSoundPlayed) {
                        routineCompleteSoundPlayed = true
                        soundPlayer?.playRandomFinished()
                    }
                }
            }
        }

        // Clear routine-complete sound guard when user starts or resets so next completion can play
        viewModel.onRunStarted = {
            runOnUiThread { routineCompleteSoundPlayed = false }
        }

        viewModel.onStartOrNextConfirm = {
            runOnUiThread { soundPlayer?.playConfirmationBeep() }
        }

        // Request microphone permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            initializeVoiceRecognition()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            var voiceControlEnabledState by remember { mutableStateOf(isVoiceControlEnabled) }

            TherapyTimerTheme {
                TherapyTimerApp(
                    viewModel = viewModel,
                    preferencesManager = preferencesManager,
                    billingManager = billingManager,
                    voiceRecognitionManager = voiceRecognitionManager,
                    voiceControlEnabled = voiceControlEnabledState,
                    customSplashStartDelayMs = systemSplashDisplayMs,
                    onScreenChange = { isTimer ->
                        isOnTimerScreen = isTimer
                    },
                    onMainScreenShown = {
                        hasReachedMainScreen = true
                        if (isVoiceControlEnabled) {
                            setNotificationMute(true)
                            voiceRecognitionManager.startListening()
                        }
                    },
                    onSettingsOpened = {
                        if (isVoiceControlEnabled) {
                            voiceRecognitionManager.stopListening()
                            setNotificationMute(false)
                        }
                    },
                    onSettingsClosed = {
                        if (isVoiceControlEnabled) {
                            setNotificationMute(true)
                            voiceRecognitionManager.startListening()
                        }
                    },
                    onToggleVoiceControl = {
                        isVoiceControlEnabled = !isVoiceControlEnabled
                        voiceControlEnabledState = isVoiceControlEnabled
                        preferencesManager.setVoiceControlEnabled(isVoiceControlEnabled)
                        if (isVoiceControlEnabled) {
                            setNotificationMute(true)
                            voiceRecognitionManager.startListening()
                        } else {
                            voiceRecognitionManager.stopListening()
                            setNotificationMute(false)
                        }
                    }
                )
            }
        }
    }

    private fun initializeVoiceRecognition() {
        android.util.Log.d("MainActivity", "Setting up voice recognition callbacks...")
        voiceRecognitionManager.apply {
            onNumberDetected = { _ ->
                // Numbers are not used to start the timer (avoids app hearing itself say "one" and starting)
                // Use "begin exercise" to start or "next" to advance.
            }
            onNextDetected = {
                runOnUiThread {
                    if (!isOnTimerScreen) return@runOnUiThread
                    val timerState = viewModel.timerState.value
                    when (timerState) {
                        is com.example.therapytimer.ui.TimerState.Idle -> viewModel.startTimer()
                        is com.example.therapytimer.ui.TimerState.Completed -> viewModel.nextCount()
                        else -> { /* running, ignore */ }
                    }
                }
            }
            onStartDetected = {
                runOnUiThread {
                    if (!isOnTimerScreen) return@runOnUiThread
                    val timerState = viewModel.timerState.value
                    val currentCount = viewModel.currentCount.value
                    when (timerState) {
                        is com.example.therapytimer.ui.TimerState.Idle -> viewModel.startTimer()
                        is com.example.therapytimer.ui.TimerState.Completed -> {
                            if (currentCount == 0) viewModel.startTimer() else viewModel.nextCount()
                        }
                        else -> { /* Running state - ignore "start" */ }
                    }
                }
            }
            onDoneDetected = {
                runOnUiThread {
                    if (!isOnTimerScreen) return@runOnUiThread
                    if (viewModel.isBasicMode.value) {
                        // In basic mode, "done" behaves like reset (new simple exercise)
                        viewModel.resetExercise()
                    } else {
                        // In custom mode, "done" moves to the next exercise instead of going back to the first
                        viewModel.completeExerciseAndGoToNext()
                    }
                }
            }
            onRestartDetected = {
                runOnUiThread {
                    if (!isOnTimerScreen) return@runOnUiThread
                    viewModel.restartCurrentCount()
                }
            }
            onResetDetected = {
                runOnUiThread {
                    if (!isOnTimerScreen) return@runOnUiThread
                    // Voice "reset" should mirror the Reset button
                    viewModel.resetExercise()
                }
            }
            // Don't start here – wait until user taps Begin and main screen (TimerScreen) is shown via onMainScreenShown().
        }
    }

    /** Set media stream to app preferred level (from prefs; 50% first run). Saves current level so we can restore on pause/destroy. */
    private fun applyAppMediaVolume() {
        val am = audioManager ?: return
        try {
            if (savedMediaVolumeBeforeApp < 0) {
                savedMediaVolumeBeforeApp = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val percent = preferencesManager.getAppMediaVolumePercent()
            val index = (percent / 100.0 * maxVol).toInt().coerceIn(0, maxVol)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "applyAppMediaVolume failed", e)
        }
    }

    /** Save current media volume to prefs (so we remember if user changed it), then restore device volume to what it was before app started. */
    private fun saveAndRestoreMediaVolume() {
        val am = audioManager ?: return
        try {
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val percent = if (maxVol > 0) (current * 100 / maxVol).coerceIn(0, 100) else 50
            preferencesManager.setAppMediaVolumePercent(percent)
            if (savedMediaVolumeBeforeApp >= 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMediaVolumeBeforeApp.coerceIn(0, maxVol), 0)
                savedMediaVolumeBeforeApp = -1
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "saveAndRestoreMediaVolume failed", e)
        }
    }

    /** Mute notification stream (save level, set to 0) or restore saved level. Uses setStreamVolume like TestSound app. */
    private fun setNotificationMute(mute: Boolean) {
        val am = audioManager ?: return
        try {
            if (mute) {
                if (savedNotificationVolume < 0) {
                    savedNotificationVolume = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                }
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            } else {
                if (savedNotificationVolume >= 0) {
                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
                    am.setStreamVolume(
                        AudioManager.STREAM_NOTIFICATION,
                        savedNotificationVolume.coerceIn(0, maxVol),
                        0
                    )
                    savedNotificationVolume = -1
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "setNotificationMute failed", e)
        }
    }

    override fun onPause() {
        super.onPause()
        saveAndRestoreMediaVolume()
        if (isVoiceControlEnabled) {
            voiceRecognitionManager.stopListening()
            setNotificationMute(false)
        }
    }

    override fun onResume() {
        super.onResume()
        applyAppMediaVolume()
        if (hasReachedMainScreen && isVoiceControlEnabled) {
            setNotificationMute(true)
            voiceRecognitionManager.startListening()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveAndRestoreMediaVolume()
        setNotificationMute(false)
        voiceRecognitionManager.destroy()
        soundPlayer?.release()
    }
}

@Composable
fun TherapyTimerApp(
    viewModel: TimerViewModel,
    preferencesManager: PreferencesManager,
    billingManager: BillingManager,
    voiceRecognitionManager: VoiceRecognitionManager,
    voiceControlEnabled: Boolean,
    onToggleVoiceControl: () -> Unit,
    customSplashStartDelayMs: Long = 3000L,
    onScreenChange: (isTimerScreen: Boolean) -> Unit = {},
    onMainScreenShown: () -> Unit = {},
    onSettingsOpened: () -> Unit = {},
    onSettingsClosed: () -> Unit = {}
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCustomSplash by rememberSaveable { mutableStateOf(true) }
    var showInstructionsOnLaunch by rememberSaveable {
        mutableStateOf(
            !preferencesManager.hasShownInstructionsThisInstall() || preferencesManager.getShowInstructionsOnLaunch()
        )
    }
    var continueWithoutVoice by rememberSaveable { mutableStateOf(false) }

    val modelLoadState by voiceRecognitionManager.modelLoadState.collectAsState()
    val showMainApp = modelLoadState is com.example.therapytimer.util.VoskModelLoadState.Ready || continueWithoutVoice

    val isTimerScreenVisible = !showCustomSplash && !showInstructionsOnLaunch && showMainApp && !showSettings
    LaunchedEffect(isTimerScreenVisible) {
        onScreenChange(isTimerScreenVisible)
    }

    if (showCustomSplash) {
        CustomSplashScreen(
            onComplete = { showCustomSplash = false },
            startDelayMs = customSplashStartDelayMs
        )
    } else if (showInstructionsOnLaunch) {
        LaunchedEffect(Unit) {
            preferencesManager.markInstructionsShownThisInstall()
        }
        InstructionsScreen(
            onNavigateBack = { showInstructionsOnLaunch = false },
            onContinue = { dontShowAgain ->
                if (dontShowAgain) preferencesManager.setShowInstructionsOnLaunch(false)
                showInstructionsOnLaunch = false
            }
        )
    } else if (!showMainApp) {
        ModelLoadingScreen(
            state = modelLoadState,
            onContinueAnyway = if (modelLoadState is VoskModelLoadState.Failed) {
                { continueWithoutVoice = true }
            } else null
        )
    } else if (showSettings) {
        BackHandler {
            showSettings = false
            onSettingsClosed()
        }
        SettingsScreen(
            viewModel = viewModel,
            preferencesManager = preferencesManager,
            billingManager = billingManager,
            onNavigateBack = {
                showSettings = false
                onSettingsClosed()
            }
        )
    } else {
        LaunchedEffect(Unit) {
            onMainScreenShown()
        }
        TimerScreen(
            viewModel = viewModel,
            preferencesManager = preferencesManager,
            onNavigateToSettings = {
                onSettingsOpened()
                showSettings = true
            },
            isVoiceControlEnabled = voiceControlEnabled,
            onToggleVoiceControl = onToggleVoiceControl
        )
    }
}