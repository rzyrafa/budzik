package com.budzik.alarm.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.data.local.isNap
import com.budzik.alarm.engine.AlarmEngine
import com.budzik.alarm.engine.WakeUpCheckReceiver
import com.budzik.alarm.sound.AlarmSoundManager
import com.budzik.alarm.ui.theme.ContrastBlack
import com.budzik.alarm.ui.theme.ContrastWhite
import com.budzik.alarm.ui.theme.MainBgCream
import com.budzik.alarm.ui.theme.PastelBlue
import com.budzik.alarm.ui.theme.PastelLavender
import com.budzik.alarm.ui.theme.PastelLime
import com.budzik.alarm.ui.theme.PastelPink
import com.budzik.alarm.ui.theme.PastelSingleAlarmColors
import com.budzik.alarm.ui.theme.PastelYellow
import com.budzik.alarm.ui.theme.PillBgBeige
import com.budzik.alarm.ui.theme.TextSecondaryLight
import com.budzik.alarm.ui.theme.bouncyPress
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class BottomSheetMode {
    object AddAlarmOrSeries : BottomSheetMode()
    data class EditAlarm(val alarm: AlarmEntity) : BottomSheetMode()
    data class EditSeries(val seriesId: String, val alarms: List<AlarmEntity>) : BottomSheetMode()
    object SoundSettings : BottomSheetMode()
    object QuickNap : BottomSheetMode()
    object ClearAllConfirm : BottomSheetMode()
}

sealed class AlarmGroupItem {
    data class Single(val alarm: AlarmEntity) : AlarmGroupItem()
    data class Series(val seriesId: String, val alarms: List<AlarmEntity>) : AlarmGroupItem()
}

