package com.budzik.alarm.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.budzik.alarm.ui.theme.ContrastBlack
import com.budzik.alarm.ui.theme.ContrastWhite
import com.budzik.alarm.ui.theme.DarkPillBg
import com.budzik.alarm.ui.theme.PastelPink
import com.budzik.alarm.ui.theme.bouncyPress
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun IosDrumTimePicker(
    hour: Int,
    minute: Int,
    onTimeChange: (newHour: Int, newMinute: Int) -> Unit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null
) {
    var internalExpanded by remember { mutableStateOf(false) }
    val actualExpanded = isExpanded ?: internalExpanded
    val setExpanded: (Boolean) -> Unit = { expanded ->
        if (onExpandedChange != null) {
            onExpandedChange(expanded)
        } else {
            internalExpanded = expanded
        }
    }

    val hourListState = rememberLazyListState(initialFirstVisibleItemIndex = hour.coerceIn(0, 23))
    val minuteListState = rememberLazyListState(initialFirstVisibleItemIndex = minute.coerceIn(0, 59))
    val selectedHour by remember { derivedStateOf { hourListState.centeredIndex() } }
    val selectedMinute by remember { derivedStateOf { minuteListState.centeredIndex() } }

    // Sync external changes into the wheels when closed
    LaunchedEffect(hour) {
        if (!actualExpanded && selectedHour != hour) {
            hourListState.scrollToItem(hour.coerceIn(0, 23))
        }
    }
    LaunchedEffect(minute) {
        if (!actualExpanded && selectedMinute != minute) {
            minuteListState.scrollToItem(minute.coerceIn(0, 59))
        }
    }

    // Report the value under the selection bar while the wheel spins
    LaunchedEffect(Unit) {
        snapshotFlow { selectedHour to selectedMinute }
            .distinctUntilChanged()
            .collect { (h, m) -> onTimeChange(h, m) }
    }

    val itemHeight = 50.dp
    val pickerHeight = 156.dp
    val animEasing = remember { CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f) }

    // Smooth transform animation for the large clock display (350ms cubic-bezier(0.22, 1, 0.36, 1))
    val headerScale by animateFloatAsState(
        targetValue = if (actualExpanded) 0.80f else 1.0f,
        animationSpec = tween(durationMillis = 350, easing = animEasing),
        label = "drum_header_scale"
    )
    val headerOffsetY by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (actualExpanded) (-6).dp else 0.dp,
        animationSpec = tween(durationMillis = 350, easing = animEasing),
        label = "drum_header_offset_y"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Large Clock Display (Clickable to toggle drum picker with cubic-bezier transition)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyPress()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    setExpanded(!actualExpanded)
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%02d:%02d", selectedHour, selectedMinute),
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ContrastWhite,
                    letterSpacing = (-1).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .graphicsLayer {
                            scaleX = headerScale
                            scaleY = headerScale
                            translationY = headerOffsetY.toPx()
                        }
                )
                if (!actualExpanded) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Dotknij, aby zmienić czas",
                        fontSize = 12.sp,
                        color = Color(0xFFA0A0A5)
                    )
                }
            }
        }

        // Animated Drum Time Picker
        AnimatedVisibility(
            visible = actualExpanded,
            enter = expandVertically(
                animationSpec = tween(
                    durationMillis = 350,
                    easing = animEasing
                )
            ) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(
                animationSpec = tween(
                    durationMillis = 300,
                    easing = animEasing
                )
            ) + fadeOut(animationSpec = tween(250))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Wheel container with selection bar (#1C1C22, border-radius: 16px)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(pickerHeight)
                        .clip(RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Center selection pill background: #1C1C22, border-radius: 16px
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .background(Color(0xFF1C1C22), RoundedCornerShape(16.dp))
                    )

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hours column (00..23)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(pickerHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            DrumColumn(
                                state = hourListState,
                                count = 24,
                                itemHeight = itemHeight,
                                pickerHeight = pickerHeight
                            )
                        }

                        // Colon separator ':'
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(itemHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ":",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ContrastWhite,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.graphicsLayer {
                                    translationY = -2f
                                }
                            )
                        }

                        // Minutes column (00..59)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(pickerHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            DrumColumn(
                                state = minuteListState,
                                count = 60,
                                itemHeight = itemHeight,
                                pickerHeight = pickerHeight
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // "Gotowe" Button to collapse wheel back cleanly
                Surface(
                    onClick = { setExpanded(false) },
                    shape = RoundedCornerShape(percent = 50),
                    color = DarkPillBg,
                    modifier = Modifier.bouncyPress()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Gotowe",
                            tint = ContrastWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Gotowe",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ContrastWhite
                        )
                    }
                }
            }
        }
    }
}

/** Index of the item whose centre is closest to the middle of the wheel (the selection bar). */
private fun LazyListState.centeredIndex(): Int {
    val info = layoutInfo
    val items = info.visibleItemsInfo
    if (items.isEmpty()) return firstVisibleItemIndex
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return items.minBy { abs(it.offset + it.size / 2 - center) }.index
}

/**
 * One wheel of the picker. A LazyColumn with snap fling: the list keeps the finger's momentum
 * (spline decay, like any Android list), so a hard flick flies through many values and a gentle one
 * moves one or two, then it settles exactly on the value nearest the selection bar.
 */
@Composable
private fun DrumColumn(
    state: LazyListState,
    count: Int,
    itemHeight: Dp,
    pickerHeight: Dp
) {
    val scope = rememberCoroutineScope()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = state, snapPosition = SnapPosition.Center)
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    LazyColumn(
        state = state,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(vertical = (pickerHeight - itemHeight) / 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        items(count) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .graphicsLayer {
                        // Read in the draw phase only: scale/fade by distance from the selection bar
                        val info = state.layoutInfo
                        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                        val distance = if (item == null) 1f else {
                            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            (abs(item.offset + item.size / 2f - center) / itemHeightPx).coerceIn(0f, 1f)
                        }
                        val scale = lerp(1.0f, 0.85f, distance)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(1.0f, 0.30f, distance)
                    }
                    .clickable { scope.launch { state.animateScrollToItem(index) } },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format("%02d", index),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ContrastWhite,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
