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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onContinue: ((dontShowAgain: Boolean) -> Unit)? = null
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
                "Therapy Timer helps you time and track exercises and routines. Basic mode is free. Custom mode in free version lets you demo a routine.\n\n" +
                "Pro Version features the ability to create and edit your own custom routines. This lets you keep track of your own personal workout and enjoy it hands free!\n\n" +
                "• Set Basic or Custom mode in the settings screen."
            )

            sectionTitle("Basic Mode (Single Exercise)")
            body(
                "• Set the duration (seconds) on the main screen & Say \"Next\" or \"Start\" (or tap Start) to begin the countdown.\n" +
                "• When the timer finishes, a sound plays and the rep count is heard. Say \"Next\" to start the next rep, or Reset to start over.\n" +
                "• Voice commands only work when \"Voice Control\" is turned on — Button on main screen."
            )

            sectionTitle("Custom Mode (Multiple Exercise Routine)")
            body(
                "• Choose a routine in the settings screen. Each routine can have multiple exercises with different durations and rep counts. Edit to change or add to the routine.\n" +
                "• You can create and edit as many routines as you want in \"Pro\". Demo routine is not editable in free version.\n" +
                "• Save then takes you to the main screen to begin using your chosen routine.\n" +
                "• On the main screen, swipe left or right on the exercise chips to see all exercises. You may tap a chip to jump to that exercise and perform it out of order.\n" +
                "• Start the first exercise by saying (voice control on) or tapping \"Start\".\n" +
                "• Routine advances to next exercise when all reps are completed, or user presses \"Finish\".\n" +
                "• Use Reset entire routine (above the chips) to start the whole routine over from the first exercise."
            )

            sectionTitle("Buttons")
            body(
                "• Start / Next Rep — Start the timer or, after a rep completes, go to the next rep.\n" +
                "• Restart — Restart the current rep (same rep number).\n" +
                "• Reset — Reset this exercise back to the first rep.\n" +
                "• Finish — Finish this exercise and go to the next. When the routine is complete, Exit closes the app."
            )

            sectionTitle("Voice commands")
            body(
                "Voice commands work in both Basic and Custom mode. With Voice Control on (toggle on the main screen), you can say:\n" +
                "• Start — Start the timer or advance to the next rep.\n" +
                "• Next — Also start the timer or advance to the next rep.\n" +
                "• Restart — Restart the current rep.\n" +
                "• Reset — Reset this exercise.\n" +
                "• Finish — Finish this exercise and go to the next.\n\n" +
                "Voice matching (Settings): The app can match Voice Commands strictly or more loosely. In Settings, choose Strict, Medium, or Relaxed. Strict accepts only clearly spoken words (This is helpful when the environment is noisy). Medium and Relaxed allow small mishearings. If the timer keeps starting when you didn't say start or next, set Voice Matching to a stricter setting to reduce false triggers.\n\n" +
                "Pro tip: Speaking clearly is important — pronunciation matters more than volume (e.g. the \"t\" in \"Next\" and \"Start\").\n\n" +
                "Settings → \"Mute all sounds\" - turns off all sounds.\n\n" +
                "Voice recognition is paused while you are in Settings."
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

            if (isOnLaunch) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "* You can view these instructions again anytime in \"Settings\".",
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
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
