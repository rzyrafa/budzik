package com.budzik.alarm.sound

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

data class SoundTone(
    val id: String,
    val name: String,
    val description: String
)

data class CustomRingtone(
    val id: String,
    val name: String,
    val filePath: String
)

object AlarmSoundManager {

    private const val TAG = "AlarmSoundManager"
    private const val PREFS_NAME = "budzik_sound_preferences"
    private const val KEY_TONE_ID = "pref_tone_id"
    private const val KEY_VOLUME = "pref_volume"
    private const val KEY_GENTLE_RAMP = "pref_gentle_ramp"
    private const val KEY_VIBRATE = "pref_vibrate"
    private const val KEY_CUSTOM_RINGTONES = "pref_custom_ringtones"

    const val TONE_MORNING_PULSE = "morning_pulse"
    const val TONE_DIGITAL_ZEN = "digital_zen"
    const val TONE_CLASSIC_BELL = "classic_bell"
    const val TONE_SIREN = "siren"
    const val TONE_SOFT_CHORD = "soft_chord"

    val AVAILABLE_TONES = listOf(
        SoundTone(TONE_MORNING_PULSE, "Poranny Puls", "Ciepły, rytmiczny puls na łagodne wybudzenie"),
        SoundTone(TONE_DIGITAL_ZEN, "Cyfrowy Zen", "Harmoniczne dzwony i rezonans 528 Hz"),
        SoundTone(TONE_CLASSIC_BELL, "Klasyczny Budzik", "Energiczne, naprzemienne dzwonienie staccato"),
        SoundTone(TONE_SIREN, "Syrena Alarmowa", "Intensywny narastający sygnał dla twardo śpiących"),
        SoundTone(TONE_SOFT_CHORD, "Miękki Akord", "Spokojny, przestrzenny akord durowy")
    )

    private const val SAMPLE_RATE = 44100
    private var previewAudioTrack: AudioTrack? = null
    private var ringingAudioTrack: AudioTrack? = null
    private var silentKeepAliveTrack: AudioTrack? = null
    private var previewMediaPlayer: MediaPlayer? = null
    private var ringingMediaPlayer: MediaPlayer? = null
    private var preloadedMediaPlayer: MediaPlayer? = null
    private var gentleRampJob: Job? = null
    private var lastContext: Context? = null
    private val soundScope = CoroutineScope(Dispatchers.IO + Job())
    private var _isAudioUnlocked = false
    private var _isSilentKeepAliveActive = false

    fun isAudioUnlocked(): Boolean = _isAudioUnlocked
    fun isSilentKeepAliveActive(): Boolean = _isSilentKeepAliveActive

    fun unlockAudio(context: Context) {
        if (_isAudioUnlocked) return
        try {
            val silentPcm = ShortArray(64)
            val track = createAudioTrack(silentPcm, isAlarmUsage = false)
            track.play()
            track.stop()
            track.release()
            _isAudioUnlocked = true
            Log.d(TAG, "Audio subsystem unlocked")
        } catch (e: Exception) {
            Log.w(TAG, "Audio unlock: ${e.message}")
            _isAudioUnlocked = true
        }
    }

    /**
     * Silent Audio Keep-Alive: Maintains a low-overhead silent audio buffer looping
     * at ultra-low gain (0.0001) while alarms are armed to prevent Android power managers
     * and CPU governor from freezing timers or background coroutines.
     */
    @Synchronized
    fun startSilentKeepAlive() {
        if (_isSilentKeepAliveActive) return
        try {
            val bufferSize = 1024
            val silentPcm = ShortArray(bufferSize)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(silentPcm, 0, bufferSize)
            track.setLoopPoints(0, bufferSize, -1)
            track.setVolume(0.0001f)
            track.play()
            silentKeepAliveTrack = track
            _isSilentKeepAliveActive = true
            Log.d(TAG, "Silent Audio Keep-Alive started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Silent Audio Keep-Alive", e)
        }
    }

