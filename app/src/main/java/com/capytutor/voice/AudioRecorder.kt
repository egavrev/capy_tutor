package com.capytutor.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * Records the child's voice for Whisper (HANDOFF.md §4.4): mono, 16 kHz, AAC in an .m4a file in
 * the cache directory, at most 3 minutes. The caller sends the file and deletes it
 * (VoiceTranscriber always does); [clearLeftovers] removes files left by a killed app.
 */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    private val dir: File get() = File(context.cacheDir, "rec")

    fun clearLeftovers() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** False when the microphone could not start (busy, no audio input, no permission). */
    fun start(onMaxReached: () -> Unit): Boolean {
        if (recorder != null) return true
        clearLeftovers()
        dir.mkdirs()
        val f = File(dir, "r-${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(SAMPLE_RATE)
            r.setAudioEncodingBitRate(BIT_RATE)
            r.setMaxDuration(MAX_MS)
            r.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onMaxReached() }
            r.setOutputFile(f.path)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            f.delete()
            return false
        }
        recorder = r
        file = f
        startedAt = SystemClock.elapsedRealtime()
        return true
    }

    /** The recording and its length in seconds; null when nothing usable was recorded. */
    fun stop(): Pair<File, Int>? {
        val r = recorder ?: return null
        val f = file
        recorder = null
        file = null
        val seconds = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt().coerceAtLeast(1)
        val ok = try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            false // stop() right after start(): no audio yet
        } finally {
            r.release()
        }
        if (!ok || f == null || f.length() == 0L) {
            f?.delete()
            return null
        }
        return f to seconds
    }

    /** Stops and deletes, sending nothing. */
    fun cancel() {
        stop()?.first?.delete()
        clearLeftovers()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val BIT_RATE = 32_000
        const val MAX_MS = 3 * 60 * 1000
    }
}
