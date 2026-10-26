package com.budzik.alarm.engine

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.budzik.alarm.BudzikApplication
import com.budzik.alarm.R
import com.budzik.alarm.data.local.AlarmDatabase
import com.budzik.alarm.data.local.RingingStateEntity
import com.budzik.alarm.sound.AlarmSoundManager
import com.budzik.alarm.ui.RingingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmRingingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var volumeEnforcerJob: Job? = null
    private var isQuietModeActive: Boolean = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    // What is ringing right now; null until the first ACTION_START_RINGING
    private var ringingAlarmId: Long? = null
    private var ringingDifficulty: String = "MEDIUM"
    private var ringingProblemCount: Int = 3

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        acquireWakeLock()
        initVibrator()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_RINGING) {
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_SET_QUIET_MODE) {
            val isQuiet = intent.getBooleanExtra(EXTRA_QUIET, false)
            isQuietModeActive = isQuiet
            AlarmSoundManager.setRingingQuietMode(isQuiet, applicationContext)
            return START_STICKY
        }

        val alarmId = intent?.getLongExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, -1L) ?: -1L
        val difficulty = intent?.getStringExtra(AlarmSchedulerImpl.EXTRA_DIFFICULTY) ?: "MEDIUM"
        val problemCount = intent?.getIntExtra(AlarmSchedulerImpl.EXTRA_PROBLEM_COUNT, 3) ?: 3

        // AlarmReceiver, the in-app ticker and RingingActivity may all start the service for one alarm
        // (or the next alarm of a series fires while this one rings). Restarting would replay the MP3 from
        // the beginning, so while ringing just satisfy startForegroundService() with the notification.
        if (ringingAlarmId != null) {
            Log.d(TAG, "Already ringing (alarm $ringingAlarmId), ignoring restart for alarm $alarmId")
            startForegroundWithNotification()
            return START_STICKY
        }
        ringingAlarmId = alarmId
        ringingDifficulty = difficulty
        ringingProblemCount = problemCount
        Log.d(TAG, "Starting alarm ringing service for alarm ID: $alarmId")

        // Persist ringing state for Anti-Cheat / Reboot Trap
        serviceScope.launch {
            val db = AlarmDatabase.getDatabase(applicationContext)
            db.ringingStateDao().setRingingState(
                RingingStateEntity(
                    id = 1,
                    alarmId = alarmId,
                    isRinging = true,
                    difficulty = difficulty,
                    problemCount = problemCount,
                    solvedCount = 0
                )
            )
        }

        startForegroundWithNotification()
        startAudioPlayback()
        startVolumeLock()
        if (AlarmSoundManager.isVibrateEnabled(applicationContext)) {
            startVibration()
        }

        return START_STICKY
    }

    /**
     * While the ringing screen is on top, the notification moves to a quiet channel without a full-screen
     * intent, so Android doesn't drop a heads-up banner over the math task. When the user leaves the
     * screen, the loud version (heads-up + full-screen intent) comes back.
     */
    private fun startForegroundWithNotification() {
        val alarmId = ringingAlarmId ?: -1L
        val difficulty = ringingDifficulty
        val problemCount = ringingProblemCount
        val quiet = isRingingUiVisible
        val fullScreenIntent = Intent(this, RingingActivity::class.java).apply {
            putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmSchedulerImpl.EXTRA_DIFFICULTY, difficulty)
            putExtra(AlarmSchedulerImpl.EXTRA_PROBLEM_COUNT, problemCount)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (quiet) BudzikApplication.CHANNEL_ID_ALARM_ONGOING else BudzikApplication.CHANNEL_ID_ALARM
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Budzik dzwoni!")
            .setContentText(
                if (problemCount > 0) "Rozwiąż zadanie matematyczne, aby wyłączyć budzik"
                else "Dotknij, aby wyłączyć budzik"
            )
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
        if (!quiet) builder.setFullScreenIntent(fullScreenPendingIntent, true)
        val notification = builder.build()

        // Loud and quiet versions use different ids: an update of the same id keeps an already shown
        // heads-up banner on screen, while moving the foreground notification to another id lets us
        // cancel the old one (and its banner) for real.
        val id = if (quiet) NOTIFICATION_ID_QUIET else NOTIFICATION_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(id, notification)
        }
        getSystemService(NotificationManager::class.java)
            ?.cancel(if (quiet) NOTIFICATION_ID else NOTIFICATION_ID_QUIET)
    }

    private fun startAudioPlayback() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            // Request AudioFocus and start tone playback via AlarmSoundManager
            AlarmSoundManager.requestAudioFocus(applicationContext)
            AlarmSoundManager.startRinging(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alarm audio", e)
        }
    }

    private fun startVolumeLock() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

        volumeEnforcerJob?.cancel()
        volumeEnforcerJob = serviceScope.launch {
            while (isActive) {
                // If quiet mode is not active, enforce maximum volume against user silencing
                if (!isQuietModeActive) {
                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    if (currentVol < maxVolume) {
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
                    }
                }
                delay(400)
            }
        }
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 500, 300, 500, 300)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Budzik:RingingWakeLock"
        )?.apply {
            acquire(10 * 60 * 1000L) // 10 minutes max
        }
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun stopAlarm() {
        ringingAlarmId = null
        volumeEnforcerJob?.cancel()
        try {
            AlarmSoundManager.stopRinging()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping sound manager ringing", e)
        }
        vibrator?.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        serviceScope.launch {
            val db = AlarmDatabase.getDatabase(applicationContext)
            db.ringingStateDao().clearRingingState()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopAlarm()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_RINGING = "com.budzik.alarm.ACTION_START_RINGING"
        const val ACTION_STOP_RINGING = "com.budzik.alarm.ACTION_STOP_RINGING"
        const val ACTION_SET_QUIET_MODE = "com.budzik.alarm.ACTION_SET_QUIET_MODE"
        const val EXTRA_QUIET = "extra_quiet"
        private const val NOTIFICATION_ID = 9999
        private const val NOTIFICATION_ID_QUIET = 9998
        private const val TAG = "AlarmRingingService"

        @Volatile
        private var instance: AlarmRingingService? = null

        @Volatile
        private var isRingingUiVisible = false

        /** Called by RingingActivity on resume/pause; swaps the ringing notification between loud and quiet. */
        fun setRingingUiVisible(visible: Boolean) {
            isRingingUiVisible = visible
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                if (service.ringingAlarmId != null) service.startForegroundWithNotification()
            }
        }
    }
}
