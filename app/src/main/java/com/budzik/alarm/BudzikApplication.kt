package com.budzik.alarm

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class BudzikApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java) ?: return

            // Alarm Ringing Channel. Must stay SILENT: AlarmRingingService plays the selected tone (MP3 or
            // synth) and vibrates itself. A channel sound would play the system alarm ringtone on top of it.
            // Channel settings are immutable once created, hence a new id + removal of the old noisy one.
            notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID_ALARM)
            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARM,
                getString(R.string.notification_channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_alarm_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
                enableLights(true)
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(alarmChannel)

            // Same alarm while its ringing screen is already open: no heads-up banner over the math task
            val ongoingChannel = NotificationChannel(
                CHANNEL_ID_ALARM_ONGOING,
                "Budzik dzwoni (ekran otwarty)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ciche powiadomienie, gdy ekran z zadaniem jest już na wierzchu"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(ongoingChannel)

            // Wake-Up Check ("Sprawdź czy wstałem") Channel
            val wakeUpChannel = NotificationChannel(
                CHANNEL_ID_WAKE_UP_CHECK,
                "Sprawdzanie czy wstałem",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Powiadomienia weryfikujące czy użytkownik nie zasnął z powrotem"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 400L, 200L, 400L)
                enableLights(true)
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(wakeUpChannel)
        }
    }

    companion object {
        const val CHANNEL_ID_ALARM = "budzik_alarm_channel_silent"
        const val CHANNEL_ID_ALARM_ONGOING = "budzik_alarm_ongoing"
        private const val LEGACY_CHANNEL_ID_ALARM = "budzik_alarm_channel"
        const val CHANNEL_ID_WAKE_UP_CHECK = "budzik_wake_up_check_channel"
    }
}
