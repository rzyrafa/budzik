package com.budzik.alarm.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budzik.alarm.sound.AlarmSoundManager
import com.budzik.alarm.ui.theme.ContrastBlack
import com.budzik.alarm.ui.theme.ContrastWhite
import com.budzik.alarm.ui.theme.DarkCardBg
import com.budzik.alarm.ui.theme.DarkPillBg
import com.budzik.alarm.ui.theme.PastelBlue
import com.budzik.alarm.ui.theme.PastelPink
import com.budzik.alarm.ui.theme.TextSubtle
import com.budzik.alarm.ui.theme.bouncyPress
import kotlinx.coroutines.launch

// =========================================================================
// SOUND SETTINGS BOTTOM SHEET ("DŹWIĘK ALARMU")
// =========================================================================

@Composable
fun SoundSettingsBottomSheet(
    viewModel: AlarmViewModel,
    showToast: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedToneId by remember { mutableStateOf(AlarmSoundManager.getSelectedToneId(context)) }
    var volume by remember { mutableFloatStateOf(AlarmSoundManager.getVolume(context)) }
    var gentleRamp by remember { mutableStateOf(AlarmSoundManager.isGentleRampEnabled(context)) }
    var vibrate by remember { mutableStateOf(AlarmSoundManager.isVibrateEnabled(context)) }
    var previewingToneId by remember { mutableStateOf<String?>(null) }
    var customRingtones by remember { mutableStateOf(AlarmSoundManager.getCustomRingtones(context)) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val added = AlarmSoundManager.addCustomRingtone(context, uri)
            if (added != null) {
                customRingtones = AlarmSoundManager.getCustomRingtones(context)
                selectedToneId = added.id
                AlarmSoundManager.setSelectedToneId(context, added.id)
                previewingToneId = added.id
                AlarmSoundManager.playPreview(added.id, volume, context)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AlarmSoundManager.stopPreview()
        }
    }

    BottomSheetWrapper(
        title = "DŹWIĘK ALARMU",
        tabColor = PastelBlue,
        closeButtonColor = PastelPink,
        onDismiss = onDismiss
    ) {
        // 1. Wyróżniający się przycisk/karta "+ Wgraj własny dźwięk (MP3)"
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyPress(0.98f)
                .clickable {
                    filePickerLauncher.launch("audio/*")
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkPillBg),
            border = null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PastelPink),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Dodaj plik",
                        tint = ContrastBlack,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "+ Wgraj własny dźwięk (MP3)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ContrastWhite
                    )
                    Text(
                        text = "Wybierz plik z telefonu (.mp3, .wav, .ogg, .m4a)",
                        fontSize = 11.sp,
                        color = Color(0xFFA0A0A5)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Lista wgranych utworów użytkownika (jeśli istnieją)
        if (customRingtones.isNotEmpty()) {
            Text(
                text = "Twoje wgrane dźwięki",
                fontSize = 12.sp,
                color = TextSubtle,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(4.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                customRingtones.forEach { ringtone ->
                    val isSelected = selectedToneId == ringtone.id
                    val isPlaying = previewingToneId == ringtone.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyPress(0.98f)
                            .clickable {
                                selectedToneId = ringtone.id
                                AlarmSoundManager.setSelectedToneId(context, ringtone.id)
                                if (isPlaying) {
                                    AlarmSoundManager.stopPreview()
                                    previewingToneId = null
                                } else {
                                    previewingToneId = ringtone.id
                                    AlarmSoundManager.playPreview(ringtone.id, volume, context)
                                }
                            },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) PastelBlue else DarkCardBg
                        ),
                        border = null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ringtone.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) ContrastBlack else ContrastWhite,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Własny MP3",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) ContrastBlack.copy(alpha = 0.8f) else PastelPink
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) ContrastBlack else DarkPillBg)
                                        .clickable {
                                            if (isPlaying) {
                                                AlarmSoundManager.stopPreview()
                                                previewingToneId = null
                                            } else {
                                                selectedToneId = ringtone.id
                                                AlarmSoundManager.setSelectedToneId(context, ringtone.id)
                                                previewingToneId = ringtone.id
                                                AlarmSoundManager.playPreview(ringtone.id, volume, context)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                        contentDescription = if (isPlaying) "Zatrzymaj" else "Odsłuchaj",
                                        tint = if (isSelected) PastelBlue else ContrastWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) ContrastBlack.copy(alpha = 0.12f) else Color(0xFF2E1A1D))
                                        .clickable {
                                            if (previewingToneId == ringtone.id) {
                                                AlarmSoundManager.stopPreview()
                                                previewingToneId = null
                                            }
                                            AlarmSoundManager.deleteCustomRingtone(context, ringtone.id)
                                            customRingtones = AlarmSoundManager.getCustomRingtones(context)
                                            selectedToneId = AlarmSoundManager.getSelectedToneId(context)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Usuń dźwięk",
                                        tint = if (isSelected) ContrastBlack else Color(0xFFFF7B7B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // 3. Synthesized Built-in Tones List (zmniejszone odstępy pionowe)
        Text(
            text = "Wbudowane brzmienia alarmu",
            fontSize = 12.sp,
            color = TextSubtle,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(4.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AlarmSoundManager.AVAILABLE_TONES.forEach { tone ->
                val isSelected = selectedToneId == tone.id
                val isPlaying = previewingToneId == tone.id

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyPress(0.98f)
                        .clickable {
                            selectedToneId = tone.id
                            AlarmSoundManager.setSelectedToneId(context, tone.id)
                            if (isPlaying) {
                                AlarmSoundManager.stopPreview()
                                previewingToneId = null
                            } else {
                                previewingToneId = tone.id
                                AlarmSoundManager.playPreview(tone.id, volume, context)
                            }
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) PastelBlue else DarkCardBg
                    ),
                    border = null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tone.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) ContrastBlack else ContrastWhite
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tone.description,
                                fontSize = 11.sp,
                                color = if (isSelected) ContrastBlack.copy(alpha = 0.8f) else Color(0xFFA0A0A5)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) ContrastBlack else DarkPillBg)
                                    .bouncyPress()
                                    .clickable {
                                        if (isPlaying) {
                                            AlarmSoundManager.stopPreview()
                                            previewingToneId = null
                                        } else {
                                            selectedToneId = tone.id
                                            AlarmSoundManager.setSelectedToneId(context, tone.id)
                                            previewingToneId = tone.id
                                            AlarmSoundManager.playPreview(tone.id, volume, context)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                    contentDescription = if (isPlaying) "Zatrzymaj" else "Odsłuchaj",
                                    tint = if (isSelected) PastelBlue else ContrastWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Suwak Głośności (Volume Slider)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = null
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Głośność alarmu",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ContrastWhite
                    )
                    Text(
                        text = "${(volume * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PastelBlue
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        AlarmSoundManager.setVolume(context, it)
                        if (previewingToneId != null) {
                            AlarmSoundManager.setPreviewVolume(it)
                        }
                    },
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = PastelBlue,
                        activeTrackColor = PastelBlue,
                        inactiveTrackColor = DarkPillBg
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Przełączniki: "Łagodne narastanie dźwięku" & "Wibracje"
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
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
                            text = "Łagodne narastanie dźwięku",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ContrastWhite
                        )
                        Text(
                            text = "Płynny start od cichego tonu przez pierwsze 15s",
                            fontSize = 11.sp,
                            color = Color(0xFFA0A0A5)
                        )
                    }

                    Switch(
                        checked = gentleRamp,
                        onCheckedChange = {
                            gentleRamp = it
                            AlarmSoundManager.setGentleRampEnabled(context, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ContrastBlack,
                            checkedTrackColor = PastelBlue,
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF28282C),
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wibracje",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ContrastWhite
                        )
                        Text(
                            text = "Intensywny wzorzec wibracji podczas dzwonienia",
                            fontSize = 11.sp,
                            color = Color(0xFFA0A0A5)
                        )
                    }

                    Switch(
                        checked = vibrate,
                        onCheckedChange = {
                            vibrate = it
                            AlarmSoundManager.setVibrateEnabled(context, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ContrastBlack,
                            checkedTrackColor = PastelBlue,
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF28282C),
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Built-in Diagnostic & Instant Test Panel
        AlarmDiagnosticSection(
            viewModel = viewModel,
            showToast = showToast
        )
    }
}
