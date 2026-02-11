package com.example.therapytimer.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stores simple logs of what speech recognition heard, grouped into chunks.
 *
 * Each chunk is everything heard between "triggers" (start/next/restart/done/reset, etc.).
 * Chunks are written as JSON lines (one JSON object per line) to an internal file.
 */
class VoiceLogManager(context: Context) {

    private val logFile: File = File(context.filesDir, "voice_logs.jsonl")

    private var currentSessionId: Long = System.currentTimeMillis()
    private var currentChunkTexts: MutableList<String> = mutableListOf()
    private var currentChunkStartMs: Long = System.currentTimeMillis()

    @Synchronized
    fun startNewSession() {
        // Finish any open chunk (if there is text) before starting a new session.
        finalizeChunkInternal(trigger = "session_switch", routineId = null, isBasicMode = false)
        currentSessionId = System.currentTimeMillis()
        currentChunkTexts.clear()
        currentChunkStartMs = System.currentTimeMillis()
    }

    /**
     * Append recognized text into the current chunk.
     * This is typically called when recognition returns a non-empty text (partial or final).
     */
    @Synchronized
    fun addRecognizedText(text: String) {
        if (text.isBlank()) return
        currentChunkTexts.add(text)
    }

    /**
     * Mark a trigger boundary (e.g. voice "start", "next", "done", "reset", etc.).
     * Everything heard since the previous trigger is saved as one chunk.
     */
    @Synchronized
    fun onTrigger(trigger: String, routineId: String?, isBasicMode: Boolean) {
        finalizeChunkInternal(trigger, routineId, isBasicMode)
    }

    /** Deletes all stored voice logs. */
    @Synchronized
    fun clearLog() {
        currentChunkTexts.clear()
        if (logFile.exists()) logFile.delete()
    }

    /**
     * Read all stored chunks from disk.
     * Returns newest chunks last (file is append-only).
     */
    @Synchronized
    fun readAllChunks(): List<VoiceLogChunk> {
        if (!logFile.exists()) return emptyList()
        val result = mutableListOf<VoiceLogChunk>()
        logFile.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEachLine
            try {
                val obj = JSONObject(trimmed)
                val textsJson = obj.optJSONArray("texts") ?: JSONArray()
                val texts = mutableListOf<String>()
                for (i in 0 until textsJson.length()) {
                    texts.add(textsJson.optString(i, ""))
                }
                result.add(
                    VoiceLogChunk(
                        sessionId = obj.optLong("sessionId", 0L),
                        routineId = obj.optString("routineId").takeIf { it.isNotEmpty() },
                        isBasicMode = obj.optBoolean("isBasicMode", false),
                        trigger = obj.optString("trigger", ""),
                        texts = texts,
                        startedAt = obj.optLong("startedAt", 0L),
                        endedAt = obj.optLong("endedAt", 0L)
                    )
                )
            } catch (_: Exception) {
                // Ignore malformed lines
            }
        }
        return result
    }

    @Synchronized
    private fun finalizeChunkInternal(trigger: String, routineId: String?, isBasicMode: Boolean) {
        if (currentChunkTexts.isEmpty()) {
            // Nothing heard since last trigger; nothing to persist.
            currentChunkStartMs = System.currentTimeMillis()
            return
        }
        val now = System.currentTimeMillis()
        val json = JSONObject().apply {
            put("sessionId", currentSessionId)
            put("startedAt", currentChunkStartMs)
            put("endedAt", now)
            put("trigger", trigger)
            if (routineId != null) {
                put("routineId", routineId)
            } else {
                put("routineId", JSONObject.NULL)
            }
            put("isBasicMode", isBasicMode)
            put("texts", JSONArray().apply {
                currentChunkTexts.forEach { put(it) }
            })
        }
        try {
            logFile.appendText(json.toString() + "\n")
        } catch (_: Exception) {
            // Ignore I/O failures; logging is best-effort.
        }
        currentChunkTexts.clear()
        currentChunkStartMs = now
    }
}

/**
 * A single logged chunk: everything the recognizer heard between two triggers.
 */
data class VoiceLogChunk(
    val sessionId: Long,
    val routineId: String?,
    val isBasicMode: Boolean,
    val trigger: String,
    val texts: List<String>,
    val startedAt: Long,
    val endedAt: Long
)

