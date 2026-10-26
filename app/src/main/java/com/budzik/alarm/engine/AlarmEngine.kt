package com.budzik.alarm.engine

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.budzik.alarm.data.local.AlarmDatabase
import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.sound.AlarmSoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Central Alarm Engine for bulletproof countdown, timing, and alarm triggering.
 *
 * Solves:
 * 1. Stale Closures: Keeps all active alarms in AtomicReference synchronized with Room DAO.
 * 2. Missed Minutes / Epoch checking: Compares System.currentTimeMillis() >= alarm.getEffectiveTriggerEpochMs()
 *    every 500ms (2 times per second) and on every lifecycle resume/focus/wake event.
 * 3. Series sub-alarms: Every sub-alarm (#1, #2, #3...) has its own nextTriggerAt timestamp and is evaluated.
 * 4. Quick Nap: Instant epoch target timestamp evaluation.
 * 5. WakeLock & Audio readiness: Manages partial wake locks and audio unlock keep-alive.
 */
object AlarmEngine {

    private const val TAG = "AlarmEngine"
    private const val TICK_INTERVAL_MS = 500L

    private val engineScope = CoroutineScope(Dispatchers.IO + Job())
    private var tickerJob: Job? = null
    private var alarmsCollectorJob: Job? = null

    private val cachedAlarms = AtomicReference<List<AlarmEntity>>(emptyList())
    private var lastTriggeredAlarmId: Long = -1L
    private var lastTriggerTimestamp: Long = 0L

    private var appWakeLock: PowerManager.WakeLock? = null

    // Live observable states for UI diagnostic panel
    private val _isEngineActive = MutableStateFlow(true)
    val isEngineActive: StateFlow<Boolean> = _isEngineActive.asStateFlow()

    private val _isAudioUnlocked = MutableStateFlow(false)
    val isAudioUnlocked: StateFlow<Boolean> = _isAudioUnlocked.asStateFlow()

    private val _isWakeLockActive = MutableStateFlow(false)
    val isWakeLockActive: StateFlow<Boolean> = _isWakeLockActive.asStateFlow()

    // Test wake check epoch for in-engine 5s verification
    var pendingTestWakeCheckEpoch: Long = 0L

    fun start(context: Context) {
        val appContext = context.applicationContext
        _isEngineActive.value = true

        // Preload selected tone for instant playback
        AlarmSoundManager.preloadSelectedTone(appContext)

        // 1. Observe alarms from database and keep fresh copy in AtomicReference (No Stale Closures!)
        alarmsCollectorJob?.cancel()
        alarmsCollectorJob = engineScope.launch {
            val db = AlarmDatabase.getDatabase(appContext)
            db.alarmDao().getAllAlarms().collect { alarms ->
                cachedAlarms.set(alarms)
                Log.d(TAG, "Cached ${alarms.size} alarms in AlarmEngine (fresh snapshot)")
                // No 24h WakeLock / silent audio loop here: they kept the CPU awake around the clock
                // while any alarm was enabled. AlarmManager.setAlarmClock() wakes the device on time;
                // this ticker is only a backup while the app is in the foreground.
            }
        }

        // 2. High frequency 500ms heartbeat loop
        tickerJob?.cancel()
        tickerJob = engineScope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                evaluateAlarms(appContext)
            }
        }

        Log.d(TAG, "AlarmEngine started successfully with 500ms ticker")
    }

    fun stop() {
        tickerJob?.cancel()
        tickerJob = null
        alarmsCollectorJob?.cancel()
        alarmsCollectorJob = null
        AlarmSoundManager.stopSilentKeepAlive()
        releaseWakeLock()
        _isEngineActive.value = false
        Log.d(TAG, "AlarmEngine stopped")
    }

    /**
     * Immediate evaluation called on Activity onResume/onStart/focus.
     * Guarantees zero-latency trigger when device wakes up or app comes to foreground.
     */
    fun checkNow(context: Context) {
        engineScope.launch {
            evaluateAlarms(context.applicationContext)
        }
    }

    private suspend fun evaluateAlarms(context: Context) {
        val now = System.currentTimeMillis()
        val alarms = cachedAlarms.get() ?: return
        val db = AlarmDatabase.getDatabase(context)

        // Refresh live status indicators for diagnostic panel
        _isAudioUnlocked.value = AlarmSoundManager.isAudioUnlocked()
        _isWakeLockActive.value = appWakeLock?.isHeld == true

        // Check if an alarm is already ringing
        val ringingState = db.ringingStateDao().getRingingState()
        val isCurrentlyRinging = ringingState?.isRinging == true

        // 1. Check all enabled alarms (single alarms, series sub-alarms, quick nap alarms)
        for (alarm in alarms) {
            if (!alarm.isEnabled) continue

            val triggerEpochMs = alarm.getEffectiveTriggerEpochMs(now)

            // Trigger condition: enabled, not currently ringing same alarm, and current time >= trigger time
            if (now >= triggerEpochMs) {
                // Prevent duplicate rapid re-triggering of the same alarm within 10 seconds
                if (alarm.id == lastTriggeredAlarmId && (now - lastTriggerTimestamp) < 10_000L) {
                    continue
                }
                if (isCurrentlyRinging && ringingState?.alarmId == alarm.id) {
                    continue
                }

                Log.d(TAG, "TRIGGERING ALARM ID: ${alarm.id} (${alarm.label}) at $now (target: $triggerEpochMs)")
                lastTriggeredAlarmId = alarm.id
                lastTriggerTimestamp = now

                triggerAlarm(context, alarm)
                break // Handle one alarm at a time
            }
        }

        // 2. Check pending test wake check if scheduled
        if (pendingTestWakeCheckEpoch in 1..now) {
            pendingTestWakeCheckEpoch = 0L
            Log.d(TAG, "Triggering pending test wake-up check at $now")
            val checkIntent = Intent(context, WakeUpCheckReceiver::class.java).apply {
                action = WakeUpCheckReceiver.ACTION_TRIGGER_CHECK
                putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, 99999L)
                putExtra(AlarmSchedulerImpl.EXTRA_DIFFICULTY, "EASY")
                putExtra(AlarmSchedulerImpl.EXTRA_PROBLEM_COUNT, 1)
            }
            context.sendBroadcast(checkIntent)
        }
    }

    /**
     * Executes alarm trigger from the in-app ticker: WakeLock, ringing service + UI,
     * then the same database bookkeeping as [AlarmReceiver] (see [AlarmLifecycle.completeRing]).
     */
    fun triggerAlarm(context: Context, alarm: AlarmEntity) {
        val appContext = context.applicationContext
        acquireWakeLock(appContext, 60_000L)
        AlarmLifecycle.startRinging(appContext, alarm.id, alarm.difficulty, alarm.problemCount)
        engineScope.launch {
            AlarmLifecycle.completeRing(appContext, alarm)
        }
    }

    /**
     * Acquires CPU wake lock to prevent sleep during critical timing/handoff.
     */
    fun acquireWakeLock(context: Context, durationMs: Long = 30_000L) {
        try {
            if (appWakeLock == null) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                appWakeLock = pm?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "Budzik:AlarmEngineWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            appWakeLock?.acquire(durationMs)
            _isWakeLockActive.value = true
            Log.d(TAG, "WakeLock acquired for ${durationMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock", e)
        }
    }

    fun releaseWakeLock() {
        try {
            appWakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            _isWakeLockActive.value = false
            Log.d(TAG, "WakeLock released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
    }

    /**
     * Unlocks audio subsystem upon any user gesture / touch.
     */
    fun unlockAudio(context: Context) {
        AlarmSoundManager.unlockAudio(context)
        _isAudioUnlocked.value = true
    }
}
