package com.budzik.alarm.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budzik.alarm.engine.AlarmEngine
import com.budzik.alarm.engine.AlarmSchedulerImpl
import com.budzik.alarm.sound.AlarmSoundManager
import com.budzik.alarm.ui.theme.BudzikTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start bulletproof central AlarmEngine
        AlarmEngine.start(this)

        // Remove thin dark lines from right and top edges of screen by disabling contrast enforcement
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        // Force dark icons for status bar and navigation bar on light cream (#F5F4EF) background
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setContent {
            BudzikTheme(darkTheme = false) {
                MainAlarmScreen(
                    viewModel = viewModel(),
                    onTestAlarm = { alarm ->
                        val intent = Intent(this, RingingActivity::class.java).apply {
                            putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarm.id)
                            putExtra(AlarmSchedulerImpl.EXTRA_DIFFICULTY, alarm.difficulty)
                            putExtra(AlarmSchedulerImpl.EXTRA_PROBLEM_COUNT, alarm.problemCount)
                            putExtra(RingingActivity.EXTRA_IS_PREVIEW, true)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Fast-tick evaluate alarms immediately upon focus/resume and unlock audio
        AlarmEngine.checkNow(this)
        AlarmEngine.unlockAudio(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            AlarmEngine.checkNow(this)
            AlarmEngine.unlockAudio(this)
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        AlarmEngine.unlockAudio(this)
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AlarmEngine.checkNow(this)
    }

    override fun onPause() {
        super.onPause()
        // Stop any active sound preview if user leaves app or receives a call
        AlarmSoundManager.stopPreview()
    }
}
