package com.budzik.alarm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.engine.AlarmSchedulerImpl
import com.budzik.alarm.ui.theme.ContrastBlack
import com.budzik.alarm.ui.theme.ContrastWhite
import com.budzik.alarm.ui.theme.PastelBlue
import com.budzik.alarm.ui.theme.PastelLime
import com.budzik.alarm.ui.theme.PastelPink
import com.budzik.alarm.ui.theme.PastelSeriesColors
import com.budzik.alarm.ui.theme.PastelYellow
import com.budzik.alarm.ui.theme.bouncyPress
import kotlinx.coroutines.delay

// =========================================================================
// FLOATING PILL DOCK (3 ICONS: DZWONKI, +, DRZEMKA)
// =========================================================================

@Composable
fun FloatingPillDock(
    onOpenSoundSettings: () -> Unit,
    onCenterButtonClick: () -> Unit,
    onQuickNapClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .shadow(elevation = 20.dp, shape = RoundedCornerShape(percent = 50))
            .height(64.dp)
            .width(230.dp),
        shape = RoundedCornerShape(percent = 50),
        color = ContrastBlack,
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Dzwonki (Sound Menu)
            IconButton(
                onClick = onOpenSoundSettings,
                modifier = Modifier
                    .size(46.dp)
                    .bouncyPress()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Dzwonki",
                    tint = ContrastWhite,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Center: + (Add alarm or series)
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(PastelPink)
                    .bouncyPress()
                    .clickable { onCenterButtonClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Dodaj alarm",
                    modifier = Modifier.size(28.dp),
                    tint = ContrastBlack
                )
            }

            // Right: Drzemka (Power Nap)
            IconButton(
                onClick = onQuickNapClick,
                modifier = Modifier
                    .size(46.dp)
                    .bouncyPress()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = "Drzemka",
                    tint = ContrastWhite,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// =========================================================================
// BENTO WIDGET: SZYBKA DRZEMKA (POWER NAP)
// =========================================================================

@Composable
fun PowerNapCard(
    activeNapAlarm: AlarmEntity?,
    onStartNap: (minutes: Int) -> Unit,
    onCancelNap: (AlarmEntity) -> Unit
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activeNapAlarm) {
        if (activeNapAlarm != null) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    val liveCountdownText = remember(activeNapAlarm, nowMs) {
        if (activeNapAlarm == null) {
            "Błyskawiczny alarm jednym dotknięciem"
        } else {
            val targetEpoch = if (activeNapAlarm.nextTriggerAt > 0L) {
                activeNapAlarm.nextTriggerAt
            } else {
                AlarmSchedulerImpl.calculateNextTriggerEpochMs(activeNapAlarm.hour, activeNapAlarm.minute)
            }
            val diffMs = targetEpoch - nowMs
            if (diffMs <= 0) {
                "Drzemka dzwoni teraz!"
            } else {
                val totalSecs = (diffMs + 999L) / 1000L
                val mins = totalSecs / 60
                val secs = totalSecs % 60
                "Dzwoni za: ${mins} min ${secs} s (o ${String.format("%02d:%02d", activeNapAlarm.hour, activeNapAlarm.minute)})"
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = ContrastBlack),
        border = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(PastelYellow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bolt,
                            contentDescription = null,
                            tint = ContrastBlack,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Szybka drzemka",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = ContrastWhite
                        )
                        Text(
                            text = liveCountdownText,
                            fontSize = 12.sp,
                            fontWeight = if (activeNapAlarm != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (activeNapAlarm != null) PastelYellow else Color(0xFFA0A0A5)
                        )
                    }
                }

                if (activeNapAlarm != null) {
                    Surface(
                        onClick = { onCancelNap(activeNapAlarm) },
                        shape = RoundedCornerShape(percent = 50),
                        color = Color(0xFF2E1A1D),
                        modifier = Modifier.bouncyPress()
                    ) {
                        Text(
                            text = "Anuluj",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF7B7B),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3 Quick Pill Buttons: +15 min (Yellow), +20 min (Pink), +45 min (Blue)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NapPillButton(
                    label = "+15 min",
                    bgColor = PastelYellow,
                    modifier = Modifier.weight(1f),
                    onClick = { onStartNap(15) }
                )
                NapPillButton(
                    label = "+20 min",
                    bgColor = PastelPink,
                    modifier = Modifier.weight(1f),
                    onClick = { onStartNap(20) }
                )
                NapPillButton(
                    label = "+45 min",
                    bgColor = PastelBlue,
                    modifier = Modifier.weight(1f),
                    onClick = { onStartNap(45) }
                )
            }
        }
    }
}

@Composable
fun NapPillButton(
    label: String,
    bgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = bgColor,
        modifier = modifier
            .height(44.dp)
            .bouncyPress()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ContrastBlack
            )
        }
    }
}

// =========================================================================
// SINGLE ALARM CARD (ROTATING PASTEL FILL, DEEP BLACK TEXT, BLACK SWITCH)
// =========================================================================