@Composable
fun MainAlarmScreen(
    viewModel: AlarmViewModel,
    onTestAlarm: (AlarmEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alarms by viewModel.alarms.collectAsState()
    val listState = rememberLazyListState()

    // Unlock audio subsystem on compose
    LaunchedEffect(Unit) {
        AlarmEngine.unlockAudio(context)
    }

    // Request Notification permission on Android 13+ (API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { /* handled */ }
        LaunchedEffect(Unit) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var activeSheet by remember { mutableStateOf<BottomSheetMode?>(null) }
    var expandedSeriesIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Toast state
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var toastJob by remember { mutableStateOf<Job?>(null) }
    val showToast: (String) -> Unit = { msg ->
        toastJob?.cancel()
        toastMessage = msg
        toastJob = scope.launch {
            delay(2500L)
            if (toastMessage == msg) {
                toastMessage = null
            }
        }
    }

    // Active Wake-Up Check ("Sprawdź czy wstałem") state polling
    val activeWakeUpCheck by WakeUpCheckReceiver.activeCheckFlow.collectAsState()
    LaunchedEffect(Unit) {
        WakeUpCheckReceiver.getActiveWakeUpCheck(context)
        while (true) {
            delay(1000L)
            WakeUpCheckReceiver.getActiveWakeUpCheck(context)
        }
    }

    // Enforce dark status and nav bar icons and remove edge lines
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isStatusBarContrastEnforced = false
                    window.isNavigationBarContrastEnforced = false
                }
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    val hasOverlayPermission = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    val alarmGroupItems = remember(alarms) {
        val list = mutableListOf<AlarmGroupItem>()
        val seriesMap = linkedMapOf<String, MutableList<AlarmEntity>>()
        for (a in alarms) {
            val sId = a.seriesId
            if (sId != null && sId.isNotBlank()) {
                seriesMap.getOrPut(sId) { mutableListOf() }.add(a)
            }
        }
        val processedSeries = mutableSetOf<String>()
        for (a in alarms) {
            val sId = a.seriesId
            if (sId != null && sId.isNotBlank()) {
                if (sId !in processedSeries) {
                    processedSeries.add(sId)
                    val seriesList = seriesMap[sId]?.sortedBy { it.seriesPosition } ?: listOf(a)
                    list.add(AlarmGroupItem.Series(sId, seriesList))
                }
            } else {
                list.add(AlarmGroupItem.Single(a))
            }
        }
        list
    }

    // Dynamic countdown calculation for subtitle under "Budzik" - updates every 1 second live
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L) // update every 1 second live
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val nextAlarmSubtitle = remember(alarms, currentTimeMs) {
        val enabledAlarms = alarms.filter { it.isEnabled }
        if (enabledAlarms.isEmpty()) {
            "Wszystkie alarmy wyłączone"
        } else {
            val nextEpoch = enabledAlarms.minOf { alarm ->
                alarm.getEffectiveTriggerEpochMs(currentTimeMs)
            }
            val diffMs = nextEpoch - currentTimeMs
            if (diffMs <= 0) {
                "Dzwoni teraz"
            } else {
                val totalSeconds = diffMs / 1000L
                val totalMinutes = totalSeconds / 60
                val hours = totalMinutes / 60
                val mins = totalMinutes % 60
                val secs = totalSeconds % 60
                if (hours > 0) {
                    "Dzwoni za $hours godz. $mins min"
                } else if (mins > 0) {
                    "Dzwoni za $mins min $secs sek."
                } else {
                    "Dzwoni za $secs sek."
                }
            }
        }
    }

    // Active snooze/power nap alarm if present
    val activeNapAlarm = remember(alarms) {
        alarms.firstOrNull { it.isEnabled && it.isNap }
    }

    val isSheetOpen = activeSheet != null
    // activeSheet becomes null the moment a sheet is closed; keep rendering the last one so the
    // slide-out animation has content to move (otherwise the sheet just vanishes)
    var lastShownSheet by remember { mutableStateOf<BottomSheetMode?>(null) }
    if (activeSheet != null) lastShownSheet = activeSheet
    val bgColor = MainBgCream // Warm cream #F5F4EF

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
    ) {
        // Main Scrollable Content Area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header: "Budzik" + Bell button + Subtitle countdown + Clear All
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Budzik",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ContrastBlack,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = nextAlarmSubtitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondaryLight
                    )
                }

                if (alarms.isNotEmpty()) {
                    Surface(
                        onClick = { activeSheet = BottomSheetMode.ClearAllConfirm },
                        shape = RoundedCornerShape(percent = 50),
                        color = PillBgBeige,
                        border = null,
                        modifier = Modifier.bouncyPress()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = "Wyczyść wszystko",
                                modifier = Modifier.size(16.dp),
                                tint = ContrastBlack
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Wyczyść",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ContrastBlack
                            )
                        }
                    }
                }
            }

            // System Alert Permission Notice
            if (!hasOverlayPermission) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyPress(0.98f),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = ContrastWhite),
                    border = null
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = ContrastBlack,
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(PastelYellow, CircleShape)
                                    .padding(4.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Wyświetlanie nad aplikacjami",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = ContrastBlack
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Aby zadania automatycznie pojawiały się na ekranie w momencie alarmu, włącz uprawnienie.",
                            fontSize = 13.sp,
                            color = TextSecondaryLight,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(percent = 50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ContrastBlack,
                                contentColor = ContrastWhite
                            ),
                            modifier = Modifier.bouncyPress()
                        ) {
                            Text("Włącz uprawnienie", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Alarms List + Power Nap Card in LazyColumn
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 130.dp)
            ) {
                if (alarms.isEmpty()) {
                    item(key = "empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(PillBgBeige, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Alarm,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = ContrastBlack
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Brak budzików",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ContrastBlack
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Dotknij +, aby utworzyć alarm lub uruchom szybką drzemkę",
                                    fontSize = 13.sp,
                                    color = TextSecondaryLight,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Render alarms (single and series)
                itemsIndexed(alarmGroupItems, key = { idx, item ->
                    when (item) {
                        is AlarmGroupItem.Single -> "single_${item.alarm.id}"
                        is AlarmGroupItem.Series -> "series_${item.seriesId}"
                    }
                }) { index, item ->
                    when (item) {
                        is AlarmGroupItem.Single -> {
                            // Automatically rotate pastel palette: PastelBlue (#A8D5FC), PastelPink (#F4B8E4), PastelYellow (#F9E784), PastelLime (#CBE896), PastelLavender (#D3C4FC)
                            val singleAlarmIndex = remember(alarmGroupItems, item) {
                                var count = 0
                                for (it in alarmGroupItems) {
                                    if (it === item) break
                                    if (it is AlarmGroupItem.Single) count++
                                }
                                count
                            }
                            val pastelColor = PastelSingleAlarmColors[singleAlarmIndex % PastelSingleAlarmColors.size]
                            SingleAlarmCard(
                                alarm = item.alarm,
                                cardColor = pastelColor,
                                onToggle = { isEnabled -> viewModel.setAlarmEnabled(item.alarm, isEnabled) },
                                onOpenDotMenu = {
                                    activeSheet = BottomSheetMode.EditAlarm(item.alarm)
                                }
                            )
                        }

                        is AlarmGroupItem.Series -> {
                            val isExpanded = item.seriesId in expandedSeriesIds
                            StackedSeriesCard(
                                seriesId = item.seriesId,
                                alarms = item.alarms,
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    expandedSeriesIds = if (isExpanded) {
                                        expandedSeriesIds - item.seriesId
                                    } else {
                                        expandedSeriesIds + item.seriesId
                                    }
                                },
                                onToggleMaster = { enableAll -> viewModel.setSeriesEnabled(item.alarms, enableAll) },
                                onToggleSingle = { alarm, isEnabled -> viewModel.setAlarmEnabled(alarm, isEnabled) },
                                onOpenDotMenu = { alarm ->
                                    activeSheet = BottomSheetMode.EditAlarm(alarm)
                                },
                                onOpenSeriesDotMenu = {
                                    activeSheet = BottomSheetMode.EditSeries(item.seriesId, item.alarms)
                                }
                            )
                        }
                    }
                }

                // Section 5: Bento Card "Szybka drzemka (Power Nap)"
                item(key = "power_nap_card") {
                    PowerNapCard(
                        activeNapAlarm = activeNapAlarm,
                        onStartNap = { minutes ->
                            AlarmEngine.unlockAudio(context)
                            viewModel.startNap(minutes, requireMathProblem = true)
                            showToast("Uruchomiono drzemkę na $minutes min")
                        },
                        onCancelNap = { napAlarm ->
                            viewModel.cancelNap(napAlarm)
                            showToast("Anulowano drzemkę")
                        }
                    )
                }
            }
        }

        // Floating Pill Navigation Bar (Anchored at Bottom Center)
        // Hidden when bottom sheet is open so "X" button lives directly inside the sheet!
        AnimatedVisibility(
            visible = !isSheetOpen,
            enter = fadeIn(animationSpec = tween(200)) + slideInVertically { it / 2 },
            exit = fadeOut(animationSpec = tween(150)) + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .zIndex(100f)
        ) {
            FloatingPillDock(
                onOpenSoundSettings = { activeSheet = BottomSheetMode.SoundSettings },
                onCenterButtonClick = { activeSheet = BottomSheetMode.AddAlarmOrSeries },
                onQuickNapClick = { activeSheet = BottomSheetMode.QuickNap }
            )
        }

        // Bottom Sheet Overlay Backdrop
        AnimatedVisibility(
            visible = isSheetOpen,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250)),
            modifier = Modifier.zIndex(50f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable {
                        AlarmSoundManager.stopPreview()
                        activeSheet = null
                    }
            )
        }

        // Animated Bottom Sheet
        AnimatedVisibility(
            visible = isSheetOpen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut(),
            // No navigationBarsPadding here: the sheet's black background runs under the
            // system navigation bar and BottomSheetWrapper pads its content instead
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(60f)
        ) {
            when (val mode = activeSheet ?: lastShownSheet) {
                is BottomSheetMode.AddAlarmOrSeries -> {
                    AddAlarmBottomSheet(
                        onDismiss = { activeSheet = null },
                        onSaveSingle = { hour, minute, difficulty, problemCount, wakeUpEnabled, wakeUpMinutes ->
                            AlarmEngine.unlockAudio(context)
                            viewModel.createSingleAlarm(hour, minute, difficulty, problemCount, wakeUpEnabled, wakeUpMinutes)
                            showToast("Utworzono alarm")
                            activeSheet = null
                        },
                        onSaveSeries = { count, interval, baseHour, baseMinute, difficulty, problemCount, wakeUpEnabled, wakeUpMinutes ->
                            AlarmEngine.unlockAudio(context)
                            viewModel.createSeries(count, interval, baseHour, baseMinute, difficulty, problemCount, wakeUpEnabled, wakeUpMinutes)
                            showToast("Utworzono serię $count alarmów")
                            activeSheet = null
                        }
                    )
                }

                is BottomSheetMode.EditAlarm -> {
                    EditAlarmBottomSheet(
                        alarm = mode.alarm,
                        onDismiss = { activeSheet = null },
                        onSave = { updated ->
                            viewModel.saveAlarm(updated)
                            showToast("Zapisano zmiany")
                            activeSheet = null
                        },
                        onTest = {
                            activeSheet = null
                            onTestAlarm(mode.alarm)
                        },
                        onDelete = {
                            viewModel.deleteAlarm(mode.alarm)
                            showToast("Usunięto alarm")
                            activeSheet = null
                        }
                    )
                }

                is BottomSheetMode.EditSeries -> {
                    SeriesOptionsBottomSheet(
                        seriesId = mode.seriesId,
                        alarms = mode.alarms,
                        onDismiss = { activeSheet = null },
                        onSaveSeriesSettings = { enabled, minutes ->
                            viewModel.saveSeriesWakeUpSettings(mode.alarms, enabled, minutes)
                            showToast("Zapisano ustawienia serii")
                            activeSheet = null
                        },
                        onTestFirst = {
                            activeSheet = null
                            mode.alarms.firstOrNull()?.let { onTestAlarm(it) }
                        },
                        onDeleteSeries = {
                            viewModel.deleteSeries(mode.seriesId, mode.alarms)
                            showToast("Usunięto serię alarmów")
                            activeSheet = null
                        }
                    )
                }

                is BottomSheetMode.SoundSettings -> {
                    SoundSettingsBottomSheet(
                        viewModel = viewModel,
                        showToast = showToast,
                        onDismiss = {
                            AlarmSoundManager.stopPreview()
                            activeSheet = null
                        }
                    )
                }

                is BottomSheetMode.QuickNap -> {
                    QuickNapBottomSheet(
                        activeNapAlarm = activeNapAlarm,
                        onStartNap = { minutes, requireMathProblem ->
                            AlarmEngine.unlockAudio(context)
                            viewModel.startNap(minutes, requireMathProblem)
                            showToast("Uruchomiono drzemkę na $minutes min")
                            activeSheet = null
                        },
                        onCancelNap = { napAlarm ->
                            viewModel.cancelNap(napAlarm)
                            showToast("Anulowano drzemkę")
                            activeSheet = null
                        },
                        onDismiss = { activeSheet = null }
                    )
                }

                is BottomSheetMode.ClearAllConfirm -> {
                    ClearAllConfirmationBottomSheet(
                        onDismiss = { activeSheet = null },
                        onConfirmClear = {
                            viewModel.clearAll()
                            showToast("Usunięto wszystkie alarmy")
                            activeSheet = null
                        }
                    )
                }

                null -> {}
            }
        }

        // Distinctive Toast Notification Overlay (Positioned 96px above bottom, cleanly OVER the bottom nav bar, vector SVG icon without ⏰)
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(animationSpec = tween(180)) + slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
            ),
            exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(
                targetOffsetY = { it / 2 }
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp)
                .zIndex(150f)
        ) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = ContrastBlack,
                shadowElevation = 12.dp,
                border = null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Alarm,
                        contentDescription = null,
                        tint = PastelYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = toastMessage ?: "",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ContrastWhite
                    )
                }
            }
        }

        // Interactive "Sprawdź czy wstałem" Fullscreen/Modal Verification Panel
        activeWakeUpCheck?.let { check ->
            WakeUpVerificationOverlay(
                activeCheck = check,
                onConfirm = {
                    WakeUpCheckReceiver.confirmAwake(context, check.alarmId)
                    showToast("Potwierdzono pobudkę! Dobrego dnia.")
                }
            )
        }
    }
}
