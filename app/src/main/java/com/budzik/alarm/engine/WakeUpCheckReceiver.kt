package com.budzik.alarm.engine

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.budzik.alarm.BudzikApplication
import com.budzik.alarm.R
import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveWakeUpCheck(
    val alarmId: Long,
    val triggerEpochMs: Long,
    val difficulty: String,
    val problemCount: Int
)

class WakeUpCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val alarmId = intent.getLongExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, -1L)
        val difficulty = intent.getStringExtra(AlarmSchedulerImpl.EXTRA_DIFFICULTY) ?: "MEDIUM"
        val problemCount = intent.getIntExtra(AlarmSchedulerImpl.EXTRA_PROBLEM_COUNT, 3)

        Log.d(TAG, "WakeUpCheckReceiver received action: $action, alarmId: $alarmId")

        when (action) {
            ACTION_TRIGGER_CHECK -> {
                handleTriggerCheck(context, alarmId, difficulty, problemCount)
            }
            ACTION_CONFIRM_AWAKE -> {
                handleConfirmAwake(context, alarmId)
            }
            ACTION_CHECK_TIMEOUT -> {
                handleCheckTimeout(context, alarmId, difficulty, problemCount)
            }
        }
    }

    private fun handleTriggerCheck(
        context: Context,
        alarmId: Long,
        difficulty: String,
        problemCount: Int
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        // Persist active check for interactive UI
        saveActiveCheck(context, alarmId, difficulty, problemCount)

        // PendingIntent for Confirmation via Notification Action Button
        val confirmIntent = Intent(context, WakeUpCheckReceiver::class.java).apply {
            action = ACTION_CONFIRM_AWAKE
            putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarmId)
        }
        val confirmPendingIntent = PendingIntent.getBroadcast(
            context,
            (alarmId.toInt() * 10) + 1,
            confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent to Open In-App Verification Modal in MainActivity
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_TRIGGER_CHECK
            putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarmId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            (alarmId.toInt() * 10) + 4,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, BudzikApplication.CHANNEL_ID_WAKE_UP_CHECK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sprawdź czy wstałem")
            .setContentText("Czy na pewno nie śpisz? Kliknij, aby potwierdzić.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .setFullScreenIntent(openAppPendingIntent, true)
            .addAction(
                R.drawable.ic_notification,
                "Potwierdzam, nie śpię",
                confirmPendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID_BASE + alarmId.toInt(), notification)

        try {
            context.startActivity(openAppIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot launch MainActivity directly: ${e.message}")
        }

        // Schedule 60-second fallback timeout
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val timeoutIntent = Intent(context, WakeUpCheckReceiver::class.java).apply {
            action = ACTION_CHECK_TIMEOUT
            putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmSchedulerImpl.EXTRA_DIFFICULTY, difficulty)
            putExtra(AlarmSchedulerImpl.EXTRA_PROBLEM_COUNT, problemCount)
        }
        val timeoutPendingIntent = PendingIntent.getBroadcast(
            context,
            (alarmId.toInt() * 10) + 2,
            timeoutIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeoutEpochMs = System.currentTimeMillis() + TIMEOUT_DURATION_MS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timeoutEpochMs,
                    timeoutPendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    timeoutEpochMs,
                    timeoutPendingIntent
                )
            }
            Log.d(TAG, "Scheduled 60s fallback timeout for alarm ID: $alarmId at $timeoutEpochMs")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule wake-up timeout alarm", e)
        }
    }

    private fun handleConfirmAwake(context: Context, alarmId: Long) {
        clearActiveCheck(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID_BASE + alarmId.toInt())

        // Cancel 60-second fallback timeout
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val timeoutIntent = Intent(context, WakeUpCheckReceiver::class.java).apply {
            action = ACTION_CHECK_TIMEOUT
            putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarmId)
        }
        val timeoutPendingIntent = PendingIntent.getBroadcast(
            context,
            (alarmId.toInt() * 10) + 2,
            timeoutIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (timeoutPendingIntent != null && alarmManager != null) {
            alarmManager.cancel(timeoutPendingIntent)
            timeoutPendingIntent.cancel()
        }

        Toast.makeText(context, "Potwierdzono pobudkę! Dobrego dnia.", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Wake-up check confirmed successfully for alarm ID: $alarmId")
    }

    private fun handleCheckTimeout(
        context: Context,
        alarmId: Long,
        difficulty: String,
        problemCount: Int
    ) {
        clearActiveCheck(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID_BASE + alarmId.toInt())

        Log.w(TAG, "Wake-up check TIMEOUT! User did not confirm in 60s. Re-triggering alarm ID: $alarmId at full volume!")

        AlarmLifecycle.startRinging(context, alarmId, difficulty, problemCount)
    }

    companion object {
        const val ACTION_TRIGGER_CHECK = "com.budzik.alarm.ACTION_TRIGGER_WAKE_UP_CHECK"
        const val ACTION_CONFIRM_AWAKE = "com.budzik.alarm.ACTION_CONFIRM_WAKE_UP"
        const val ACTION_CHECK_TIMEOUT = "com.budzik.alarm.ACTION_WAKE_UP_TIMEOUT"

        private const val NOTIFICATION_ID_BASE = 50000
        const val TIMEOUT_DURATION_MS = 60_000L // 60 seconds
        private const val TAG = "WakeUpCheckReceiver"

        private const val PREFS_NAME = "wake_up_check_prefs"
        private const val KEY_CHECK_ACTIVE = "key_check_active"
        private const val KEY_CHECK_ALARM_ID = "key_check_alarm_id"
        private const val KEY_CHECK_TRIGGER_EPOCH = "key_check_trigger_epoch"
        private const val KEY_CHECK_DIFFICULTY = "key_check_difficulty"
        private const val KEY_CHECK_PROBLEM_COUNT = "key_check_problem_count"

        private val _activeCheckFlow = MutableStateFlow<ActiveWakeUpCheck?>(null)
        val activeCheckFlow: StateFlow<ActiveWakeUpCheck?> = _activeCheckFlow.asStateFlow()

        fun getActiveWakeUpCheck(context: Context): ActiveWakeUpCheck? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_CHECK_ACTIVE, false)) {
                _activeCheckFlow.value = null
                return null
            }
            val alarmId = prefs.getLong(KEY_CHECK_ALARM_ID, -1L)
            val triggerEpochMs = prefs.getLong(KEY_CHECK_TRIGGER_EPOCH, 0L)
            val difficulty = prefs.getString(KEY_CHECK_DIFFICULTY, "MEDIUM") ?: "MEDIUM"
            val problemCount = prefs.getInt(KEY_CHECK_PROBLEM_COUNT, 3)
            val now = System.currentTimeMillis()
            if (alarmId == -1L || now - triggerEpochMs > TIMEOUT_DURATION_MS) {
                clearActiveCheck(context)
                _activeCheckFlow.value = null
                return null
            }
            val check = ActiveWakeUpCheck(alarmId, triggerEpochMs, difficulty, problemCount)
            _activeCheckFlow.value = check
            return check
        }

        fun saveActiveCheck(context: Context, alarmId: Long, difficulty: String, problemCount: Int) {
            val now = System.currentTimeMillis()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_CHECK_ACTIVE, true)
                .putLong(KEY_CHECK_ALARM_ID, alarmId)
                .putLong(KEY_CHECK_TRIGGER_EPOCH, now)
                .putString(KEY_CHECK_DIFFICULTY, difficulty)
                .putInt(KEY_CHECK_PROBLEM_COUNT, problemCount)
                .apply()
            _activeCheckFlow.value = ActiveWakeUpCheck(alarmId, now, difficulty, problemCount)
        }

        fun clearActiveCheck(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            _activeCheckFlow.value = null
        }

        fun confirmAwake(context: Context, alarmId: Long) {
            val receiver = WakeUpCheckReceiver()
            receiver.handleConfirmAwake(context, alarmId)
        }

        fun scheduleCheck(context: Context, alarm: AlarmEntity) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val delayMinutes = alarm.wakeUpCheckMinutes.coerceAtLeast(1)
            val triggerEpochMs = System.currentTimeMillis() + (delayMinutes * 60_000L)

            val checkIntent = Intent(context, WakeUpCheckReceiver::class.java).apply {
                action = ACTION_TRIGGER_CHECK
                putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarm.id)
                putExtra(AlarmSchedulerImpl.EXTRA_DIFFICULTY, alarm.difficulty)
                putExtra(AlarmSchedulerImpl.EXTRA_PROBLEM_COUNT, alarm.problemCount)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (alarm.id.toInt() * 10) + 3,
                checkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerEpochMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerEpochMs,
                        pendingIntent
                    )
                }
                Log.d(TAG, "Scheduled wake-up check for alarm ${alarm.id} in $delayMinutes min at $triggerEpochMs")
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to schedule wake-up check", e)
            }
        }

        fun scheduleTestCheck(context: Context, delayMs: Long = 5000L) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val triggerEpochMs = System.currentTimeMillis() + delayMs
            val testAlarmId = 99999L

            // Register with AlarmEngine for high-frequency in-engine backup
            AlarmEngine.pendingTestWakeCheckEpoch = triggerEpochMs

            val checkIntent = Intent(context, WakeUpCheckReceiver::class.java).apply {
                action = ACTION_TRIGGER_CHECK
                putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, testAlarmId)
                putExtra(AlarmSchedulerImpl.EXTRA_DIFFICULTY, "EASY")
                putExtra(AlarmSchedulerImpl.EXTRA_PROBLEM_COUNT, 1)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (testAlarmId.toInt() * 10) + 3,
                checkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerEpochMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerEpochMs,
                        pendingIntent
                    )
                }
                Log.d(TAG, "Scheduled test wake-up check in ${delayMs / 1000}s at $triggerEpochMs")
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to schedule test wake-up check", e)
            }
        }

        fun cancelCheck(context: Context, alarmId: Long) {
            clearActiveCheck(context)

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            notificationManager?.cancel(NOTIFICATION_ID_BASE + alarmId.toInt())

            if (alarmManager != null) {
                val triggerIntent = Intent(context, WakeUpCheckReceiver::class.java).apply {
                    action = ACTION_TRIGGER_CHECK
                }
                val triggerPending = PendingIntent.getBroadcast(
                    context,
                    (alarmId.toInt() * 10) + 3,
                    triggerIntent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (triggerPending != null) {
                    alarmManager.cancel(triggerPending)
                    triggerPending.cancel()
                }

                val timeoutIntent = Intent(context, WakeUpCheckReceiver::class.java).apply {
                    action = ACTION_CHECK_TIMEOUT
                }
                val timeoutPending = PendingIntent.getBroadcast(
                    context,
                    (alarmId.toInt() * 10) + 2,
                    timeoutIntent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (timeoutPending != null) {
                    alarmManager.cancel(timeoutPending)
                    timeoutPending.cancel()
                }
            }
        }
    }
}
