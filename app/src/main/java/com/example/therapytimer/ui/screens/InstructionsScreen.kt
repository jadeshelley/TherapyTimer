package com.example.therapytimer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionsScreen(
    onNavigateBack: () -> Unit,
    onContinue: ((dontShowAgain: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var dontShowAgain by remember { mutableStateOf(false) }
    val isOnLaunch = onContinue != null

    BackHandler(onBack = {
        if (isOnLaunch) onContinue?.invoke(false) else onNavigateBack()
    })
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "How to use this app",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    if (!isOnLaunch) {
                        IconButton(onClick = onNavigateBack) {
                            Text("←", fontSize = 24.sp)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            sectionTitle("Overview")
            body(
                "Therapy Timer helps you time and track exercises and routines. In Basic Mode you run a single timed exercise. Custom mode lets you create and save custom exercise routines to help you keep track of your therapy session."
            )

            sectionTitle("Basic Mode")
            body(
                "• One exercise: set the duration (seconds) on the main screen.\n" +
                "• Tap Start to begin the countdown.\n" +
                "• When the timer finishes, a sound plays. Tap Next to start the next rep, or Reset to start over.\n" +
                "• You can use voice commands in Basic mode too — turn on Voice Control on the main screen."
            )

            sectionTitle("Custom Mode")
            body(
                "• Create and select routines in Settings. Each routine has exercises with duration and number of reps.\n" +
                "• On the main screen, swipe left or right on the exercise chips to see all exercises. Tap a chip to jump to that exercise."
            )

            sectionTitle("Buttons")
            body(
                "• Begin Exercise / Next Rep — Start the timer or, after a rep completes, go to the next rep.\n" +
                "• Restart — Redo the current rep (same rep number).\n" +
                "• Reset — Reset this exercise back to the first rep.\n" +
                "• Done — Finish this exercise and go to the next. When the routine is complete, Exit closes the app."
            )

            sectionTitle("Voice commands")
            body(
                "Voice commands work in both Basic and Custom mode. With Voice Control on (toggle on the main screen), you can say:\n" +
                "• Start — Start the timer or advance to the next rep.\n" +
                "• Next — Go to the next rep.\n" +
                "• Restart — Redo the current rep.\n" +
                "• Reset — Reset this exercise.\n" +
                "• Done — Finish this exercise and go to the next. When the routine is complete, Exit closes the app.\n\n" +
                "When the app accepts a voice command, it plays a short confirmation beep so you don't have to look at the phone. You can turn up the Alarm volume for the beep and keep other volumes lower (Settings → Mute all sounds turns off every sound).\n\n" +
                "Voice recognition is paused while you are in Settings."
            )

            sectionTitle("Settings")
            body(
                "• Mode — Switch between Basic and Custom.\n" +
                "• Mute all sounds — Turn off all app sounds (confirmation beep, notification, count, and finished).\n" +
                "• Notification sound — Choose the sound that plays when a rep or exercise finishes.\n" +
                "• Custom mode — Select a routine, or create and edit routines (add exercises, set duration and reps).\n" +
                "• Tap Save all changes to apply and return to the timer. If you leave without saving, you’ll be asked to save or discard changes."
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

            if (isOnLaunch) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it }
                    )
                    Text(
                        text = "Don't show this again",
                        modifier = Modifier.padding(start = 8.dp),
                        fontSize = 15.sp
                    )
                }
                Button(
                    onClick = { onContinue?.invoke(dontShowAgain) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Continue", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun sectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun body(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}
