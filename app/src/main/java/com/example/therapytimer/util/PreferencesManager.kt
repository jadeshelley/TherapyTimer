package com.example.therapytimer.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.example.therapytimer.data.Exercise
import com.example.therapytimer.data.ExerciseRoutine
import com.example.therapytimer.data.NamedRoutine
import org.json.JSONArray
import org.json.JSONObject

class PreferencesManager(context: Context) {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("therapy_timer_prefs", Context.MODE_PRIVATE)

    private val instructionsShownThisInstallFile get() = java.io.File(appContext.noBackupFilesDir, "instructions_shown_install")

    private fun lineToExercise(line: String): Exercise? {
        val parts = line.split("\t")
        if (parts.size < 2) return null
        val name = parts[0].trim().ifEmpty { "Exercise" }
        val duration = parts[1].toIntOrNull()?.coerceIn(0, 9999) ?: 0
        val repeats = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 999) ?: 1
        return Exercise(name, if (duration < 1) 1 else duration, repeats)
    }

    /** Demo routine included on first install when no routines exist. */
    private fun createDemoRoutine(): NamedRoutine {
        return NamedRoutine(
            id = "demo",
            name = "Demo Routine",
            exercises = listOf(
                Exercise("Table Stretch - Front", 10, 2),
                Exercise("Pulley exercise - Front", 5, 2),
                Exercise("Leg Lift", 15, 2),
                Exercise("Arm Swing", 60, 2)
            )
        )
    }

    fun getSavedRoutines(): List<NamedRoutine> {
        if (!getFullVersionUnlocked()) return listOf(createDemoRoutine())
        val raw = prefs.getString("custom_routines_json", null)
        if (raw == null || raw.isEmpty()) {
            // Migrate from old single routine
            val old = prefs.getString("custom_routine", null)
            if (old != null) {
                val exercises = old.split("\n").mapNotNull { line -> lineToExercise(line.trim()) }
                if (exercises.isNotEmpty()) {
                    val migrated = listOf(NamedRoutine("default", "My Routine", exercises))
                    setSavedRoutines(migrated)
                    setCurrentRoutineId("default")
                    return migrated
                }
            }
            // First run: provide demo routine and persist it
            val demo = createDemoRoutine()
            setSavedRoutines(listOf(demo))
            setCurrentRoutineId(demo.id)
            return listOf(demo)
        }
        return parseRoutinesJson(raw)
    }

    fun setSavedRoutines(routines: List<NamedRoutine>) {
        if (!getFullVersionUnlocked()) return
        val arr = JSONArray()
        routines.forEach { r ->
            val obj = JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("exercises", JSONArray().apply {
                    r.exercises.forEach { e ->
                        put(JSONObject().apply {
                            put("n", e.name)
                            put("d", e.durationSeconds)
                            put("r", e.repeats)
                        })
                    }
                })
            }
            arr.put(obj)
        }
        prefs.edit().putString("custom_routines_json", arr.toString()).apply()
    }

    private fun parseRoutinesJson(raw: String): List<NamedRoutine> {
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                parseRoutineObject(obj, i)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseRoutineObject(obj: JSONObject, indexFallback: Int): NamedRoutine? {
        val id = obj.optString("id", "r_$indexFallback")
        val name = obj.optString("name", "Routine ${indexFallback + 1}")
        val exArr = obj.optJSONArray("exercises") ?: JSONArray()
        val exercises = (0 until exArr.length()).mapNotNull { j ->
            val e = exArr.optJSONObject(j) ?: return@mapNotNull null
            val n = e.optString("n", "Exercise")
            val d = e.optInt("d", 30).coerceIn(1, 9999)
            val r = e.optInt("r", 1).coerceIn(1, 999)
            Exercise(n, d, r)
        }
        return if (exercises.isEmpty()) null else NamedRoutine(id, name, exercises)
    }

    /** Serialize a single routine to JSON for backup/export. Same format as one element in saved routines. */
    fun routineToExportJson(routine: NamedRoutine): String {
        val obj = JSONObject().apply {
            put("id", routine.id)
            put("name", routine.name)
            put("exercises", JSONArray().apply {
                routine.exercises.forEach { e ->
                    put(JSONObject().apply {
                        put("n", e.name)
                        put("d", e.durationSeconds)
                        put("r", e.repeats)
                    })
                }
            })
        }
        return obj.toString()
    }

    /**
     * Parse JSON from an imported file. Accepts either a JSON array of routines or a single routine object.
     * Returns list of valid routines (empty if parse failed or no valid routines).
     */
    fun parseRoutinesFromImport(raw: String): List<NamedRoutine> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            if (trimmed.startsWith("[")) {
                parseRoutinesJson(trimmed)
            } else {
                val obj = JSONObject(trimmed)
                listOfNotNull(parseRoutineObject(obj, 0))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getCurrentRoutineId(): String? {
        if (!getFullVersionUnlocked()) return "demo"
        return prefs.getString("current_routine_id", null)?.takeIf { it.isNotEmpty() }
    }

    fun setCurrentRoutineId(id: String?) {
        if (!getFullVersionUnlocked()) return
        prefs.edit().putString("current_routine_id", id ?: "").apply()
    }

    /** Returns the current routine as ExerciseRoutine for the timer, or null. */
    fun getCurrentRoutine(): ExerciseRoutine? {
        val id = getCurrentRoutineId() ?: return null
        val routines = getSavedRoutines()
        val named = routines.find { it.id == id } ?: routines.firstOrNull() ?: return null
        return named.toExerciseRoutine()
    }

    fun getIsBasicMode(): Boolean = prefs.getBoolean("is_basic_mode", true)
    fun setIsBasicMode(isBasic: Boolean) = prefs.edit().putBoolean("is_basic_mode", isBasic).apply()
    fun getVoiceControlEnabled(): Boolean = prefs.getBoolean("voice_control_enabled", true)
    fun setVoiceControlEnabled(enabled: Boolean) = prefs.edit().putBoolean("voice_control_enabled", enabled).apply()

    /** True if user has unlocked the full version (multiple routines, edit, add, import, export). */
    fun getFullVersionUnlocked(): Boolean = prefs.getBoolean("full_version_unlocked", false)
    fun setFullVersionUnlocked(unlocked: Boolean) = prefs.edit().putBoolean("full_version_unlocked", unlocked).apply()

    fun getMuteAllSounds(): Boolean = prefs.getBoolean("mute_all_sounds", false)
    fun setMuteAllSounds(muted: Boolean) = prefs.edit().putBoolean("mute_all_sounds", muted).apply()

    /** Preferred media volume when app is in use (0–100). Default 50 on first run. */
    fun getAppMediaVolumePercent(): Int = prefs.getInt("app_media_volume_percent", 50).coerceIn(0, 100)
    fun setAppMediaVolumePercent(percent: Int) = prefs.edit().putInt("app_media_volume_percent", percent.coerceIn(0, 100)).apply()
    fun getBasicModeDuration(): Int = prefs.getInt("basic_mode_duration", 30).coerceIn(1, 9999)
    fun setBasicModeDuration(seconds: Int) = prefs.edit().putInt("basic_mode_duration", seconds).apply()

    fun getNotificationSoundUri(): Uri? {
        val uriString = prefs.getString("notification_sound_uri", null)
        return if (uriString != null) Uri.parse(uriString) else null
    }
    
    fun setNotificationSoundUri(uri: Uri?) {
        prefs.edit().putString("notification_sound_uri", uri?.toString()).apply()
    }
    
    fun getNotificationSoundName(): String {
        return prefs.getString("notification_sound_name", "Default") ?: "Default"
    }
    
    fun setNotificationSoundName(name: String) {
        prefs.edit().putString("notification_sound_name", name).apply()
    }
    
    fun getTtsVoiceName(): String? {
        return prefs.getString("tts_voice_name", null)
    }
    
    fun setTtsVoiceName(name: String?) {
        prefs.edit().putString("tts_voice_name", name).apply()
    }
    
    fun getTtsEnginePackage(): String {
        return prefs.getString("tts_engine_package", "auto") ?: "auto"
    }
    
    fun setTtsEnginePackage(enginePackage: String) {
        prefs.edit().putString("tts_engine_package", enginePackage).apply()
    }

    /** If true, show the instructions screen when the app opens. Set false when user checks "Don't show this again". */
    fun getShowInstructionsOnLaunch(): Boolean = prefs.getBoolean("show_instructions_on_launch", true)
    fun setShowInstructionsOnLaunch(show: Boolean) = prefs.edit().putBoolean("show_instructions_on_launch", show).apply()

    /** True if we have already shown the instructions screen this install. Stored in no-backup dir so it is not restored on reinstall. */
    fun hasShownInstructionsThisInstall(): Boolean = instructionsShownThisInstallFile.exists()

    /** Mark that we have shown the instructions this install (so we don't show again until next install). */
    fun markInstructionsShownThisInstall() {
        try {
            instructionsShownThisInstallFile.writeText("1")
        } catch (_: Exception) { /* ignore */ }
    }
}
