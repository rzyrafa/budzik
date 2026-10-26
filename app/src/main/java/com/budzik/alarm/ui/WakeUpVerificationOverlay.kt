package com.budzik.alarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.zIndex
import com.budzik.alarm.engine.ActiveWakeUpCheck
import com.budzik.alarm.engine.WakeUpCheckReceiver
import com.budzik.alarm.ui.theme.ContrastBlack
import com.budzik.alarm.ui.theme.ContrastWhite
import com.budzik.alarm.ui.theme.DarkPillBg
import com.budzik.alarm.ui.theme.PastelLime
import com.budzik.alarm.ui.theme.PastelPink
import com.budzik.alarm.ui.theme.PastelYellow
import com.budzik.alarm.ui.theme.bouncyPress
import kotlinx.coroutines.delay

// =========================================================================
// WAKE UP VERIFICATION OVERLAY ("SPRAWDŹ CZY WSTAŁEM")
// =========================================================================

@Composable
fun WakeUpVerificationOverlay(
    activeCheck: ActiveWakeUpCheck,
    onConfirm: () -> Unit
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activeCheck) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(500L)
        }
    }

    val elapsedMs = nowMs - activeCheck.triggerEpochMs
    val remainingSecs = remember(elapsedMs) {
        ((WakeUpCheckReceiver.TIMEOUT_DURATION_MS - elapsedMs + 999L) / 1000L).coerceIn(0L, 60L)
    }

    // Ciche pikanie ostrzegawcze podczas odliczania weryfikacji
    LaunchedEffect(remainingSecs) {
        if (remainingSecs in 1L..60L) {
            // Each ToneGenerator holds a native AudioTrack; without release() a few dozen beeps
            // exhaust the process audio track limit and the alarm sound itself can fail to start.
            var toneGen: android.media.ToneGenerator? = null
            try {
                toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 30)
                toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 100)
                delay(150L)
            } catch (e: Exception) {
                // Ignore tone generator errors on emulator or headless audio
            } finally {
                toneGen?.release()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .zIndex(300f)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .bouncyPress(0.98f),
            shape = RoundedCornerShape(36.dp),
            colors = CardDefaults.cardColors(containerColor = ContrastBlack),
            border = null,
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Circular icon tab
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(PastelYellow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Alarm,
                        contentDescription = null,
                        tint = ContrastBlack,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SPRAWDŹ CZY WSTAŁEM",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PastelYellow,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Czy na pewno nie śpisz?",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = ContrastWhite,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Alarm włączy się ponownie za ${remainingSecs}s",
                    fontSize = 14.sp,
                    color = Color(0xFFA0A0A5),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Big Countdown display
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(DarkPillBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${remainingSecs}s",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (remainingSecs <= 10) PastelPink else PastelYellow
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Big Pastel Button "Już nie śpię – potwierdzam!"
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .bouncyPress(),
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PastelLime,
                        contentColor = ContrastBlack
                    )
                ) {
                    Text(
                        text = "Już nie śpię – potwierdzam!",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
