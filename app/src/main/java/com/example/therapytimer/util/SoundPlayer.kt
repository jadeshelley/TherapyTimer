package com.example.therapytimer.util

import android.content.Context
import android.media.AudioManager
import com.example.therapytimer.R
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.Random

class SoundPlayer(private val context: Context, private val preferencesManager: PreferencesManager) {
    private var mediaPlayer: MediaPlayer? = null
    private val random = Random()

    /** Short beep to confirm a voice command was accepted. Uses alarm stream at 50% volume. */
    fun playConfirmationBeep() {
        if (preferencesManager.getMuteAllSounds()) return
        try {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, 50)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            Handler(Looper.getMainLooper()).postDelayed({
                tg.release()
            }, 200)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playNotificationSound(onComplete: (() -> Unit)? = null) {
        if (preferencesManager.getMuteAllSounds()) {
            onComplete?.invoke()
            return
        }
        try {
            val notificationUri: Uri = preferencesManager.getNotificationSoundUri()
                ?: Uri.parse("android.resource://${context.packageName}/${R.raw.end_bell}")

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, notificationUri)
            if (mediaPlayer == null) {
                onComplete?.invoke()
                return
            }
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener {
                it.release()
                mediaPlayer = null
                onComplete?.invoke()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete?.invoke()
        }
    }

    /**
     * Plays the Revoicer count file for 1..20 from assets/numbers/{n}count.mp3.
     * If count is outside 1..20, calls onComplete immediately.
     * @param onHalfway invoked at roughly half the clip duration (e.g. for resuming voice listening).
     */
    fun playCountNumber(
        count: Int,
        onHalfway: (() -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        if (preferencesManager.getMuteAllSounds()) {
            onComplete?.invoke()
            return
        }
        if (count !in 1..20) {
            onComplete?.invoke()
            return
        }
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer()
            context.assets.openFd("numbers/${count}count.mp3").use { afd ->
                mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mediaPlayer?.prepare()
            val durationMs = mediaPlayer?.duration?.takeIf { it > 0 } ?: 0
            mediaPlayer?.start()
            if (durationMs > 0 && onHalfway != null) {
                Handler(Looper.getMainLooper()).postDelayed({
                    onHalfway.invoke()
                }, (durationMs / 2).toLong())
            }
            mediaPlayer?.setOnCompletionListener {
                it.release()
                mediaPlayer = null
                onComplete?.invoke()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mediaPlayer = null
            onComplete?.invoke()
        }
    }

    /**
     * Routine complete: plays Finished5.mp3 first, then a random other finished clip (Finished1, Finished2, etc.).
     */
    fun playRandomFinished(onComplete: (() -> Unit)? = null) {
        if (preferencesManager.getMuteAllSounds()) {
            onComplete?.invoke()
            return
        }
        val allFiles = context.assets.list("finished")?.filter { it.endsWith(".mp3", ignoreCase = true) }?.takeIf { it.isNotEmpty() }
            ?: listOf("Finished1.mp3", "Finished2.mp3", "Finished5.mp3")
        val others = allFiles.filter { it.equals("Finished5.mp3", ignoreCase = true).not() }
        playFinished5Then {
            if (others.isNotEmpty()) {
                val nextFile = "finished/${others[random.nextInt(others.size)]}"
                playOneFinished(nextFile, onComplete)
            } else {
                onComplete?.invoke()
            }
        }
    }

    private fun playFinished5Then(onComplete: () -> Unit) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer()
            context.assets.openFd("finished/Finished5.mp3").use { afd ->
                mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mediaPlayer?.prepare()
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener {
                it.release()
                mediaPlayer = null
                onComplete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mediaPlayer = null
            onComplete()
        }
    }

    private fun playOneFinished(fileName: String, onComplete: (() -> Unit)?) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer()
            context.assets.openFd(fileName).use { afd ->
                mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mediaPlayer?.prepare()
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener {
                it.release()
                mediaPlayer = null
                onComplete?.invoke()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mediaPlayer = null
            onComplete?.invoke()
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
