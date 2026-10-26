package com.budzik.alarm.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.data.local.isNap
import com.budzik.alarm.ui.MainActivity
import com.budzik.alarm.ui.RingingActivity
import java.util.Calendar

class AlarmSchedulerImpl(
    private val context: Context
) : AlarmScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun schedule(alarm: AlarmEntity) {
        if (!alarm.isEnabled || alarmManager == null) return

        val triggerMs = alarm.getEffectiveTriggerEpochMs()

        // 1. Primary PendingIntent for AlarmReceiver broadcast (reliably fires AlarmRingingService + RingingActivity)
        val receiverIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_ALARM
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_DIFFICULTY, alarm.difficulty)
            putExtra(EXTRA_PROBLEM_COUNT, alarm.problemCount)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. ShowIntent for system lockscreen clock widget click -> opens MainActivity
        val showIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerMs, showIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Scheduled alarm ID: ${alarm.id} for epoch $triggerMs (${alarm.hour}:${alarm.minute}) via AlarmReceiver")
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot schedule exact alarm via setAlarmClock, falling back to setExactAndAllowWhileIdle", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerMs,
                        pendingIntent
                    )
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Failed fallback exact alarm scheduling", e2)
            }
        }
    }

    override fun cancel(alarmId: Long) {
        if (alarmManager == null) return

        // Cancel broadcast PendingIntent
        val receiverIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_ALARM
        }
        val broadcastPending = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            receiverIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (broadcastPending != null) {
            alarmManager.cancel(broadcastPending)
            broadcastPending.cancel()
        }

        // Cancel any legacy activity PendingIntent
        val ringIntent = Intent(context, RingingActivity::class.java)
        val activityPending = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            ringIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (activityPending != null) {
            alarmManager.cancel(activityPending)
            activityPending.cancel()
        }

        Log.d(TAG, "Cancelled alarm ID: $alarmId")
    }

    override fun cancelAll(alarmIds: List<Long>) {
        alarmIds.forEach { cancel(it) }
    }

    override fun rescheduleAll(alarms: List<AlarmEntity>) {
        alarms.forEach { if (it.isEnabled) schedule(it) }
    }

    companion object {
        const val ACTION_TRIGGER_ALARM = "com.budzik.alarm.ACTION_TRIGGER_ALARM"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_DIFFICULTY = "extra_difficulty"
        const val EXTRA_PROBLEM_COUNT = "extra_problem_count"
        private const val TAG = "AlarmScheduler"

        /** One-time alarms missed by more than this (e.g. phone was off) are disabled on boot instead of ringing late. */
        const val MISSED_ALARM_GRACE_MS = 15 * 60_000L

        /**
         * Returns the trigger epoch that matches the alarm's wall-clock [AlarmEntity.hour]:[AlarmEntity.minute]
         * in the current time zone. Needed after TIME_SET / TIMEZONE_CHANGED, because [AlarmEntity.nextTriggerAt]
         * is an absolute epoch and would otherwise ring at the old local time.
         * Naps are relative timers and keep their epoch; missed one-time alarms are left as-is.
         */
        fun realignTriggerToWallClock(alarm: AlarmEntity, nowMs: Long = System.currentTimeMillis()): Long {
            val current = alarm.nextTriggerAt
            if (current <= 0L || alarm.isNap) return current
            if (current <= nowMs && alarm.daysOfWeekBitmask == 0) return current
            if (current > nowMs) {
                val cal = Calendar.getInstance().apply { timeInMillis = current }
                if (cal.get(Calendar.HOUR_OF_DAY) == alarm.hour && cal.get(Calendar.MINUTE) == alarm.minute) {
                    return current
                }
            }
            return calculateNextTriggerEpochMs(alarm.hour, alarm.minute, alarm.daysOfWeekBitmask, nowMs)
        }

        fun calculateNextTriggerEpochMs(hour: Int, minute: Int, nowMs: Long = System.currentTimeMillis()): Long {
            return calculateNextTriggerEpochMs(hour, minute, 0, nowMs)
        }

        fun calculateNextTriggerEpochMs(
            hour: Int,
            minute: Int,
            daysOfWeekBitmask: Int,
            nowMs: Long = System.currentTimeMillis()
        ): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (daysOfWeekBitmask == 0) {
                if (cal.timeInMillis <= nowMs) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                return cal.timeInMillis
            }

            // Days of week bitmask: bit 0 = Mon, bit 1 = Tue, ..., bit 6 = Sun
            // Calendar: SUNDAY=1, MONDAY=2, ..., SATURDAY=7
            for (dayOffset in 0..7) {
                if (dayOffset > 0) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val bitIndex = (dayOfWeek + 5) % 7 // Mon=0, Tue=1, ..., Sun=6
                val isDayActive = (daysOfWeekBitmask and (1 shl bitIndex)) != 0
                if (isDayActive && cal.timeInMillis > nowMs) {
                    return cal.timeInMillis
                }
            }
            if (cal.timeInMillis <= nowMs) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }
    }
}
