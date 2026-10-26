package com.budzik.alarm.engine

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.budzik.alarm.data.local.AlarmDatabase
import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.data.local.isNap
import com.budzik.alarm.data.local.isRepeating
import com.budzik.alarm.data.local.isTest
import com.budzik.alarm.ui.RingingActivity

/**
 * Single place for what happens when an alarm rings and when it is dismissed.
 * Used by [AlarmReceiver], [AlarmEngine], [WakeUpCheckReceiver] and [RingingActivity],
 * so every trigger path leaves the database in the same state.
 */
object AlarmLifecycle {

    private const val TAG = "AlarmLifecycle"

    /** Starts the foreground ringing service and tries to bring up the full-screen ringing UI. */
    fun startRinging(context: Context, alarmId: Long, difficulty: String, problemCount: Int) {
        val appContext = context.applicationContext
        val serviceIntent = Intent(appContext, AlarmRingingService::class.java).apply {
            action = AlarmRingingService.ACTION_START_RINGING
            putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmSchedulerImpl.EXTRA_DIFFICULTY, difficulty)
            putExtra(AlarmSchedulerImpl.EXTRA_PROBLEM_COUNT, problemCount)
        }
        try {
            ContextCompat.startForegroundService(appContext, serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting AlarmRingingService", e)
        }

        try {
            val activityIntent = Intent(appContext, RingingActivity::class.java).apply {
                putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmSchedulerImpl.EXTRA_DIFFICULTY, difficulty)
                putExtra(AlarmSchedulerImpl.EXTRA_PROBLEM_COUNT, problemCount)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            appContext.startActivity(activityIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Direct activity launch restricted; fullScreenIntent notification will present UI", e)
        }
    }

    /**
     * Moves an alarm past its ring: naps and diagnostic tests are deleted, one-time alarms are disabled,
     * repeating alarms are advanced to their next day and rescheduled. Safe to call more than once.
     */
    suspend fun completeRing(context: Context, alarm: AlarmEntity) {
        val appContext = context.applicationContext
        val dao = AlarmDatabase.getDatabase(appContext).alarmDao()
        when {
            alarm.isNap || alarm.isTest -> dao.deleteAlarm(alarm.id)
            !alarm.isRepeating -> if (alarm.isEnabled) dao.updateAlarm(alarm.copy(isEnabled = false))
            else -> {
                val nextEpoch = AlarmSchedulerImpl.calculateNextTriggerEpochMs(
                    alarm.hour,
                    alarm.minute,
                    alarm.daysOfWeekBitmask,
                    System.currentTimeMillis() + 60_000L
                )
                val updated = alarm.copy(nextTriggerAt = nextEpoch)
                dao.updateAlarm(updated)
                AlarmSchedulerImpl(appContext).schedule(updated)
            }
        }
    }

    /** User solved the mission: finish every alarm that rang and plan "Sprawdź czy wstałem" if needed. */
    suspend fun dismiss(context: Context, alarmIds: Collection<Long>) {
        val appContext = context.applicationContext
        val dao = AlarmDatabase.getDatabase(appContext).alarmDao()
        for (alarmId in alarmIds) {
            val alarm = dao.getAlarmById(alarmId) ?: continue
            completeRing(appContext, alarm)

            if (alarm.wakeUpCheckEnabled) {
                val seriesAlarms = alarm.seriesId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { dao.getAlarmsBySeries(it) }
                    .orEmpty()
                if (RingingActivity.shouldScheduleWakeUpCheck(alarm, seriesAlarms)) {
                    WakeUpCheckReceiver.scheduleCheck(appContext, alarm)
                }
            }
        }
    }
}
