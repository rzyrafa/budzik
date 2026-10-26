package com.budzik.alarm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.ui.components.IosDrumTimePicker
import com.budzik.alarm.ui.theme.ContrastBlack
import com.budzik.alarm.ui.theme.ContrastWhite
import com.budzik.alarm.ui.theme.DarkCardBg
import com.budzik.alarm.ui.theme.DarkPillBg
import com.budzik.alarm.ui.theme.PastelBlue
import com.budzik.alarm.ui.theme.PastelLavender
import com.budzik.alarm.ui.theme.PastelLime
import com.budzik.alarm.ui.theme.PastelPink
import com.budzik.alarm.ui.theme.PastelYellow
import com.budzik.alarm.ui.theme.TextSubtle
import com.budzik.alarm.ui.theme.bouncyPress
import java.util.Calendar

// =========================================================================
// ADD ALARM BOTTOM SHEET
// =========================================================================

@Composable
fun AddAlarmBottomSheet(
    onDismiss: () -> Unit,
    onSaveSingle: (hour: Int, minute: Int, difficulty: Difficulty, problemCount: Int, wakeUpEnabled: Boolean, wakeUpMinutes: Int) -> Unit,
    onSaveSeries: (count: Int, interval: Int, baseHour: Int, baseMinute: Int, difficulty: Difficulty, problemCount: Int, wakeUpEnabled: Boolean, wakeUpMinutes: Int) -> Unit
) {
    var isSeriesMode by remember { mutableStateOf(false) }
    var isPickerExpanded by remember { mutableStateOf(false) }

    val now = Calendar.getInstance()
    var hour by remember { mutableIntStateOf(now.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf((now.get(Calendar.MINUTE) + 5) % 60) }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }
    var problemCount by remember { mutableIntStateOf(3) }
    var wakeUpEnabled by remember { mutableStateOf(false) }
    var wakeUpMinutes by remember { mutableIntStateOf(5) }

    var seriesCount by remember { mutableIntStateOf(5) }
    var seriesInterval by remember { mutableIntStateOf(5) }

    BottomSheetWrapper(
        title = if (isSeriesMode) "NOWA SERIA ALARMÓW" else "NOWY ALARM",
        tabColor = if (isSeriesMode) PastelLime else PastelPink,
        onDismiss = onDismiss
    ) {
        SlidingModeToggle(
            isSeriesMode = isSeriesMode,
            onModeChanged = {
                isSeriesMode = it
                isPickerExpanded = false
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Section 2: iOS Scroll Wheel Drum Time Picker (Android Native clock completely replaced)
        IosDrumTimePicker(
            hour = hour,
            minute = minute,
            isExpanded = isPickerExpanded,
            onExpandedChange = { isPickerExpanded = it },
            onTimeChange = { newH, newM ->
                hour = newH
                minute = newM
            }
        )

        // Smooth Series parameters
        AnimatedVisibility(
            visible = isSeriesMode,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(250)),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut(animationSpec = tween(200))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(8.dp))

                Text("Liczba alarmów", fontSize = 13.sp, color = TextSubtle)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(3, 5, 8, 10).forEach { cnt ->
                        PillOptionChip(
                            text = "$cnt",
                            isSelected = seriesCount == cnt,
                            accentColor = PastelLime,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                seriesCount = cnt
                                isPickerExpanded = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Odstęp", fontSize = 13.sp, color = TextSubtle)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(3, 5, 10, 15).forEach { iv ->
                        PillOptionChip(
                            text = "${iv}m",
                            isSelected = seriesInterval == iv,
                            accentColor = PastelYellow,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                seriesInterval = iv
                                isPickerExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Trudność: Animated sliding selector with PastelBlue & Black text
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Trudność", fontSize = 13.sp, color = TextSubtle)
            Spacer(modifier = Modifier.height(4.dp))
            DifficultySlidingSelector(
                selectedDifficulty = difficulty,
                onDifficultySelected = {
                    difficulty = it
                    isPickerExpanded = false
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Liczba zadań
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Liczba zadań", fontSize = 13.sp, color = TextSubtle)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1, 3, 5).forEach { count ->
                    PillOptionChip(
                        text = "$count zad.",
                        isSelected = problemCount == count,
                        accentColor = PastelLavender,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            problemCount = count
                            isPickerExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sprawdź czy wstałem
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = null
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sprawdź czy wstałem",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ContrastWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isSeriesMode) "Weryfikacja po ostatnim alarmie" else "Powiadomienie weryfikacyjne po wyłączeniu",
                            fontSize = 12.sp,
                            color = Color(0xFFA0A0A5)
                        )
                    }

                    Switch(
                        checked = wakeUpEnabled,
                        onCheckedChange = { wakeUpEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ContrastBlack,
                            checkedTrackColor = if (isSeriesMode) PastelLime else PastelPink,
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF28282C),
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }

                AnimatedVisibility(
                    visible = wakeUpEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Opóźnienie sprawdzenia:",
                            fontSize = 13.sp,
                            color = TextSubtle
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(3, 5, 10).forEach { mins ->
                                PillOptionChip(
                                    text = "$mins min",
                                    isSelected = wakeUpMinutes == mins,
                                    accentColor = if (isSeriesMode) PastelLime else PastelPink,
                                    modifier = Modifier.weight(1f),
                                    onClick = { wakeUpMinutes = mins }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Save Action Button
        Button(
            onClick = {
                if (isSeriesMode) {
                    onSaveSeries(seriesCount, seriesInterval, hour, minute, difficulty, problemCount, wakeUpEnabled, wakeUpMinutes)
                } else {
                    onSaveSingle(hour, minute, difficulty, problemCount, wakeUpEnabled, wakeUpMinutes)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .bouncyPress(),
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSeriesMode) PastelLime else PastelPink,
                contentColor = ContrastBlack
            )
        ) {
            Text(
                text = if (isSeriesMode) "Utwórz serię ($seriesCount alarmów)" else "Zapisz alarm",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =========================================================================
// EDIT ALARM BOTTOM SHEET
// =========================================================================

@Composable
fun EditAlarmBottomSheet(
    alarm: AlarmEntity,
    onDismiss: () -> Unit,
    onSave: (AlarmEntity) -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit
) {
    var hour by remember { mutableIntStateOf(alarm.hour) }
    var minute by remember { mutableIntStateOf(alarm.minute) }
    var isPickerExpanded by remember { mutableStateOf(false) }
    var difficulty by remember {
        mutableStateOf(
            try { Difficulty.valueOf(alarm.difficulty.uppercase()) } catch (e: Exception) { Difficulty.MEDIUM }
        )
    }
    var problemCount by remember { mutableIntStateOf(alarm.problemCount) }
    var wakeUpEnabled by remember { mutableStateOf(alarm.wakeUpCheckEnabled) }
    var wakeUpMinutes by remember { mutableIntStateOf(alarm.wakeUpCheckMinutes) }

    BottomSheetWrapper(
        title = "OPCJE ALARMU",
        tabColor = PastelBlue,
        onDismiss = onDismiss
    ) {
        IosDrumTimePicker(
            hour = hour,
            minute = minute,
            isExpanded = isPickerExpanded,
            onExpandedChange = { isPickerExpanded = it },
            onTimeChange = { newH, newM ->
                hour = newH
                minute = newM
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Trudność", fontSize = 13.sp, color = TextSubtle)
            Spacer(modifier = Modifier.height(6.dp))
            DifficultySlidingSelector(
                selectedDifficulty = difficulty,
                onDifficultySelected = {
                    difficulty = it
                    isPickerExpanded = false
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Liczba zadań", fontSize = 13.sp, color = TextSubtle)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1, 3, 5).forEach { count ->
                    PillOptionChip(
                        text = "$count zad.",
                        isSelected = problemCount == count,
                        accentColor = PastelLavender,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            problemCount = count
                            isPickerExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = null
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sprawdź czy wstałem",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ContrastWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Powiadomienie weryfikujące po wyłączeniu",
                            fontSize = 12.sp,
                            color = Color(0xFFA0A0A5)
                        )
                    }

                    Switch(
                        checked = wakeUpEnabled,
                        onCheckedChange = { wakeUpEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ContrastBlack,
                            checkedTrackColor = PastelPink,
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF28282C),
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }

                AnimatedVisibility(
                    visible = wakeUpEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Opóźnienie sprawdzenia:",
                            fontSize = 13.sp,
                            color = TextSubtle
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(3, 5, 10).forEach { mins ->
                                PillOptionChip(
                                    text = "$mins min",
                                    isSelected = wakeUpMinutes == mins,
                                    accentColor = PastelPink,
                                    modifier = Modifier.weight(1f),
                                    onClick = { wakeUpMinutes = mins }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                onSave(
                    alarm.copy(
                        hour = hour,
                        minute = minute,
                        difficulty = difficulty.name,
                        problemCount = problemCount,
                        wakeUpCheckEnabled = wakeUpEnabled,
                        wakeUpCheckMinutes = wakeUpMinutes
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .bouncyPress(),
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(
                containerColor = ContrastWhite,
                contentColor = ContrastBlack
            )
        ) {
            Text("Zapisz zmiany", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onTest,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .bouncyPress(),
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PastelBlue,
                    contentColor = ContrastBlack
                )
            ) {
                Icon(imageVector = Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Testuj alarm", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .bouncyPress(),
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E1A1D),
                    contentColor = Color(0xFFFF7B7B)
                )
            ) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Usuń alarm", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =========================================================================
// SERIES OPTIONS BOTTOM SHEET
// =========================================================================

@Composable
fun SeriesOptionsBottomSheet(
    seriesId: String,
    alarms: List<AlarmEntity>,
    onDismiss: () -> Unit,
    onSaveSeriesSettings: (enabled: Boolean, minutes: Int) -> Unit,
    onTestFirst: () -> Unit,
    onDeleteSeries: () -> Unit
) {
    val firstAlarm = alarms.firstOrNull()
    var wakeUpEnabled by remember { mutableStateOf(firstAlarm?.wakeUpCheckEnabled ?: false) }
    var wakeUpMinutes by remember { mutableIntStateOf(firstAlarm?.wakeUpCheckMinutes ?: 5) }

    BottomSheetWrapper(
        title = "OPCJE SERII (${alarms.size} ALARMÓW)",
        tabColor = PastelLime,
        onDismiss = onDismiss
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = null
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sprawdź czy wstałem",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ContrastWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Weryfikacja po ostatnim alarmie w całej serii",
                            fontSize = 12.sp,
                            color = Color(0xFFA0A0A5)
                        )
                    }

                    Switch(
                        checked = wakeUpEnabled,
                        onCheckedChange = { wakeUpEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ContrastBlack,
                            checkedTrackColor = PastelLime,
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF28282C),
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }

                AnimatedVisibility(
                    visible = wakeUpEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Opóźnienie sprawdzenia:",
                            fontSize = 13.sp,
                            color = TextSubtle
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(3, 5, 10).forEach { mins ->
                                PillOptionChip(
                                    text = "$mins min",
                                    isSelected = wakeUpMinutes == mins,
                                    accentColor = PastelLime,
                                    modifier = Modifier.weight(1f),
                                    onClick = { wakeUpMinutes = mins }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = { onSaveSeriesSettings(wakeUpEnabled, wakeUpMinutes) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .bouncyPress(),
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(
                containerColor = ContrastWhite,
                contentColor = ContrastBlack
            )
        ) {
            Text("Zapisz ustawienia serii", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onTestFirst,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .bouncyPress(),
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(
                containerColor = PastelBlue,
                contentColor = ContrastBlack
            )
        ) {
            Icon(imageVector = Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Testuj pierwszy alarm serii", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onDeleteSeries,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .bouncyPress(),
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E1A1D),
                contentColor = Color(0xFFFF7B7B)
            )
        ) {
            Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Usuń całą serię", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// =========================================================================
// CLEAR ALL CONFIRMATION BOTTOM SHEET
// =========================================================================

@Composable
fun ClearAllConfirmationBottomSheet(
    onDismiss: () -> Unit,
    onConfirmClear: () -> Unit
) {
    BottomSheetWrapper(
        title = "WYCZYŚĆ WSZYSTKO",
        tabColor = PastelYellow,
        onDismiss = onDismiss
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Czy na pewno chcesz usunąć wszystkie alarmy?",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = ContrastWhite,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Ta akcja wyłączy i bezpowrotnie usunie wszystkie zaplanowane budziki.",
            fontSize = 14.sp,
            color = Color(0xFFA0A0A5),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .bouncyPress(),
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkPillBg,
                    contentColor = ContrastWhite
                )
            ) {
                Text("Anuluj", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = onConfirmClear,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .bouncyPress(),
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PastelPink,
                    contentColor = ContrastBlack
                )
            ) {
                Text("Wyczyść", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
