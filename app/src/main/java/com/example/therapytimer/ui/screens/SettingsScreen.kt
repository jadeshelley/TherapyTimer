package com.example.therapytimer.ui.screens

import android.content.Intent
import android.media.RingtoneManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.os.Build
import android.speech.tts.Voice
import com.example.therapytimer.billing.BillingManager
import com.example.therapytimer.data.Exercise
import com.example.therapytimer.data.ExerciseRoutine
import com.example.therapytimer.data.NamedRoutine
import com.example.therapytimer.util.PreferencesManager
import com.example.therapytimer.viewmodel.TimerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TimerViewModel,
    preferencesManager: PreferencesManager,
    billingManager: BillingManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var notificationSoundName by remember { mutableStateOf(preferencesManager.getNotificationSoundName()) }
    val isBasicMode by viewModel.isBasicMode.collectAsState()
    val basicModeDuration by viewModel.basicModeDuration.collectAsState()
    var selectedMode by remember { mutableStateOf(if (isBasicMode) "basic" else "custom") }

    val exerciseRoutine by viewModel.exerciseRoutine.collectAsState()
    var routines by remember { mutableStateOf<List<NamedRoutine>>(emptyList()) }
    var selectedRoutineId by remember { mutableStateOf<String?>(null) }
    var showRoutineEdit by remember { mutableStateOf(false) }
    var editRoutineForNew by remember { mutableStateOf<NamedRoutine?>(null) }
    var showInstructions by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }
    var fullVersionUnlocked by remember { mutableStateOf(preferencesManager.getFullVersionUnlocked()) }
    var billingReady by remember { mutableStateOf(false) }
    var purchaseInProgress by remember { mutableStateOf(false) }
    var restoreInProgress by remember { mutableStateOf(false) }
    val priceString = remember(billingReady) { if (billingReady) billingManager.getPriceString() else null }
    LaunchedEffect(showPaywall) {
        if (showPaywall) {
            billingManager.onPurchaseSuccess = {
                fullVersionUnlocked = true
                showPaywall = false
                routines = preferencesManager.getSavedRoutines()
                selectedRoutineId = preferencesManager.getCurrentRoutineId()
            }
            billingManager.startConnection { ready: Boolean ->
                billingReady = ready
            }
        }
    }

    // Snapshot of state when Settings was opened (for unsaved-changes detection)
    val initialMode = remember { mutableStateOf(if (isBasicMode) "basic" else "custom") }
    val initialRoutineId = remember { mutableStateOf(preferencesManager.getCurrentRoutineId()) }
    val initialBasicModeDuration = remember { mutableStateOf(preferencesManager.getBasicModeDuration()) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    val hasUnsavedChanges = (selectedMode != initialMode.value) ||
        (selectedMode == "basic" && basicModeDuration != initialBasicModeDuration.value) ||
        (selectedMode == "custom" && selectedRoutineId != initialRoutineId.value)
    LaunchedEffect(selectedMode) {
        if (selectedMode == "custom") {
            routines = preferencesManager.getSavedRoutines()
            val currentId = preferencesManager.getCurrentRoutineId()
            selectedRoutineId = routines.find { it.id == currentId }?.id ?: routines.firstOrNull()?.id
        }
    }
    LaunchedEffect(showRoutineEdit) {
        if (!showRoutineEdit) {
            routines = preferencesManager.getSavedRoutines()
        }
    }

    if (showRoutineEdit) {
        BackHandler { showRoutineEdit = false; editRoutineForNew = null }
        RoutineEditScreen(
            routine = editRoutineForNew,
            onSave = { savedRoutine ->
                val list = if (editRoutineForNew != null) {
                    routines.map { if (it.id == savedRoutine.id) savedRoutine else it }
                } else {
                    routines + savedRoutine
                }
                preferencesManager.setSavedRoutines(list)
                preferencesManager.setCurrentRoutineId(savedRoutine.id)
                viewModel.setExerciseRoutine(savedRoutine.toExerciseRoutine())
                preferencesManager.setIsBasicMode(false)
                showRoutineEdit = false
                editRoutineForNew = null
            },
            onBack = {
                showRoutineEdit = false
                editRoutineForNew = null
            },
            onDelete = if (editRoutineForNew != null) {
                {
                    val toDelete = editRoutineForNew!!
                    val list = routines.filter { it.id != toDelete.id }
                    preferencesManager.setSavedRoutines(list)
                    if (selectedRoutineId == toDelete.id) {
                        selectedRoutineId = list.firstOrNull()?.id
                        viewModel.setExerciseRoutine(
                            list.firstOrNull()?.toExerciseRoutine()
                                ?: ExerciseRoutine(listOf(Exercise("Exercise 1", 30, 1)))
                        )
                        preferencesManager.setCurrentRoutineId(selectedRoutineId)
                    }
                    showRoutineEdit = false
                    editRoutineForNew = null
                }
            } else null
        )
    } else if (showInstructions) {
        BackHandler { showInstructions = false }
        InstructionsScreen(onNavigateBack = { showInstructions = false })
    } else {
    BackHandler {
        if (hasUnsavedChanges) showUnsavedDialog = true else onNavigateBack()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) showUnsavedDialog = true else onNavigateBack()
                    }) {
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
            // Save button (top)
            val performSave: () -> Unit = {
                if (selectedMode == "basic") {
                    viewModel.setBasicMode(true)
                    preferencesManager.setIsBasicMode(true)
                    preferencesManager.setBasicModeDuration(basicModeDuration)
                } else {
                    if (selectedRoutineId != null) {
                        preferencesManager.setCurrentRoutineId(selectedRoutineId)
                        viewModel.setExerciseRoutine(
                            routines.find { it.id == selectedRoutineId }?.toExerciseRoutine()
                                ?: ExerciseRoutine(listOf(Exercise("Exercise 1", 30, 1)))
                        )
                        preferencesManager.setIsBasicMode(false)
                    }
                }
                initialMode.value = selectedMode
                initialRoutineId.value = selectedRoutineId
                initialBasicModeDuration.value = basicModeDuration
                onNavigateBack()
            }
            Button(
                onClick = performSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 16.dp)
            ) {
                Text("Save all changes", fontSize = 18.sp)
            }

            Text(
                text = "Mode Selection",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Mode selection – takes effect immediately; no Save required to use the selected mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FilterChip(
                    selected = selectedMode == "basic",
                    onClick = {
                        selectedMode = "basic"
                        viewModel.setBasicMode(true)
                        preferencesManager.setIsBasicMode(true)
                    },
                    label = { Text("Basic Mode") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedMode == "custom",
                    onClick = {
                        selectedMode = "custom"
                        viewModel.setBasicMode(false)
                        preferencesManager.setIsBasicMode(false)
                        viewModel.setExerciseRoutine(
                            preferencesManager.getCurrentRoutine()
                                ?: ExerciseRoutine(listOf(Exercise("Exercise 1", 30, 1)))
                        )
                    },
                    label = { Text("Custom Mode") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Mute all sounds
            var muteAllSounds by remember { mutableStateOf(preferencesManager.getMuteAllSounds()) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mute all sounds",
                    fontSize = 16.sp
                )
                Switch(
                    checked = muteAllSounds,
                    onCheckedChange = {
                        muteAllSounds = it
                        preferencesManager.setMuteAllSounds(it)
                    }
                )
            }

            // Notification Sound Setting
            Text(
                text = "Notification Sound",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            val ringtonePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as android.net.Uri?
                    }
                    if (uri != null) {
                        preferencesManager.setNotificationSoundUri(uri)
                        val ringtone = RingtoneManager.getRingtone(context, uri)
                        val name = ringtone?.getTitle(context) ?: "Custom Sound"
                        preferencesManager.setNotificationSoundName(name)
                        notificationSoundName = name
                    } else {
                        // User selected "None" or default
                        preferencesManager.setNotificationSoundUri(null)
                        preferencesManager.setNotificationSoundName("Default")
                        notificationSoundName = "Default"
                    }
                }
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = notificationSoundName,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Tap to change",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification Sound")
                                val currentUri = preferencesManager.getNotificationSoundUri()
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            }
                            ringtonePickerLauncher.launch(intent)
                        }
                    ) {
                        Text("Change")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // How to use / Instructions
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { showInstructions = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "How to use this app",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text("→", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (selectedMode != "basic") {
                // Custom mode: routine list; free users see only Demo Routine and cannot Edit/Add/Import/Export
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Routine",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (fullVersionUnlocked) {
                        Button(
                            onClick = {
                                editRoutineForNew = selectedRoutineId?.let { id -> routines.find { it.id == id } }
                                showRoutineEdit = true
                            },
                            enabled = selectedRoutineId != null
                        ) {
                            Text("Edit")
                        }
                    } else {
                        Button(
                            onClick = { showPaywall = true },
                            enabled = selectedRoutineId != null
                        ) {
                            Text("Edit")
                        }
                    }
                }
                if (routines.isEmpty()) {
                    Text(
                        text = "No routines yet. Add one below.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        routines.forEach { r ->
                            val isSelected = r.id == selectedRoutineId
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedRoutineId = r.id },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = r.name,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
                if (fullVersionUnlocked) {
                    Button(
                        onClick = {
                            editRoutineForNew = null
                            showRoutineEdit = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ New routine")
                    }
                } else {
                    Button(
                        onClick = { showPaywall = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ New routine (Unlock full version)")
                    }
                }

                // Backup / Import — only when full version unlocked
                if (fullVersionUnlocked) {
                val selectedRoutine = selectedRoutineId?.let { id -> routines.find { it.id == id } }
                val backupLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri != null && selectedRoutine != null) {
                        try {
                            context.contentResolver.openOutputStream(uri)?.use { os ->
                                os.write(preferencesManager.routineToExportJson(selectedRoutine).toByteArray(Charsets.UTF_8))
                            }
                            Toast.makeText(context, "Routine backed up", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Backup failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        try {
                            val raw = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
                            val imported = preferencesManager.parseRoutinesFromImport(raw)
                            if (imported.isEmpty()) {
                                Toast.makeText(context, "No valid routine in file", Toast.LENGTH_SHORT).show()
                            } else {
                                val existingIds = routines.map { it.id }.toSet()
                                val withNewIds = imported.mapIndexed { i, r ->
                                    val newId = if (r.id in existingIds) "import_${System.currentTimeMillis()}_$i" else r.id
                                    r.copy(id = newId)
                                }
                                val merged = routines + withNewIds
                                preferencesManager.setSavedRoutines(merged)
                                val first = withNewIds.first()
                                preferencesManager.setCurrentRoutineId(first.id)
                                viewModel.setExerciseRoutine(first.toExerciseRoutine())
                                selectedRoutineId = first.id
                                routines = merged
                                Toast.makeText(context, "Imported ${imported.size} routine(s)", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val name = selectedRoutine?.name?.replace(Regex("[^a-zA-Z0-9 ._-]"), "_") ?: "backup"
                            backupLauncher.launch("TherapyTimer_${name}.json")
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selectedRoutine != null
                    ) {
                        Text("Backup routine", fontSize = 14.sp)
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import routine", fontSize = 14.sp)
                    }
                }
                }

                // Unlock full version (show when free)
                if (!fullVersionUnlocked) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .clickable { showPaywall = true },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Unlock full version",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Add and edit routines, import & export",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("→", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (showPaywall) {
                AlertDialog(
                    onDismissRequest = { showPaywall = false },
                    title = { Text("Unlock full version") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Get full access to custom mode: add and edit routines, reorder exercises, backup and import routines. " +
                                "Basic mode and the demo routine remain free."
                            )
                            priceString?.let { price ->
                                Text(
                                    text = price,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val activity = context as? Activity
                                    if (activity != null && billingReady) {
                                        purchaseInProgress = true
                                        billingManager.launchPurchase(activity) { _: Boolean ->
                                            purchaseInProgress = false
                                        }
                                    } else if (activity == null) {
                                        Toast.makeText(context, "Unable to start purchase", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Loading… Try again in a moment.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = billingReady && !purchaseInProgress
                            ) {
                                Text(if (purchaseInProgress) "Opening…" else (priceString ?: "Unlock"))
                            }
                            if (!billingReady) {
                                Text(
                                    text = "Purchases are available when the app is installed from the Play Store.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = {
                                        preferencesManager.setFullVersionUnlocked(true)
                                        fullVersionUnlocked = true
                                        showPaywall = false
                                        routines = preferencesManager.getSavedRoutines()
                                        selectedRoutineId = preferencesManager.getCurrentRoutineId()
                                        Toast.makeText(context, "Unlocked for testing", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Unlock for testing")
                                }
                            }
                            TextButton(
                                onClick = {
                                    restoreInProgress = true
                                    billingManager.restorePurchases { restored: Boolean ->
                                        (context as? Activity)?.runOnUiThread {
                                            restoreInProgress = false
                                            if (restored) {
                                                fullVersionUnlocked = true
                                                showPaywall = false
                                                routines = preferencesManager.getSavedRoutines()
                                                selectedRoutineId = preferencesManager.getCurrentRoutineId()
                                                Toast.makeText(context, "Purchase restored", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "No previous purchase found", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            ) {
                                Text(if (restoreInProgress) "Checking…" else "Restore purchases")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPaywall = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showUnsavedDialog) {
                AlertDialog(
                    onDismissRequest = { showUnsavedDialog = false },
                    title = { Text("Unsaved changes") },
                    text = { Text("You have unsaved changes. Save before leaving?") },
                    confirmButton = {
                        TextButton(onClick = {
                            performSave()
                            showUnsavedDialog = false
                        }) {
                            Text("Save", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            TextButton(onClick = {
                                selectedMode = initialMode.value
                                selectedRoutineId = initialRoutineId.value
                                viewModel.setBasicMode(initialMode.value == "basic")
                                preferencesManager.setIsBasicMode(initialMode.value == "basic")
                                preferencesManager.setBasicModeDuration(initialBasicModeDuration.value)
                                viewModel.setBasicModeDuration(initialBasicModeDuration.value)
                                if (initialMode.value == "custom" && initialRoutineId.value != null) {
                                    preferencesManager.setCurrentRoutineId(initialRoutineId.value)
                                    val routine = routines.find { it.id == initialRoutineId.value }
                                    viewModel.setExerciseRoutine(
                                        routine?.toExerciseRoutine()
                                            ?: ExerciseRoutine(listOf(Exercise("Exercise 1", 30, 1)))
                                    )
                                }
                                showUnsavedDialog = false
                                onNavigateBack()
                            }) {
                                Text("Don't save", color = MaterialTheme.colorScheme.error)
                            }
                            TextButton(onClick = { showUnsavedDialog = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                )
            }
        }
    }
    }
}
