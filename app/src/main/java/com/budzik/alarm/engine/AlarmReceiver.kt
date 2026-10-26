package com.budzik.alarm.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.budzik.alarm.data.local.AlarmDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, -1L)
        val difficulty = intent.getStringExtra(AlarmSchedulerImpl.EXTRA_DIFFICULTY) ?: "MEDIUM"
        val problemCount = intent.getIntExtra(AlarmSchedulerImpl.EXTRA_PROBLEM_COUNT, 3)

        Log.d(TAG, "Alarm triggered! ID: $alarmId, difficulty: $difficulty, problemCount: $problemCount")

        // Acquire WakeLock to prevent CPU sleeping between broadcast and service
        AlarmEngine.acquireWakeLock(context, 45_000L)
        AlarmLifecycle.startRinging(context, alarmId, difficulty, problemCount)

        // Same database bookkeeping as the in-app ticker, so the alarm cannot fire a second time
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = AlarmDatabase.getDatabase(context).alarmDao().getAlarmById(alarmId)
                if (alarm != null) AlarmLifecycle.completeRing(context, alarm)
            } catch (e: Exception) {
                Log.e(TAG, "Failed updating alarm $alarmId after trigger", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}
