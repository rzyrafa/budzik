package com.budzik.alarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.engine.AlarmSchedulerImpl
import com.budzik.alarm.ui.theme.ContrastBlack
import com.budzik.alarm.ui.theme.ContrastWhite
import com.budzik.alarm.ui.theme.DarkCardBg
import com.budzik.alarm.ui.theme.PastelPink
import com.budzik.alarm.ui.theme.PastelYellow
import com.budzik.alarm.ui.theme.TextSubtle
import com.budzik.alarm.ui.theme.bouncyPress
import kotlinx.coroutines.delay

// =========================================================================
// QUICK NAP BOTTOM SHEET ("SZYBKA DRZEMKA")
// =========================================================================

@Composable
fun QuickNapBottomSheet(
    activeNapAlarm: AlarmEntity?,
    onStartNap: (minutes: Int, requireMathProblem: Boolean) -> Unit,
    onCancelNap: (AlarmEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var requireMathProblem by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(activeNapAlarm) {
        if (activeNapAlarm != null) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    BottomSheetWrapper(
        title = "SZYBKA DRZEMKA",
        tabColor = PastelYellow,
        closeButtonColor = PastelPink,
        onDismiss = onDismiss
    ) {
        if (activeNapAlarm != null) {
            val targetEpoch = remember(activeNapAlarm) {
                if (activeNapAlarm.nextTriggerAt > 0L) {
                    activeNapAlarm.nextTriggerAt
                } else {
                    AlarmSchedulerImpl.calculateNextTriggerEpochMs(activeNapAlarm.hour, activeNapAlarm.minute)
                }
            }
            val diffMs = targetEpoch - nowMs
            val (mins, secs, isRingingNow) = if (diffMs <= 0) {
                Triple(0L, 0L, true)
            } else {
                val totalSecs = (diffMs + 999L) / 1000L
                Triple(totalSecs / 60, totalSecs % 60, false)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isRingingNow) "Drzemka dzwoni teraz!" else "Dzwoni za",
                        fontSize = 13.sp,
                        color = TextSubtle
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isRingingNow) "00:00" else String.format("%02d:%02d", mins, secs),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                        color = PastelYellow,
                        letterSpacing = (-1).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Godzina budzenia: ${String.format("%02d:%02d", activeNapAlarm.hour, activeNapAlarm.minute)}",
                        fontSize = 13.sp,
                        color = ContrastWhite.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onCancelNap(activeNapAlarm) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .bouncyPress(),
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E1A1D),
                            contentColor = Color(0xFFFF7B7B)
                        )
                    ) {
                        Text(
                            text = "Anuluj drzemkę",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Siatka szybkich czasów drzemki ("+5 min", "+10 min", "+15 min", "+20 min", "+30 min", "+45 min", "+60 min", "+90 min")
        Text(
            text = if (activeNapAlarm != null) "Zmień czas drzemki" else "Wybierz czas drzemki",
            fontSize = 12.sp,
            color = TextSubtle,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))

        val napTimes = listOf(5, 10, 15, 20, 30, 45, 60, 90)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            napTimes.chunked(4).forEach { rowChunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowChunk.forEach { m ->
                        PillOptionChip(
                            text = "+$m min",
                            isSelected = false,
                            accentColor = PastelYellow,
                            modifier = Modifier.weight(1f),
                            onClick = { onStartNap(m, requireMathProblem) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Przełącznik "Wymagaj zadania przy wybudzeniu"
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wymagaj zadania przy wybudzeniu",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ContrastWhite
                    )
                    Text(
                        text = "Rozwiązanie zadania matematycznego, by wyłączyć drzemkę",
                        fontSize = 11.sp,
                        color = Color(0xFFA0A0A5)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = requireMathProblem,
                    onCheckedChange = { requireMathProblem = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ContrastBlack,
                        checkedTrackColor = PastelYellow,
                        uncheckedThumbColor = Color(0xFF888888),
                        uncheckedTrackColor = Color(0xFF28282C),
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }
        }

    }
}
