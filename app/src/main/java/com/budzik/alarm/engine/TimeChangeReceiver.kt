package com.budzik.alarm.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.budzik.alarm.data.local.AlarmDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "TimeChangeReceiver received: $action")

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            try {
                val db = AlarmDatabase.getDatabase(context)
                val scheduler = AlarmSchedulerImpl(context)
                val now = System.currentTimeMillis()
                val enabledAlarms = db.alarmDao().getEnabledAlarms().map { alarm ->
                    val realigned = AlarmSchedulerImpl.realignTriggerToWallClock(alarm, now)
                    if (realigned != alarm.nextTriggerAt) {
                        alarm.copy(nextTriggerAt = realigned).also { db.alarmDao().updateAlarm(it) }
                    } else {
                        alarm
                    }
                }
                Log.d(TAG, "Rescheduling ${enabledAlarms.size} alarms due to time/timezone change")
                scheduler.rescheduleAll(enabledAlarms)
            } catch (e: Exception) {
                Log.e(TAG, "Error in TimeChangeReceiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TimeChangeReceiver"
    }
}
