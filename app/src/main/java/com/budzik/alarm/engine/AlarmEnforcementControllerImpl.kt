package com.budzik.alarm.engine

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.budzik.alarm.data.local.AlarmDatabase
import kotlinx.coroutines.runBlocking

class AlarmEnforcementControllerImpl(
    private val context: Context
) : AlarmEnforcementController {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    override fun startRinging(alarmId: Long) {
        val intent = Intent(context, AlarmRingingService::class.java).apply {
            action = AlarmRingingService.ACTION_START_RINGING
            putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarmId)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    override fun stopRinging() {
        val intent = Intent(context, AlarmRingingService::class.java).apply {
            action = AlarmRingingService.ACTION_STOP_RINGING
        }
        context.stopService(intent)
    }

    override fun enforceMaxVolume() {
        audioManager?.let { am ->
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
        }
    }

    override fun isRebootTrapActive(): Boolean {
        return runBlocking {
            val db = AlarmDatabase.getDatabase(context)
            val state = db.ringingStateDao().getRingingState()
            state?.isRinging == true
        }
    }
}