    @Synchronized
    fun stopSilentKeepAlive() {
        try {
            silentKeepAliveTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Silent Audio Keep-Alive", e)
        } finally {
            silentKeepAliveTrack = null
            _isSilentKeepAliveActive = false
            Log.d(TAG, "Silent Audio Keep-Alive stopped")
        }
    }

    /**
     * Pre-loads the selected custom MP3 into memory so it is immediately ready
     * to play the moment the alarm triggers.
     */
    @Synchronized
    fun preloadSelectedTone(context: Context) {
        try {
            preloadedMediaPlayer?.release()
            preloadedMediaPlayer = null

            val toneId = getSelectedToneId(context)
            val customRingtone = getCustomRingtones(context).firstOrNull { it.id == toneId }
            if (customRingtone != null && File(customRingtone.filePath).exists()) {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(customRingtone.filePath)
                    isLooping = true
                    prepare()
                }
                preloadedMediaPlayer = player
                Log.d(TAG, "Pre-loaded custom MP3 tone: ${customRingtone.name}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed pre-loading custom MP3", e)
        }
    }

    fun requestAudioFocus(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus", e)
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSelectedToneId(context: Context): String {
        return getPrefs(context).getString(KEY_TONE_ID, TONE_MORNING_PULSE) ?: TONE_MORNING_PULSE
    }

    fun setSelectedToneId(context: Context, toneId: String) {
        getPrefs(context).edit().putString(KEY_TONE_ID, toneId).apply()
        preloadSelectedTone(context)
    }

    fun getVolume(context: Context): Float {
        return getPrefs(context).getFloat(KEY_VOLUME, 0.85f)
    }

    fun setVolume(context: Context, volume: Float) {
        getPrefs(context).edit().putFloat(KEY_VOLUME, volume.coerceIn(0.1f, 1.0f)).apply()
    }

    fun isGentleRampEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_GENTLE_RAMP, true)
    }

    fun setGentleRampEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_GENTLE_RAMP, enabled).apply()
    }

    fun isVibrateEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VIBRATE, true)
    }

    fun setVibrateEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VIBRATE, enabled).apply()
    }

    fun getCustomRingtones(context: Context): List<CustomRingtone> {
        val jsonStr = getPrefs(context).getString(KEY_CUSTOM_RINGTONES, null) ?: return emptyList()
        val list = mutableListOf<CustomRingtone>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val path = obj.getString("filePath")
                val file = File(path)
                if (file.exists()) {
                    list.add(CustomRingtone(id, name, path))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing custom ringtones", e)
        }
        return list
    }

    private fun saveCustomRingtones(context: Context, list: List<CustomRingtone>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("filePath", item.filePath)
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_CUSTOM_RINGTONES, array.toString()).apply()
    }

    fun addCustomRingtone(context: Context, uri: Uri): CustomRingtone? {
        try {
            var fileName = "Własny dźwięk.mp3"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val nameFromCursor = cursor.getString(nameIndex)
                    if (!nameFromCursor.isNullOrBlank()) {
                        fileName = nameFromCursor
                    }
                }
            }

            val customDir = File(context.filesDir, "custom_ringtones")
            if (!customDir.exists()) {
                customDir.mkdirs()
            }

            val id = "custom_${System.currentTimeMillis()}"
            val sanitized = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(customDir, "${id}_$sanitized")

            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (!targetFile.exists() || targetFile.length() == 0L) {
                Log.e(TAG, "Target custom audio file empty or not created")
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                return null
            }

            val ringtone = CustomRingtone(id = id, name = fileName, filePath = targetFile.absolutePath)
            val currentList = getCustomRingtones(context).toMutableList()
            currentList.add(0, ringtone) // Most recently uploaded at top
            saveCustomRingtones(context, currentList)
            return ringtone
        } catch (e: Exception) {
            Log.e(TAG, "Error importing custom ringtone", e)
            return null
        }
    }

    fun deleteCustomRingtone(context: Context, id: String): Boolean {
        val currentList = getCustomRingtones(context).toMutableList()
        val toDelete = currentList.firstOrNull { it.id == id } ?: return false
        try {
            val file = File(toDelete.filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting custom ringtone file", e)
        }
        currentList.removeAll { it.id == id }
        saveCustomRingtones(context, currentList)

        if (getSelectedToneId(context) == id) {
            setSelectedToneId(context, TONE_MORNING_PULSE)
        }
        return true
    }

    /**
     * Synthesizes 16-bit PCM mono audio buffers algorithmically for each tone.
     */
    fun generatePcmData(toneId: String): ShortArray {
        return when (toneId) {
            TONE_DIGITAL_ZEN -> generateDigitalZenPcm()
            TONE_CLASSIC_BELL -> generateClassicBellPcm()
            TONE_SIREN -> generateSirenPcm()
            TONE_SOFT_CHORD -> generateSoftChordPcm()
            else -> generateMorningPulsePcm()
        }
    }

    // 1. Poranny Puls: 2.0s loop, warm pulsating 440 Hz + 880 Hz with 2.5Hz Hann envelope
    private fun generateMorningPulsePcm(): ShortArray {
        val durationSec = 2.0
        val numSamples = (durationSec * SAMPLE_RATE).toInt()
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            // 2.5 Hz pulse envelope
            val env = 0.5 * (1.0 - cos(2.0 * PI * 2.5 * t))
            val sig = 0.7 * sin(2.0 * PI * 440.0 * t) + 0.3 * sin(2.0 * PI * 880.0 * t)
            val sample = sig * env
            pcm[i] = (sample * 30000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    // 2. Cyfrowy Zen: 3.0s loop, 528 Hz Solfeggio with gentle 1056 Hz overtone and exponential decay
    private fun generateDigitalZenPcm(): ShortArray {
        val durationSec = 3.0
        val numSamples = (durationSec * SAMPLE_RATE).toInt()
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val decay = exp(-t * 1.05)
            val sig = 0.75 * sin(2.0 * PI * 528.0 * t) +
                    0.20 * sin(2.0 * PI * 1056.0 * t) +
                    0.05 * sin(2.0 * PI * 1584.0 * t)
            val sample = sig * decay
            pcm[i] = (sample * 30000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    // 3. Klasyczny Budzik: 1.0s loop, alternating 800 Hz and 1000 Hz rapid staccato twin bell
    private fun generateClassicBellPcm(): ShortArray {
        val durationSec = 1.0
        val numSamples = (durationSec * SAMPLE_RATE).toInt()
        val pcm = ShortArray(numSamples)
        val strikeRate = 12.0 // 12 strikes per second
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val strikeIndex = (t * strikeRate).toInt()
            val strikeFraction = (t * strikeRate) - strikeIndex
            val freq = if (strikeIndex % 2 == 0) 820.0 else 1020.0
            val strikeEnv = if (strikeFraction < 0.8) exp(-strikeFraction * 7.0) else 0.0
            val sig = (sin(2.0 * PI * freq * t) + 0.3 * sin(2.0 * PI * (freq * 2.1) * t)) * strikeEnv
            pcm[i] = (sig * 30000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    // 4. Syrena Alarmowa: 1.5s loop, rising & falling frequency sweep 650Hz to 1350Hz
    private fun generateSirenPcm(): ShortArray {
        val durationSec = 1.5
        val numSamples = (durationSec * SAMPLE_RATE).toInt()
        val pcm = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 1000.0 + 350.0 * sin(2.0 * PI * (1.0 / durationSec) * t)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sample = 0.85 * sin(phase) + 0.15 * sin(phase * 2.0)
            pcm[i] = (sample * 29000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    // 5. Miękki Akord: 3.0s loop, lush major 7th chord (C4, E4, G4, B4)
    private fun generateSoftChordPcm(): ShortArray {
        val durationSec = 3.0
        val numSamples = (durationSec * SAMPLE_RATE).toInt()
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val swell = 0.5 * (1.0 - cos(2.0 * PI * (1.0 / durationSec) * t))
            val chord = 0.35 * sin(2.0 * PI * 261.63 * t) + // C4
                    0.28 * sin(2.0 * PI * 329.63 * t) + // E4
                    0.22 * sin(2.0 * PI * 392.00 * t) + // G4
                    0.15 * sin(2.0 * PI * 493.88 * t)   // B4
            val sample = chord * swell
            pcm[i] = (sample * 30000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    private fun createAudioTrack(pcmData: ShortArray, isAlarmUsage: Boolean): AudioTrack {
        val usage = if (isAlarmUsage) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(pcmData.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcmData, 0, pcmData.size)
        track.setLoopPoints(0, pcmData.size, -1) // Loop continuously
        return track
    }

    /**
     * Preview tone playback in UI bottom sheet.
     */
    @Synchronized
    fun playPreview(toneId: String, volume: Float, context: Context? = null) {
        stopPreview()
        val customRingtone = context?.let { ctx ->
            getCustomRingtones(ctx).firstOrNull { it.id == toneId }
        }

        if (customRingtone != null && File(customRingtone.filePath).exists()) {
            try {
                val vol = volume.coerceIn(0.05f, 1.0f)
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(customRingtone.filePath)
                    isLooping = true
                    setVolume(vol, vol)
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "Preview MediaPlayer error: what=$what, extra=$extra")
                        stopPreview()
                        true
                    }
                    prepare()
                    start()
                }
                previewMediaPlayer = player
            } catch (e: Exception) {
                Log.e(TAG, "Error playing preview MediaPlayer", e)
            }
        } else {
            try {
                val pcm = generatePcmData(toneId)
                previewAudioTrack = createAudioTrack(pcm, isAlarmUsage = false).apply {
                    setVolume(volume.coerceIn(0.05f, 1.0f))
                    play()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing preview audio track", e)
            }
        }
    }

    @Synchronized
    fun setPreviewVolume(volume: Float) {
        val vol = volume.coerceIn(0.05f, 1.0f)
        try {
            previewAudioTrack?.setVolume(vol)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting preview volume on AudioTrack", e)
        }
        try {
            previewMediaPlayer?.setVolume(vol, vol)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting preview volume on MediaPlayer", e)
        }
    }

    @Synchronized
    fun stopPreview() {
        try {
            previewAudioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping preview AudioTrack", e)
        } finally {
            previewAudioTrack = null
        }

        try {
            previewMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping preview MediaPlayer", e)
        } finally {
            previewMediaPlayer = null
        }
    }

    /**
     * Alarm ringing playback used by AlarmRingingService.
     */
    @Synchronized
    fun startRinging(context: Context) {
        stopRinging()
        lastContext = context.applicationContext
        val toneId = getSelectedToneId(context)
        val targetVolume = getVolume(context)
        val gentleRamp = isGentleRampEnabled(context)

        val customRingtone = getCustomRingtones(context).firstOrNull { it.id == toneId }
        if (customRingtone != null && File(customRingtone.filePath).exists()) {
            try {
                val initialVol = if (gentleRamp) 0.05f else targetVolume
                val player = preloadedMediaPlayer ?: MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(customRingtone.filePath)
                    isLooping = true
                    prepare()
                }
                preloadedMediaPlayer = null
                player.setVolume(initialVol, initialVol)
                // Strict mutual exclusion: the synth fallback starts ONLY on a real playback error,
                // and stopRinging() releases the MP3 player first so two sources never play together.
                // (No timer-based fallback: a "not playing yet" check used to cut in on a valid MP3.)
                player.setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Ringing MediaPlayer error: what=$what, extra=$extra. Falling back to synthesized audio.")
                    synchronized(AlarmSoundManager) {
                        stopRinging()
                        startSynthesizedRinging(TONE_SIREN, targetVolume, false)
                    }
                    true
                }
                ringingMediaPlayer = player
                player.start()

                if (gentleRamp && targetVolume > initialVol) {
                    gentleRampJob?.cancel()
                    gentleRampJob = soundScope.launch {
                        val steps = 30
                        val durationMs = 15000L // 15 seconds ramp
                        val stepDelay = durationMs / steps
                        val volIncrement = (targetVolume - initialVol) / steps

                        for (step in 1..steps) {
                            if (!isActive) break
                            delay(stepDelay)
                            val newVol = (initialVol + volIncrement * step).coerceIn(0.05f, targetVolume)
                            synchronized(AlarmSoundManager) {
                                try {
                                    ringingMediaPlayer?.setVolume(newVol, newVol)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error updating MediaPlayer ramp volume", e)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting custom ringtone in MediaPlayer, falling back to synthesized siren", e)
                stopRinging()
                startSynthesizedRinging(TONE_SIREN, targetVolume, false)
            }
        } else {
            startSynthesizedRinging(toneId, targetVolume, gentleRamp)
        }
    }

    private fun startSynthesizedRinging(toneId: String, targetVolume: Float, gentleRamp: Boolean) {
        try {
            val pcm = generatePcmData(toneId)
            val initialVol = if (gentleRamp) 0.05f else targetVolume
            val track = createAudioTrack(pcm, isAlarmUsage = true).apply {
                setVolume(initialVol)
                play()
            }
            ringingAudioTrack = track

            if (gentleRamp) {
                gentleRampJob?.cancel()
                gentleRampJob = soundScope.launch {
                    val steps = 30
                    val durationMs = 15000L // 15 seconds ramp
                    val stepDelay = durationMs / steps
                    val volIncrement = (targetVolume - initialVol) / steps

                    for (step in 1..steps) {
                        if (!isActive) break
                        delay(stepDelay)
                        val newVol = (initialVol + volIncrement * step).coerceIn(0.05f, targetVolume)
                        synchronized(AlarmSoundManager) {
                            ringingAudioTrack?.setVolume(newVol)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting synthesized alarm ringing for $toneId, fallback to siren", e)
            if (toneId != TONE_SIREN) {
                try {
                    val sirenPcm = generatePcmData(TONE_SIREN)
                    val track = createAudioTrack(sirenPcm, isAlarmUsage = true).apply {
                        setVolume(targetVolume)
                        play()
                    }
                    ringingAudioTrack = track
                } catch (e2: Exception) {
                    Log.e(TAG, "Emergency siren fallback failed", e2)
                }
            }
        }
    }

    @Synchronized
    fun setRingingQuietMode(isQuiet: Boolean, context: Context) {
        if (isQuiet) {
            // Cancel any active gentle ramp so it doesn't overwrite quiet volume!
            gentleRampJob?.cancel()
            gentleRampJob = null
        }
        val targetVolume = getVolume(context)
        val vol = if (isQuiet) {
            // Przyciszenie o 85% (czyli 15% głośności docelowej)
            (targetVolume * 0.15f).coerceAtLeast(0.05f)
        } else {
            // Powrót do 100% pełnej głośności
            targetVolume
        }
        try {
            ringingAudioTrack?.setVolume(vol)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting quiet mode volume on AudioTrack", e)
        }
        try {
            ringingMediaPlayer?.setVolume(vol, vol)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting quiet mode volume on MediaPlayer", e)
        }
    }

    @Synchronized
    fun stopRinging() {
        gentleRampJob?.cancel()
        gentleRampJob = null
        try {
            ringingAudioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringing audio track", e)
        } finally {
            ringingAudioTrack = null
        }

        try {
            ringingMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringing MediaPlayer", e)
        } finally {
            ringingMediaPlayer = null
        }

        // The preloaded MP3 player was consumed by this ring; prepare a fresh one for the next alarm
        val ctx = lastContext
        if (preloadedMediaPlayer == null && ctx != null) {
            soundScope.launch { preloadSelectedTone(ctx) }
        }
    }
}
