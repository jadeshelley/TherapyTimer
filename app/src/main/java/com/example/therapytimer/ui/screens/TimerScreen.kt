package com.example.therapytimer.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.app.Activity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.therapytimer.ui.TimerState
import com.example.therapytimer.util.PreferencesManager
import com.example.therapytimer.viewmodel.TimerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    preferencesManager: PreferencesManager,
    onNavigateToSettings: () -> Unit,
    isVoiceControlEnabled: Boolean,
    onToggleVoiceControl: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timerState by viewModel.timerState.collectAsState()
    val currentCount by viewModel.currentCount.collectAsState()
    val isBasicMode by viewModel.isBasicMode.collectAsState()
    val basicModeDuration by viewModel.basicModeDuration.collectAsState()
    val currentExerciseIndex by viewModel.currentExerciseIndex.collectAsState()
    val exerciseRoutine by viewModel.exerciseRoutine.collectAsState()
    val completedExerciseIndices by viewModel.completedExerciseIndices.collectAsState()
    val currentExerciseName = remember(isBasicMode, currentExerciseIndex) {
        viewModel.getCurrentExerciseName()
    }
    val currentRepeats = remember(isBasicMode, currentExerciseIndex) {
        viewModel.getCurrentExerciseRepeats()
    }
    val routineComplete = remember(isBasicMode, currentExerciseIndex, currentCount, completedExerciseIndices) {
        viewModel.isRoutineComplete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Therapy Timer") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Text("⚙️", fontSize = 20.sp)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Reset entire routine (custom mode only) – between app bar and chips
            if (!isBasicMode && exerciseRoutine != null) {
                OutlinedButton(
                    onClick = { viewModel.resetRoutine() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp)
                ) {
                    Text("Reset entire routine", fontSize = 14.sp)
                }
            }

            // Routine progress list (custom mode only) – tap a chip to jump to that exercise
            if (!isBasicMode && exerciseRoutine != null) {
                val list = exerciseRoutine!!.exercises
                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                val canScrollBackward by remember {
                    derivedStateOf {
                        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
                    }
                }
                val canScrollForward by remember {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        lastVisible < layoutInfo.totalItemsCount - 1
                    }
                }

                LaunchedEffect(currentExerciseIndex) {
                    if (list.isNotEmpty()) {
                        val targetIndex = currentExerciseIndex.coerceIn(0, list.lastIndex)
                        listState.animateScrollToItem(targetIndex)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small left chevron – show when there’s content to the left
                    if (canScrollBackward) {
                        Text(
                            text = "‹",
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(4.dp)
                                .clickable {
                                    scope.launch {
                                        val target = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                                        listState.animateScrollToItem(target)
                                    }
                                }
                        )
                    }
                    LazyRow(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(list) { index, ex ->
                            val isDone = index in completedExerciseIndices
                            val status = when {
                                isDone -> "✓"
                                index == currentExerciseIndex -> "•"
                                else -> "○"
                            }
                            val isCurrent = index == currentExerciseIndex
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                onClick = { viewModel.goToExercise(index) },
                                color = when {
                                    isDone -> MaterialTheme.colorScheme.primaryContainer
                                    isCurrent -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier
                                    .widthIn(min = if (isCurrent) 100.dp else 72.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = "$status ${index + 1}. ${ex.name}",
                                    fontSize = if (isCurrent) 14.sp else 11.sp,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    // Small right chevron – show when there’s content to the right
                    if (canScrollForward) {
                        Text(
                            text = "›",
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(4.dp)
                                .clickable {
                                    scope.launch {
                                        val target = (listState.firstVisibleItemIndex + 1).coerceAtMost(list.lastIndex)
                                        listState.animateScrollToItem(target)
                                    }
                                }
                        )
                    }
                }

                Text(
                    text = "Tap a chip to go to that exercise.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 8.dp)
                )
            }

            // Current exercise name – larger, wraps, with border
            if (!isBasicMode) {
                Text(
                    text = if (routineComplete) "Routine complete!" else currentExerciseName,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 0.dp, 16.dp, 16.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                )
            }

            // Count display (in custom mode with repeats: show "Rep X of Y" subtitle)
            if (!isBasicMode && currentRepeats > 1) {
                val repText = when (timerState) {
                    is TimerState.Completed -> "Rep $currentCount of $currentRepeats"
                    else -> "Rep ${currentCount + 1} of $currentRepeats"
                }
                Text(
                    text = repText,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Text(
                text = currentCount.toString(),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 32.dp)
            )

            // Timer display
            when (val state = timerState) {
                is TimerState.Idle -> {
                    Text(
                        text = "Ready",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is TimerState.Running -> {
                    Text(
                        text = state.remainingSeconds.toString(),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is TimerState.Completed -> {
                    Text(
                        text = "Complete!",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Action buttons: row 1 = Next/Start/Restart + Reset (two buttons so text is readable)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (timerState) {
                        is TimerState.Idle -> {
                            Button(
                                onClick = { viewModel.startTimer() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            ) {
                                Text("Start", fontSize = 18.sp)
                            }
                        }
                        is TimerState.Running -> {
                            Button(
                                onClick = { viewModel.restartCurrentCount() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text("Restart", fontSize = 18.sp)
                                    Text("this Rep", fontSize = 12.sp)
                                }
                            }
                        }
                        is TimerState.Completed -> {
                            Button(
                                onClick = { viewModel.nextCount() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            ) {
                                val (mainLabel, subLabel) = if (currentCount == 0) {
                                    "Start" to null
                                } else {
                                    "Next" to "Rep"
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(mainLabel, fontSize = 18.sp)
                                    if (subLabel != null) {
                                        Text(subLabel, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.resetExercise() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Reset", fontSize = 18.sp)
                            Text("this exercise", fontSize = 12.sp)
                        }
                    }
                }

                // Done = go to next exercise, or Exit when routine complete (custom mode only)
                if (!isBasicMode) {
                    val context = LocalContext.current
                    if (routineComplete) {
                        Button(
                            onClick = {
                                (context as? Activity)?.finishAndRemoveTask()
                                android.os.Process.killProcess(android.os.Process.myPid())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("Exit", fontSize = 18.sp)
                                Text("Or choose an exercise above to start again", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Button(
                            onClick = { viewModel.completeExerciseAndGoToNext() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("Done", fontSize = 18.sp)
                                Text("Go to next exercise", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Duration control (basic mode) – editable on main screen
            if (isBasicMode) {
                var durationText by remember(basicModeDuration) { mutableStateOf(basicModeDuration.toString()) }
                LaunchedEffect(basicModeDuration) { durationText = basicModeDuration.toString() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Duration (seconds):",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { new ->
                            if (new.all { it.isDigit() }) {
                                durationText = new
                                val n = new.toIntOrNull()?.coerceIn(1, 9999) ?: return@OutlinedTextField
                                viewModel.setBasicModeDuration(n)
                                preferencesManager.setBasicModeDuration(n)
                            }
                        },
                        modifier = Modifier.widthIn(min = 64.dp, max = 96.dp),
                        singleLine = true
                    )
                }
            }

            // Voice control toggle – tertiary color so it’s distinct from Begin Exercise/Next (primary) and Restart (secondary)
            Button(
                onClick = onToggleVoiceControl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Text(
                    text = if (isVoiceControlEnabled) {
                        "Voice Control: On (tap to turn off)"
                    } else {
                        "Voice Control: Off (tap to turn on)"
                    },
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (isVoiceControlEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                var showVoiceCommandsDialog by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { showVoiceCommandsDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("Show voice commands", fontSize = 13.sp)
                }
                if (showVoiceCommandsDialog) {
                    AlertDialog(
                        onDismissRequest = { showVoiceCommandsDialog = false },
                        title = { Text("Voice Commands") },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "Start",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Start the timer or advance to the next rep.",
                                        fontSize = 14.sp
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Next",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Go to the next rep.",
                                        fontSize = 14.sp
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Restart",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Redo the current count (same rep number)",
                                        fontSize = 14.sp
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Done",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Finish this exercise and go to the next one",
                                        fontSize = 14.sp
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Reset",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Reset this exercise back to the first rep",
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showVoiceCommandsDialog = false }) {
                                Text("Done")
                            }
                        }
                    )
                }
            }
        }
    }
}
