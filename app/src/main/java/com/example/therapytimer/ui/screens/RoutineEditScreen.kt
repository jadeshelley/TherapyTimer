package com.example.therapytimer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.therapytimer.data.Exercise
import com.example.therapytimer.data.NamedRoutine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditScreen(
    routine: NamedRoutine?,
    onSave: (NamedRoutine) -> Unit,
    onBack: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var routineName by remember(routine?.id) {
        mutableStateOf(routine?.name ?: "New Routine")
    }
    var exercises by remember(routine?.id) {
        mutableStateOf(
            routine?.exercises ?: listOf(Exercise("Exercise 1", 0, 0))
        )
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (routine == null) "New Routine" else "Edit Routine",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 24.sp)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = routineName,
                onValueChange = { routineName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Routine name") },
                singleLine = true,
                placeholder = { Text("e.g. Morning stretch") }
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Exercises",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            exercises.forEachIndexed { index, exercise ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = exercise.name,
                            onValueChange = { newName ->
                                exercises = exercises.toMutableList().apply {
                                    set(index, exercise.copy(name = newName))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Exercise Name") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = if (exercise.durationSeconds == 0) "" else exercise.durationSeconds.toString(),
                            onValueChange = { newDuration ->
                                if (newDuration.isEmpty() || newDuration.all { it.isDigit() }) {
                                    exercises = exercises.toMutableList().apply {
                                        set(
                                            index,
                                            exercise.copy(durationSeconds = newDuration.toIntOrNull()?.coerceIn(0, 9999) ?: 0)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Duration (seconds)") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = if (exercise.repeats == 0) "" else exercise.repeats.toString(),
                            onValueChange = { newRepeats ->
                                if (newRepeats.isEmpty() || newRepeats.all { it.isDigit() }) {
                                    exercises = exercises.toMutableList().apply {
                                        set(
                                            index,
                                            exercise.copy(repeats = (newRepeats.toIntOrNull() ?: 0).coerceIn(0, 999))
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Times to do (repeats)") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    if (index > 0) {
                                        exercises = exercises.toMutableList().apply {
                                            set(index, get(index - 1))
                                            set(index - 1, exercise)
                                        }
                                    }
                                },
                                enabled = index > 0
                            ) {
                                Text("↑ Move up")
                            }
                            TextButton(
                                onClick = {
                                    if (index < exercises.size - 1) {
                                        val next = exercises[index + 1]
                                        exercises = exercises.toMutableList().apply {
                                            set(index, next)
                                            set(index + 1, exercise)
                                        }
                                    }
                                },
                                enabled = index < exercises.size - 1
                            ) {
                                Text("↓ Move down")
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = { exercises = exercises.filterIndexed { i, _ -> i != index } }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { exercises = exercises + Exercise("New Exercise", 0, 0) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Exercise")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (onDelete != null) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete this routine")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    if (exercises.isNotEmpty()) {
                        val validExercises = exercises.map { ex ->
                            ex.copy(
                                durationSeconds = if (ex.durationSeconds < 1) 1 else ex.durationSeconds,
                                repeats = if (ex.repeats < 1) 1 else ex.repeats
                            )
                        }
                        val name = routineName.ifBlank { "Routine" }
                        val id = routine?.id ?: "r_${System.currentTimeMillis()}"
                        onSave(NamedRoutine(id, name, validExercises))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = exercises.isNotEmpty()
            ) {
                Text("Save", fontSize = 18.sp)
            }
        }
    }
}
