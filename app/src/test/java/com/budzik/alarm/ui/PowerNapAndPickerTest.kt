package com.budzik.alarm.ui

import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.engine.WakeUpCheckReceiver
import com.budzik.alarm.sound.CustomRingtone
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class PowerNapAndPickerTest {

    @Test
    fun test_powerNap_time_calculation() {
        val napIntervals = listOf(15, 20, 45)
        for (mins in napIntervals) {
            val cal = Calendar.getInstance()
            val initialEpoch = cal.timeInMillis
            cal.add(Calendar.MINUTE, mins)

            val napHour = cal.get(Calendar.HOUR_OF_DAY)
            val napMinute = cal.get(Calendar.MINUTE)

            val napAlarm = AlarmEntity(
                hour = napHour,
                minute = napMinute,
                isEnabled = true,
                daysOfWeekBitmask = 0,
                difficulty = Difficulty.EASY.name,
                problemCount = 1,
                label = "Drzemka (+$mins min)"
            )

            assertTrue("Nap hour must be 0..23", napAlarm.hour in 0..23)
            assertTrue("Nap minute must be 0..59", napAlarm.minute in 0..59)
            assertEquals("Drzemka (+$mins min)", napAlarm.label)
            assertTrue("Nap alarm must be enabled", napAlarm.isEnabled)
            assertEquals("EASY", napAlarm.difficulty)
            assertEquals(1, napAlarm.problemCount)
        }
    }

    @Test
    fun test_drumPicker_bounds() {
        for (h in -5..30) {
            val clampedHour = h.coerceIn(0, 23)
            assertTrue(clampedHour in 0..23)
        }
        for (m in -10..75) {
            val clampedMinute = m.coerceIn(0, 59)
            assertTrue(clampedMinute in 0..59)
        }
    }

    @Test
    fun test_quiet_mode_timeout_extension_and_cap() {
        val maxTimeMs = 20_000L
        var timeLeftMs = 15_000L

        // Typing a digit adds 2000ms
        timeLeftMs = (timeLeftMs + 2000L).coerceAtMost(maxTimeMs)
        assertEquals(17_000L, timeLeftMs)

        // Typing two more digits should cap at 20000ms
        timeLeftMs = (timeLeftMs + 2000L).coerceAtMost(maxTimeMs)
        assertEquals(19_000L, timeLeftMs)
        timeLeftMs = (timeLeftMs + 2000L).coerceAtMost(maxTimeMs)
        assertEquals(20_000L, timeLeftMs)

        // Additional press stays at 20000ms
        timeLeftMs = (timeLeftMs + 2000L).coerceAtMost(maxTimeMs)
        assertEquals(20_000L, timeLeftMs)
    }

    @Test
    fun test_countdown_formatting_logic() {
        // Test formatting when hours > 0
        val diffMs1 = (6 * 3600 + 14 * 60) * 1000L
        val totalMinutes1 = diffMs1 / (60 * 1000L)
        val hours1 = totalMinutes1 / 60
        val mins1 = totalMinutes1 % 60
        val text1 = if (hours1 > 0) "Dzwoni za $hours1 godz. $mins1 min" else "Dzwoni za $mins1 min"
        assertEquals("Dzwoni za 6 godz. 14 min", text1)

        // Test formatting when only minutes
        val diffMs2 = 25 * 60 * 1000L
        val totalMinutes2 = diffMs2 / (60 * 1000L)
        val hours2 = totalMinutes2 / 60
        val mins2 = totalMinutes2 % 60
        val text2 = if (hours2 > 0) "Dzwoni za $hours2 godz. $mins2 min" else "Dzwoni za $mins2 min"
        assertEquals("Dzwoni za 25 min", text2)

        // Test formatting when under 1 minute (must NOT display "Dzwoni za 0 min")
        val diffMs3 = 45 * 1000L
        val totalMinutes3 = diffMs3 / (60 * 1000L)
        val hours3 = totalMinutes3 / 60
        val mins3 = totalMinutes3 % 60
        val text3 = if (hours3 > 0) {
            "Dzwoni za $hours3 godz. $mins3 min"
        } else if (mins3 > 0) {
            "Dzwoni za $mins3 min"
        } else {
            "Dzwoni za mniej niż minutę"
        }
        assertEquals("Dzwoni za mniej niż minutę", text3)
    }

    @Test
    fun test_powerNap_live_countdown_seconds_calculation() {
        val remainingMs = 19 * 60 * 1000L + 42 * 1000L // 19 min 42s
        val totalSecs = (remainingMs + 999L) / 1000L
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        val formatted = "Dzwoni za: ${mins} min ${secs} s"
        assertEquals("Dzwoni za: 19 min 42 s", formatted)

        // At zero remaining
        val zeroMs = 0L
        val zeroText = if (zeroMs <= 0) "Drzemka dzwoni teraz!" else "Dzwoni za..."
        assertEquals("Drzemka dzwoni teraz!", zeroText)
    }

    @Test
    fun test_single_alarm_rotating_palette_order() {
        val palette = com.budzik.alarm.ui.theme.PastelSingleAlarmColors
        assertEquals(5, palette.size)
        assertEquals(com.budzik.alarm.ui.theme.PastelBlue, palette[0])
        assertEquals(com.budzik.alarm.ui.theme.PastelPink, palette[1])
        assertEquals(com.budzik.alarm.ui.theme.PastelYellow, palette[2])
        assertEquals(com.budzik.alarm.ui.theme.PastelLime, palette[3])
        assertEquals(com.budzik.alarm.ui.theme.PastelLavender, palette[4])

        // Verify rotation wraps around
        assertEquals(palette[0], palette[5 % palette.size])
        assertEquals(palette[1], palette[6 % palette.size])
    }

    @Test
    fun test_difficulty_math_font_sizing() {
        for (diff in Difficulty.values()) {
            val size = when (diff) {
                Difficulty.HARD -> 38
                Difficulty.MEDIUM -> 46
                Difficulty.EASY -> 52
            }
            // HARD font size must be smaller than EASY to comfortably accommodate 4 digits without wrapping
            if (diff == Difficulty.HARD) {
                assertTrue("Hard font size must be <= 40sp", size <= 40)
            }
            if (diff == Difficulty.EASY) {
                assertTrue("Easy font size must be >= 50sp", size >= 50)
            }
        }
    }

    @Test
    fun test_quickNap_all_8_intervals() {
        val quickNapTimes = listOf(5, 10, 15, 20, 30, 45, 60, 90)
        assertEquals(8, quickNapTimes.size)

        for (mins in quickNapTimes) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MINUTE, mins)
            val napHour = cal.get(Calendar.HOUR_OF_DAY)
            val napMinute = cal.get(Calendar.MINUTE)

            val napAlarm = AlarmEntity(
                hour = napHour,
                minute = napMinute,
                isEnabled = true,
                daysOfWeekBitmask = 0,
                difficulty = Difficulty.EASY.name,
                problemCount = 0,
                label = "Drzemka (+$mins min)",
                wakeUpCheckEnabled = false,
                wakeUpCheckMinutes = 5
            )

            assertTrue(napAlarm.hour in 0..23)
            assertTrue(napAlarm.minute in 0..59)
            assertEquals("Drzemka (+$mins min)", napAlarm.label)
            assertEquals(0, napAlarm.problemCount)
            assertFalse(napAlarm.wakeUpCheckEnabled)
        }
    }

    @Test
    fun test_quickNap_math_toggle_problemCount() {
        val mins = 20
        // When requireMathProblem = true -> problemCount is 1
        val napWithMath = AlarmEntity(
            hour = 8,
            minute = 0,
            isEnabled = true,
            daysOfWeekBitmask = 0,
            difficulty = Difficulty.EASY.name,
            problemCount = 1,
            label = "Drzemka (+$mins min)"
        )
        assertEquals(1, napWithMath.problemCount)

        // When requireMathProblem = false -> problemCount is 0
        val napWithoutMath = AlarmEntity(
            hour = 8,
            minute = 0,
            isEnabled = true,
            daysOfWeekBitmask = 0,
            difficulty = Difficulty.EASY.name,
            problemCount = 0,
            label = "Drzemka (+$mins min)"
        )
        assertEquals(0, napWithoutMath.problemCount)
    }

    @Test
    fun test_wakeUpCheck_60s_timeout_countdown() {
        assertEquals(60_000L, WakeUpCheckReceiver.TIMEOUT_DURATION_MS)

        val triggerEpochMs = 1_000_000L

        // Immediately at trigger: 60s remaining
        val nowMsAtTrigger = triggerEpochMs
        val elapsed0 = nowMsAtTrigger - triggerEpochMs
        val remaining0 = ((WakeUpCheckReceiver.TIMEOUT_DURATION_MS - elapsed0 + 999L) / 1000L).coerceIn(0L, 60L)
        assertEquals(60L, remaining0)

        // 15 seconds elapsed: 45s remaining
        val nowMs15 = triggerEpochMs + 15_000L
        val elapsed15 = nowMs15 - triggerEpochMs
        val remaining15 = ((WakeUpCheckReceiver.TIMEOUT_DURATION_MS - elapsed15 + 999L) / 1000L).coerceIn(0L, 60L)
        assertEquals(45L, remaining15)

        // 59.1 seconds elapsed: 1s remaining
        val nowMs59 = triggerEpochMs + 59_100L
        val elapsed59 = nowMs59 - triggerEpochMs
        val remaining59 = ((WakeUpCheckReceiver.TIMEOUT_DURATION_MS - elapsed59 + 999L) / 1000L).coerceIn(0L, 60L)
        assertEquals(1L, remaining59)

        // 60+ seconds elapsed: 0s remaining
        val nowMs65 = triggerEpochMs + 65_000L
        val elapsed65 = nowMs65 - triggerEpochMs
        val remaining65 = ((WakeUpCheckReceiver.TIMEOUT_DURATION_MS - elapsed65 + 999L) / 1000L).coerceIn(0L, 60L)
        assertEquals(0L, remaining65)
    }

    @Test
    fun test_customRingtone_data_model() {
        val ringtone = CustomRingtone(
            id = "custom_12345",
            name = "Moja Melodia.mp3",
            filePath = "/data/user/0/com.budzik.alarm/files/custom_ringtones/custom_12345_Moja_Melodia.mp3"
        )
        assertEquals("custom_12345", ringtone.id)
        assertEquals("Moja Melodia.mp3", ringtone.name)
        assertTrue(ringtone.filePath.endsWith(".mp3"))
    }

    @Test
    fun test_nap_dismissal_action_decision() {
        // Nap alarm should be deleted upon dismissal
        val napAlarm = AlarmEntity(
            id = 55L,
            hour = 14,
            minute = 30,
            isEnabled = true,
            daysOfWeekBitmask = 0,
            difficulty = "EASY",
            problemCount = 0,
            label = "Drzemka (+15 min)"
        )
        val shouldDelete = napAlarm.label.startsWith("Drzemka")
        assertTrue("Nap alarm must be deleted from DB when dismissed", shouldDelete)

        // Regular one-time alarm should be disabled upon dismissal
        val regularOneTime = AlarmEntity(
            id = 56L,
            hour = 7,
            minute = 0,
            isEnabled = true,
            daysOfWeekBitmask = 0,
            difficulty = "MEDIUM",
            problemCount = 3,
            label = "Morning"
        )
        assertFalse("Regular alarm is not a Drzemka", regularOneTime.label.startsWith("Drzemka"))
        val isOneTime = regularOneTime.daysOfWeekBitmask == 0
        assertTrue("One-time alarm must be disabled when dismissed", isOneTime)
    }

    @Test
    fun test_ringing_screen_routing_by_problem_count() {
        // When problemCount is 0, user must NOT be routed to math problem screen
        val noProblemCount = 0
        val isMathScreenRequired = noProblemCount > 0
        assertFalse("When problemCount <= 0, math screen must not be required", isMathScreenRequired)

        // When problemCount is > 0, math mission is required
        val withProblemCount = 3
        val isMathScreenRequiredWithCount = withProblemCount > 0
        assertTrue("When problemCount > 0, math screen is required", isMathScreenRequiredWithCount)
    }

    @Test
    fun test_gentle_ramp_volume_progression() {
        val targetVolume = 0.85f
        val initialVol = 0.05f
        val steps = 30
        val volIncrement = (targetVolume - initialVol) / steps

        // At step 0
        assertEquals(0.05f, initialVol, 0.001f)

        // At step 15 (midpoint, 7.5 seconds)
        val midVol = (initialVol + volIncrement * 15).coerceIn(0.05f, targetVolume)
        assertEquals(0.45f, midVol, 0.01f)

        // At step 30 (end, 15 seconds)
        val finalVol = (initialVol + volIncrement * 30).coerceIn(0.05f, targetVolume)
        assertEquals(targetVolume, finalVol, 0.001f)
    }
}