@Composable
fun SingleAlarmCard(
    alarm: AlarmEntity,
    cardColor: Color,
    onToggle: (Boolean) -> Unit,
    onOpenDotMenu: () -> Unit
) {
    val timeFormatted = String.format("%02d:%02d", alarm.hour, alarm.minute)
    val diffLabel = when (alarm.difficulty.uppercase()) {
        "EASY" -> "Łatwy"
        "HARD" -> "Trudny"
        else -> "Średni"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Duża czarna godzina (#0D0D0D)
                Text(
                    text = timeFormatted,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (alarm.isEnabled) ContrastBlack else ContrastBlack.copy(alpha = 0.45f),
                    letterSpacing = (-1).sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                val subtitle = buildString {
                    append(diffLabel)
                    append(" • ")
                    append("${alarm.problemCount} zad.")
                    if (alarm.wakeUpCheckEnabled) {
                        append(" • Weryfikacja ${alarm.wakeUpCheckMinutes} min")
                    }
                }

                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (alarm.isEnabled) ContrastBlack.copy(alpha = 0.85f) else ContrastBlack.copy(alpha = 0.45f)
                )
            }

            // Czarny przełącznik z pastelowym środkiem i czarna ikona trzech kropek
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = cardColor,
                        checkedTrackColor = ContrastBlack,
                        uncheckedThumbColor = cardColor.copy(alpha = 0.5f),
                        uncheckedTrackColor = ContrastBlack,
                        uncheckedBorderColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onOpenDotMenu,
                    modifier = Modifier
                        .size(36.dp)
                        .bouncyPress()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Opcje alarmu",
                        tint = ContrastBlack
                    )
                }
            }
        }
    }
}

// =========================================================================
// STACKED CARDS FOR SERIES
// =========================================================================

@Composable
fun StackedSeriesCard(
    seriesId: String,
    alarms: List<AlarmEntity>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleMaster: (Boolean) -> Unit,
    onToggleSingle: (AlarmEntity, Boolean) -> Unit,
    onOpenDotMenu: (AlarmEntity) -> Unit,
    onOpenSeriesDotMenu: () -> Unit
) {
    val allEnabled = alarms.all { it.isEnabled }
    val firstAlarm = alarms.firstOrNull() ?: return
    val lastAlarm = alarms.lastOrNull() ?: firstAlarm

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "series_chevron_rotation"
    )

    val yellowOffsetY by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else (-16).dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "yellowTabOffset"
    )
    val yellowAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "yellowTabAlpha"
    )

    val greenOffsetY by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else (-8).dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "greenTabOffset"
    )
    val greenAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "greenTabAlpha"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .height(28.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = yellowOffsetY)
                    .graphicsLayer { alpha = yellowAlpha },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = PastelYellow,
                border = null
            ) {}

            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .height(28.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = greenOffsetY)
                    .graphicsLayer { alpha = greenAlpha },
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                color = PastelLime,
                border = null
            ) {}

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyPress(0.98f)
                    .clickable { onToggleExpand() },
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = ContrastBlack),
                border = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = PastelPink,
                            border = null
                        ) {
                            Text(
                                text = "Seria • ${alarms.size} alarmów",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ContrastBlack,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = String.format("%02d:%02d – %02d:%02d", firstAlarm.hour, firstAlarm.minute, lastAlarm.hour, lastAlarm.minute),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFA0A0A5)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = String.format("%02d:%02d", firstAlarm.hour, firstAlarm.minute),
                                fontSize = 46.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (allEnabled) ContrastWhite else Color.Gray.copy(alpha = 0.6f),
                                letterSpacing = (-1).sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            val diffLabel = when (firstAlarm.difficulty.uppercase()) {
                                "EASY" -> "Łatwy"
                                "HARD" -> "Trudny"
                                else -> "Średni"
                            }
                            Text(
                                text = "$diffLabel • ${firstAlarm.problemCount} zad. ${if (firstAlarm.wakeUpCheckEnabled) "• Weryfikacja ${firstAlarm.wakeUpCheckMinutes} min" else ""}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFA0A0A5)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = allEnabled,
                                onCheckedChange = onToggleMaster,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ContrastBlack,
                                    checkedTrackColor = PastelPink,
                                    uncheckedThumbColor = Color(0xFF888888),
                                    uncheckedTrackColor = Color(0xFF28282C),
                                    uncheckedBorderColor = Color.Transparent
                                )
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = onOpenSeriesDotMenu,
                                modifier = Modifier
                                    .size(36.dp)
                                    .bouncyPress()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "Opcje serii",
                                    tint = ContrastWhite
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Zwiń serię" else "Rozwiń serię",
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(chevronRotation),
                            tint = Color(0xFFA0A0A5)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                alarms.forEachIndexed { idx, alarm ->
                    val pastelTint = PastelSeriesColors[idx % PastelSeriesColors.size]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyPress(0.98f),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = pastelTint),
                        border = null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(ContrastBlack),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(pastelTint),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "#${idx + 1}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = ContrastBlack
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ContrastBlack
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                val diffPolish = when (alarm.difficulty.uppercase()) {
                                    "EASY" -> "Łatwy"
                                    "HARD" -> "Trudny"
                                    else -> "Średni"
                                }
                                val subtitle = buildString {
                                    append(diffPolish)
                                    append(" • ")
                                    append("${alarm.problemCount} zad.")
                                    if (alarm.wakeUpCheckEnabled) {
                                        append(" • Weryfikacja ${alarm.wakeUpCheckMinutes} min")
                                    }
                                }
                                Text(
                                    text = subtitle,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ContrastBlack
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = alarm.isEnabled,
                                    onCheckedChange = { onToggleSingle(alarm, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = pastelTint,
                                        checkedTrackColor = ContrastBlack,
                                        uncheckedThumbColor = pastelTint.copy(alpha = 0.5f),
                                        uncheckedTrackColor = ContrastBlack,
                                        uncheckedBorderColor = Color.Transparent
                                    )
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                IconButton(
                                    onClick = { onOpenDotMenu(alarm) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .bouncyPress()
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreVert,
                                        contentDescription = "Opcje",
                                        tint = ContrastBlack
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
