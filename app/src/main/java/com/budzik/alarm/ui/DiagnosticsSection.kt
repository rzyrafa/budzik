package com.budzik.alarm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budzik.alarm.engine.AlarmEngine
import com.budzik.alarm.engine.WakeUpCheckReceiver
import com.budzik.alarm.ui.theme.ContrastWhite
import com.budzik.alarm.ui.theme.DarkCardBg
import com.budzik.alarm.ui.theme.DarkPillBg
import com.budzik.alarm.ui.theme.PastelBlue
import com.budzik.alarm.ui.theme.PastelLime
import com.budzik.alarm.ui.theme.PastelPink
import com.budzik.alarm.ui.theme.PastelYellow
import com.budzik.alarm.ui.theme.TextSubtle
import com.budzik.alarm.ui.theme.bouncyPress

// =========================================================================
// DIAGNOSTIC & INSTANT TEST SECTION (CREAMY PASTEL & CONTRAST BLACK)
// =========================================================================

@Composable
fun AlarmDiagnosticSection(
    viewModel: AlarmViewModel,
    showToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isEngineActive by AlarmEngine.isEngineActive.collectAsState()
    val isAudioUnlocked by AlarmEngine.isAudioUnlocked.collectAsState()
    val isWakeLockActive by AlarmEngine.isWakeLockActive.collectAsState()

    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "diag_chevron")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Collapsible header: diagnostics are rarely needed, so the card starts folded
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Diagnostyka i szybkie testy",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ContrastWhite
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Zwiń" else "Rozwiń",
                    tint = PastelBlue,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Live Status Indicators
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkPillBg, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DiagnosticStatusRow(
                            label = "Silnik zegara",
                            statusText = if (isEngineActive) "Aktywny" else "Nieaktywny",
                            isActive = isEngineActive
                        )
                        DiagnosticStatusRow(
                            label = "Audio",
                            statusText = if (isAudioUnlocked) "Odblokowane" else "Zablokowane",
                            isActive = isAudioUnlocked
                        )
                        DiagnosticStatusRow(
                            label = "Blokada uśpienia (WakeLock)",
                            statusText = if (isWakeLockActive) "Aktywna" else "Nieaktywna",
                            isActive = isWakeLockActive
                        )
                    }

                    Text(
                        text = "Szybkie testy na żywo",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSubtle
                    )

                    // Test 1: Single Alarm (in 5s)
                    DiagnosticTestButton(
                        text = "Test pojedynczego alarmu (za 5 sek.)",
                        accentColor = PastelLime,
                        onClick = {
                            AlarmEngine.unlockAudio(context)
                            viewModel.createTestSingleAlarm()
                            showToast("Uzbrojono test pojedynczego alarmu (za 5 sek.)")
                        }
                    )

                    // Test 2: Series (2 alarms at 5s and 15s)
                    DiagnosticTestButton(
                        text = "Test serii (2 alarmy co 10 sek.)",
                        accentColor = PastelBlue,
                        onClick = {
                            AlarmEngine.unlockAudio(context)
                            viewModel.createTestSeries()
                            showToast("Uzbrojono serię testową: #1 za 5s, #2 za 15s")
                        }
                    )

                    // Test 3: Quick Nap (in 5s)
                    DiagnosticTestButton(
                        text = "Test szybkiej drzemki (za 5 sek.)",
                        accentColor = PastelYellow,
                        onClick = {
                            AlarmEngine.unlockAudio(context)
                            viewModel.createTestNap()
                            showToast("Uruchomiono szybką drzemkę (za 5 sek.)")
                        }
                    )

                    // Test 4: Wake-Up Check (in 5s)
                    DiagnosticTestButton(
                        text = "Test 'Sprawdź czy wstałem' (za 5 sek.)",
                        accentColor = PastelPink,
                        onClick = {
                            AlarmEngine.unlockAudio(context)
                            WakeUpCheckReceiver.scheduleTestCheck(context, 5000L)
                            showToast("Uruchomiono weryfikację pobudki (za 5 sek.)")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticStatusRow(
    label: String,
    statusText: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) PastelLime else PastelPink)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = ContrastWhite
            )
        }
        Text(
            text = statusText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) PastelLime else PastelPink
        )
    }
}

@Composable
fun DiagnosticTestButton(
    text: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = DarkPillBg,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ContrastWhite
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
    }
}
