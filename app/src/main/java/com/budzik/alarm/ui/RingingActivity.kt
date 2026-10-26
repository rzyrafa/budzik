package com.budzik.alarm.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.budzik.alarm.domain.generator.MathProblemGenerator
import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.engine.AlarmLifecycle
import com.budzik.alarm.engine.AlarmRingingService
import com.budzik.alarm.engine.AlarmSchedulerImpl
import com.budzik.alarm.ui.theme.BudzikTheme
import com.budzik.alarm.ui.theme.ContrastBlack
import com.budzik.alarm.ui.theme.ContrastWhite
import com.budzik.alarm.ui.theme.MainBgCream
import com.budzik.alarm.ui.theme.PastelLime
import com.budzik.alarm.ui.theme.PastelPink
import com.budzik.alarm.ui.theme.PastelYellow
import com.budzik.alarm.ui.theme.PillBgBeige
import com.budzik.alarm.ui.theme.TextSecondaryLight
import com.budzik.alarm.ui.theme.bouncyPress
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RingingActivity : ComponentActivity() {

    private lateinit var audioManager: AudioManager
    private var isQuietActive = false

    // All real alarms that rang while this screen was showing (e.g. next alarm of a series).
    // Solving the mission dismisses all of them, so none is left enabled with a past trigger time.
    private val ringingAlarmIds = linkedSetOf<Long>()

    override fun onResume() {
        super.onResume()
        AlarmRingingService.setRingingUiVisible(true)
    }

    override fun onPause() {
        // Finishing = alarm dismissed; the service is stopping, so don't flash the loud notification back
        if (!isFinishing) AlarmRingingService.setRingingUiVisible(false)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newAlarmId = intent.getLongExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, -1L)
        if (newAlarmId != -1L && !intent.getBooleanExtra(EXTRA_IS_PREVIEW, false)) {
            ringingAlarmIds.add(newAlarmId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockscreenFlags()
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        enforceVolume()

        val alarmId = intent.getLongExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, -1L)
        val difficultyStr = intent.getStringExtra(AlarmSchedulerImpl.EXTRA_DIFFICULTY) ?: "MEDIUM"
        val difficulty = try {
            Difficulty.valueOf(difficultyStr.uppercase())
        } catch (e: Exception) {
            Difficulty.MEDIUM
        }
        val rawProblemCount = intent.getIntExtra(AlarmSchedulerImpl.EXTRA_PROBLEM_COUNT, 3)
        if (alarmId != -1L && !intent.getBooleanExtra(EXTRA_IS_PREVIEW, false)) {
            ringingAlarmIds.add(alarmId)
        }

        val serviceIntent = Intent(this, AlarmRingingService::class.java).apply {
            action = AlarmRingingService.ACTION_START_RINGING
            putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmSchedulerImpl.EXTRA_DIFFICULTY, difficultyStr)
            putExtra(AlarmSchedulerImpl.EXTRA_PROBLEM_COUNT, rawProblemCount)
        }
        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)

        setContent {
            BudzikTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MainBgCream
                ) {
                    if (rawProblemCount <= 0) {
                        RingingSimpleScreen(
                            onDismiss = {
                                dismissAlarms()
                            }
                        )
                    } else {
                        RingingMathScreen(
                            difficulty = difficulty,
                            totalProblems = rawProblemCount,
                            onQuietModeChanged = { quiet ->
                                isQuietActive = quiet
                                val quietIntent = Intent(this@RingingActivity, AlarmRingingService::class.java).apply {
                                    action = AlarmRingingService.ACTION_SET_QUIET_MODE
                                    putExtra(AlarmRingingService.EXTRA_QUIET, quiet)
                                }
                                startService(quietIntent)
                            },
                            onMissionComplete = {
                                dismissAlarms()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun configureLockscreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            km?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun enforceVolume() {
        if (!isQuietActive) {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            enforceVolume() // Anti-cheat: prevent volume muting outside quiet mode
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Anti-cheat: Back button is disabled during alarm ringing
    }

    private fun dismissAlarms() {
        val appContext = applicationContext
        // Preview launched from "Testuj" has no real ids here, so the tested alarm stays untouched.
        val alarmIds = ringingAlarmIds.toList()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                AlarmLifecycle.dismiss(appContext, alarmIds)
            } catch (e: Exception) {
                android.util.Log.e("RingingActivity", "Error finishing dismissed alarms", e)
            }
        }

        val stopIntent = Intent(this, AlarmRingingService::class.java).apply {
            action = AlarmRingingService.ACTION_STOP_RINGING
        }
        startService(stopIntent)
        finishAndRemoveTask()
    }

    companion object {
        /** How many times "Wycisz na 20s" may be used during one ringing. */
        const val MAX_QUIET_USES = 3

        /** Set by "Testuj" so solving the preview does not disable or reschedule the real alarm. */
        const val EXTRA_IS_PREVIEW = "extra_is_preview"

        fun shouldScheduleWakeUpCheck(
            alarm: com.budzik.alarm.data.local.AlarmEntity,
            allSeriesAlarms: List<com.budzik.alarm.data.local.AlarmEntity>
        ): Boolean {
            if (!alarm.wakeUpCheckEnabled) return false
            if (alarm.seriesId.isNullOrBlank()) return true
            val laterAlarms = allSeriesAlarms.filter { it.isEnabled && it.seriesPosition > alarm.seriesPosition }
            return laterAlarms.isEmpty()
        }
    }
}

@Composable
fun RingingSimpleScreen(
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MainBgCream)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(PastelPink)
                    .bouncyPress(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Alarm,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = ContrastBlack
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "BUDZIK DZWONI",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ContrastBlack,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Czas wstawać! Miłego dnia.",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondaryLight
            )
        }

        // Center Pastel Card ("Stacked Pastel Cards" look)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyPress(0.98f),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = PastelYellow),
            border = null,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(ContrastBlack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Alarm,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = PastelYellow
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Dzień dobry!",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ContrastBlack
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Wybudzenie bez zadania matematycznego.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ContrastBlack.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Bottom: Large "Wyłącz alarm" Button
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .bouncyPress(),
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(
                containerColor = PastelLime,
                contentColor = ContrastBlack
            )
        ) {
            Text(
                text = "Wyłącz alarm",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RingingMathScreen(
    difficulty: Difficulty,
    totalProblems: Int,
    onQuietModeChanged: (Boolean) -> Unit,
    onMissionComplete: () -> Unit
) {
    val generator = remember { MathProblemGenerator.INSTANCE }
    val scope = rememberCoroutineScope()
    var currentProblemIndex by remember { mutableIntStateOf(1) }
    var currentProblem by remember { mutableStateOf(generator.generateProblem(difficulty)) }
    var userAnswerText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var shakeJob by remember { mutableStateOf<Job?>(null) }

    // Shake animation and warning color flash on wrong answer
    val shakeOffsetX = remember { Animatable(0f) }
    var isWarningFlash by remember { mutableStateOf(false) }
    val cardBgColor by animateColorAsState(
        targetValue = if (isWarningFlash) Color(0xFFFFB4AB) else PastelYellow,
        animationSpec = tween(durationMillis = 150),
        label = "math_card_color"
    )

    // Section 6: "Cichy czas na liczenie" + Timeout Bar (20 seconds)
    val maxTimeMs = 20_000L
    var timeLeftMs by remember { mutableLongStateOf(maxTimeMs) }
    var isQuietActive by remember { mutableStateOf(false) }
    // Anti-cheat: quiet time can be used only a few times per ringing, otherwise tapping the pill
    // every 19 s would keep the alarm quiet forever.
    var quietUsesLeft by remember { mutableIntStateOf(RingingActivity.MAX_QUIET_USES) }

    // Countdown coroutine ticker when quiet mode is active
    LaunchedEffect(isQuietActive) {
        if (isQuietActive) {
            while (isActive && isQuietActive && timeLeftMs > 0) {
                delay(100L)
                timeLeftMs = (timeLeftMs - 100L).coerceAtLeast(0L)
                if (timeLeftMs <= 0L) {
                    // Timeout expired! Alarm returns to 100% full volume and draws new math problem
                    isQuietActive = false
                    timeLeftMs = maxTimeMs
                    onQuietModeChanged(false)
                    currentProblem = generator.generateProblem(difficulty)
                    userAnswerText = ""
                    errorMessage = "Koniec cichego czasu! Alarm wraca na 100% głośności. Nowe zadanie!"
                }
            }
        }
    }

    fun activateQuietMode() {
        if (isQuietActive || quietUsesLeft <= 0) return
        quietUsesLeft--
        timeLeftMs = maxTimeMs
        isQuietActive = true
        errorMessage = null
        onQuietModeChanged(true)
    }

    fun submitAnswer() {
        val parsed = userAnswerText.toIntOrNull()
        if (parsed != null && generator.validateAnswer(currentProblem, parsed)) {
            errorMessage = null
            userAnswerText = ""
            if (currentProblemIndex >= totalProblems) {
                onMissionComplete()
            } else {
                currentProblemIndex++
                currentProblem = generator.generateProblem(difficulty)
                timeLeftMs = maxTimeMs // Give fresh 20s for next problem
            }
        } else {
            errorMessage = "Błędna odpowiedź. Spróbuj ponownie."
            userAnswerText = ""
            shakeJob?.cancel()
            shakeJob = scope.launch {
                isWarningFlash = true
                shakeOffsetX.snapTo(0f)
                shakeOffsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 400
                        0f at 0
                        (-20f) at 50
                        20f at 100
                        (-15f) at 150
                        15f at 200
                        (-8f) at 250
                        8f at 300
                        (-3f) at 350
                        0f at 400
                    }
                )
                delay(50L)
                isWarningFlash = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MainBgCream)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header: Alarm Icon + "BUDZIK DZWONI" + Mission progress
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(PastelPink)
                    .bouncyPress(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Alarm,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = ContrastBlack
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "BUDZIK DZWONI",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ContrastBlack,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Zadanie $currentProblemIndex z $totalProblems",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondaryLight
            )
        }

        // Section 6: Timeout Bar (Timer Bar) & "Wycisz na 20s (liczę)" pill
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Horizontal Rounded Timer Bar
            val progressFraction = (timeLeftMs.toFloat() / maxTimeMs).coerceIn(0f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = progressFraction,
                label = "timer_progress"
            )

            val barColor = if (isQuietActive) {
                if (animatedProgress > 0.4f) PastelLime else if (animatedProgress > 0.2f) PastelYellow else PastelPink
            } else {
                Color(0xFFDCDAD2)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color(0xFFE2E0D8))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(percent = 50))
                        .background(barColor)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Pill Button "Wycisz na 20s (liczę)" / Countdown
            Surface(
                onClick = { activateQuietMode() },
                enabled = isQuietActive || quietUsesLeft > 0,
                shape = RoundedCornerShape(percent = 50),
                color = when {
                    isQuietActive -> PastelLime
                    quietUsesLeft > 0 -> PillBgBeige
                    else -> PillBgBeige.copy(alpha = 0.5f)
                },
                border = null,
                modifier = Modifier.bouncyPress()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isQuietActive) Icons.Outlined.Timer else Icons.AutoMirrored.Outlined.VolumeDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ContrastBlack
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isQuietActive -> "Cichy czas: ${((timeLeftMs + 999) / 1000)}s"
                            quietUsesLeft > 0 -> "Wycisz na 20s (liczę) · zostało $quietUsesLeft"
                            else -> "Limit wyciszeń wykorzystany"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ContrastBlack
                    )
                }
            }
        }

        // Pastel Math Problem Card (żółty #F9E784 z czarnym tekstem np. "8 + 4")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = shakeOffsetX.value }
                .bouncyPress(0.98f),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val mathFontSize = when (difficulty) {
                    Difficulty.HARD -> 38.sp
                    Difficulty.MEDIUM -> 46.sp
                    Difficulty.EASY -> 52.sp
                }

                Text(
                    text = "${currentProblem.operandA} + ${currentProblem.operandB}",
                    fontSize = mathFontSize,
                    fontWeight = FontWeight.ExtraBold,
                    color = ContrastBlack,
                    letterSpacing = (-1).sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(60.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(ContrastWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (userAnswerText.isEmpty()) "Wpisz wynik" else userAnswerText,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (userAnswerText.isEmpty()) Color(0xFFA0A0A5) else ContrastBlack
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFBA1A1A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Keypad with comfortable, taller buttons without borders (1-9, C, 0, OK)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "OK")
            )

            for (row in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (btn in row) {
                        val btnBg = when (btn) {
                            "OK" -> PastelPink
                            "C" -> Color(0xFFDCDAD2)
                            else -> PillBgBeige
                        }

                        Button(
                            onClick = {
                                when (btn) {
                                    "C" -> {
                                        if (userAnswerText.isNotEmpty()) {
                                            userAnswerText = userAnswerText.dropLast(1)
                                        }
                                    }
                                    "OK" -> submitAnswer()
                                    else -> {
                                        // Typing digits triggers quiet mode or extends the timer bar
                                        if (!isQuietActive) {
                                            activateQuietMode()
                                        } else {
                                            // Each digit click gently extends timer by 2.0s
                                            timeLeftMs = (timeLeftMs + 2000L).coerceAtMost(maxTimeMs)
                                        }
                                        if (userAnswerText.length < 7) {
                                            userAnswerText += btn
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(68.dp)
                                .bouncyPress(),
                            shape = RoundedCornerShape(24.dp),
                            border = null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = btnBg,
                                contentColor = ContrastBlack
                            )
                        ) {
                            Text(
                                text = btn,
                                fontSize = if (btn == "OK") 20.sp else 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = ContrastBlack
                            )
                        }
                    }
                }
            }
        }
    }
}
