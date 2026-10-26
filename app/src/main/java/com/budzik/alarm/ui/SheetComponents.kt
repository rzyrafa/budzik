package com.budzik.alarm.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.ui.theme.ContrastBlack
import com.budzik.alarm.ui.theme.ContrastWhite
import com.budzik.alarm.ui.theme.DarkPillBg
import com.budzik.alarm.ui.theme.PastelBlue
import com.budzik.alarm.ui.theme.PastelLime
import com.budzik.alarm.ui.theme.PastelPink
import com.budzik.alarm.ui.theme.bouncyPress

// =========================================================================
// BOTTOM SHEET WRAPPER & DISMISS BUTTON
// =========================================================================

@Composable
fun BottomSheetWrapper(
    title: String,
    tabColor: Color = PastelPink,
    closeButtonColor: Color = PastelPink,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val animatedTabColor by animateColorAsState(
        targetValue = tabColor,
        animationSpec = tween(
            durationMillis = 350,
            easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
        ),
        label = "bottom_sheet_tab_color"
    )

    // Up to 94% of the screen height, so tall forms (series) open high instead of scrolling inside
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.94f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp)
    ) {
        // Protruding Pastel Tab
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-40).dp),
            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
            color = animatedTabColor,
            border = null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 26.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ContrastBlack,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Deep Black Content Sheet
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight),
            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
            color = ContrastBlack,
            border = null,
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 6.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    content()
                }

                // Compact "X" right under the content (no tall empty footer)
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(closeButtonColor)
                        .bouncyPress()
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Zamknij",
                        modifier = Modifier.size(22.dp),
                        tint = ContrastBlack
                    )
                }
            }
        }
    }
}

// =========================================================================
// SLIDING MODE TOGGLE (COLOR MORPHING: PINK -> LIME)
// =========================================================================

@Composable
fun SlidingModeToggle(
    isSeriesMode: Boolean,
    onModeChanged: (Boolean) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(DarkPillBg, RoundedCornerShape(percent = 50))
            .padding(4.dp)
    ) {
        val pillWidth = maxWidth / 2

        val slideProgress by animateFloatAsState(
            targetValue = if (isSeriesMode) 1f else 0f,
            animationSpec = tween(
                durationMillis = 350,
                easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
            ),
            label = "mode_toggle_slide"
        )

        // Pill color smoothly changes from pastel pink (#F4B8E4) to pastel green (#CBE896)
        val pillColor by animateColorAsState(
            targetValue = if (isSeriesMode) PastelLime else PastelPink,
            animationSpec = tween(
                durationMillis = 350,
                easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
            ),
            label = "mode_toggle_color"
        )

        Box(
            modifier = Modifier
                .offset(x = pillWidth * slideProgress)
                .width(pillWidth)
                .fillMaxHeight()
                .background(pillColor, RoundedCornerShape(percent = 50))
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .bouncyPress()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onModeChanged(false) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Pojedynczy alarm",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (!isSeriesMode) ContrastBlack else ContrastWhite
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .bouncyPress()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onModeChanged(true) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Seria alarmów",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSeriesMode) ContrastBlack else ContrastWhite
                )
            }
        }
    }
}

// =========================================================================
// DIFFICULTY SLIDING SELECTOR (PASTEL BLUE #A8D5FC + BLACK TEXT + SMOOTH SLIDE)
// =========================================================================

@Composable
fun DifficultySlidingSelector(
    selectedDifficulty: Difficulty,
    onDifficultySelected: (Difficulty) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(DarkPillBg, RoundedCornerShape(percent = 50))
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / 3
        val targetIndex = when (selectedDifficulty) {
            Difficulty.EASY -> 0
            Difficulty.MEDIUM -> 1
            Difficulty.HARD -> 2
        }

        val slideOffset by animateFloatAsState(
            targetValue = targetIndex.toFloat(),
            animationSpec = tween(
                durationMillis = 320,
                easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
            ),
            label = "difficulty_slide"
        )

        // Vibrant Pastel Blue (#A8D5FC) sliding pill with black text (#0D0D0D)
        Box(
            modifier = Modifier
                .offset(x = segmentWidth * slideOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .background(PastelBlue, RoundedCornerShape(percent = 50))
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Difficulty.EASY to "Łatwy",
                Difficulty.MEDIUM to "Średni",
                Difficulty.HARD to "Trudny"
            ).forEach { (diff, label) ->
                val isSelected = selectedDifficulty == diff
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) ContrastBlack else ContrastWhite,
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
                    ),
                    label = "difficulty_text_color"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .bouncyPress()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onDifficultySelected(diff) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }
    }
}

// =========================================================================
// PILL OPTION CHIP
// =========================================================================

@Composable
fun PillOptionChip(
    text: String,
    isSelected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = if (isSelected) accentColor else DarkPillBg,
        border = null,
        interactionSource = interactionSource,
        modifier = modifier.bouncyPress(scaleTarget = 0.94f, interactionSource = interactionSource)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) ContrastBlack else ContrastWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
        )
    }
}
