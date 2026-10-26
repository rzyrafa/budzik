package com.budzik.alarm.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.budzik.alarm.data.local.AlarmDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "BootReceiver received action: $action")

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            try {
                val db = AlarmDatabase.getDatabase(context)
                val ringingState = db.ringingStateDao().getRingingState()

                if (ringingState?.isRinging == true) {
                    Log.w(TAG, "REBOOT TRAP DETECTED! Device was rebooted while alarm ${ringingState.alarmId} was ringing. Resuming alarm immediately!")
                    val triggerIntent = Intent(context, AlarmReceiver::class.java).apply {
                        setAction(AlarmSchedulerImpl.ACTION_TRIGGER_ALARM)
                        putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, ringingState.alarmId)
                        putExtra(AlarmSchedulerImpl.EXTRA_DIFFICULTY, ringingState.difficulty)
                        putExtra(AlarmSchedulerImpl.EXTRA_PROBLEM_COUNT, ringingState.problemCount)
                    }
                    context.sendBroadcast(triggerIntent)
                }

                // Reschedule all enabled alarms
                val scheduler = AlarmSchedulerImpl(context)
                val now = System.currentTimeMillis()
                val trappedAlarmId = if (ringingState?.isRinging == true) ringingState.alarmId else null
                val enabledAlarms = db.alarmDao().getEnabledAlarms().mapNotNull { alarm ->
                    val missedLongAgo = alarm.daysOfWeekBitmask == 0 &&
                            alarm.nextTriggerAt in 1L until (now - AlarmSchedulerImpl.MISSED_ALARM_GRACE_MS)
                    if (missedLongAgo && alarm.id != trappedAlarmId) {
                        // Phone was off at alarm time: don't ring hours/days late right after boot
                        Log.w(TAG, "Disabling one-time alarm ${alarm.id} missed while device was off")
                        db.alarmDao().updateAlarm(alarm.copy(isEnabled = false))
                        return@mapNotNull null
                    }
                    if (alarm.id == trappedAlarmId && alarm.nextTriggerAt in 1L..now) {
                        // Already re-triggered by the reboot trap above; scheduling it would ring twice
                        return@mapNotNull null
                    }
                    val realigned = AlarmSchedulerImpl.realignTriggerToWallClock(alarm, now)
                    if (realigned != alarm.nextTriggerAt) {
                        alarm.copy(nextTriggerAt = realigned).also { db.alarmDao().updateAlarm(it) }
                    } else {
                        alarm
                    }
                }
                Log.d(TAG, "Rescheduling ${enabledAlarms.size} active alarms after reboot")
                scheduler.rescheduleAll(enabledAlarms)
            } catch (e: Exception) {
                Log.e(TAG, "Error in BootReceiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
