package com.budzik.alarm.e2e

import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.domain.generator.BatchAlarmGenerator
import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.engine.WakeUpCheckReceiver
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class WakeUpCheckAndSeriesTest {

    @Test
    fun test_alarmEntity_default_values() {
        val alarm = AlarmEntity(
            hour = 6,
            minute = 30,
            isEnabled = true,
            daysOfWeekBitmask = 0,
            difficulty = "MEDIUM",
            problemCount = 3,
            label = "Morning"
        )
        assertFalse("wakeUpCheckEnabled should default to false", alarm.wakeUpCheckEnabled)
        assertEquals("wakeUpCheckMinutes should default to 5", 5, alarm.wakeUpCheckMinutes)
        assertNull("seriesId should default to null", alarm.seriesId)
        assertEquals("seriesPosition should default to 0", 0, alarm.seriesPosition)
        assertEquals("seriesTotal should default to 0", 0, alarm.seriesTotal)
    }

    @Test
    fun test_series_generation_tags_and_positions() {
        val seriesId = UUID.randomUUID().toString()
        val count = 5
        val intervalMinutes = 5
        val baseHour = 7
        val baseMinute = 0
        val baseEpochMs = 1700000000000L
        val difficulty = Difficulty.MEDIUM
        val wakeUpEnabled = true
        val wakeUpMinutes = 10

        val generated = BatchAlarmGenerator.INSTANCE.generateBatch(
            count = count,
            intervalMinutes = intervalMinutes,
            baseHour = baseHour,
            baseMinute = baseMinute,
            baseEpochMs = baseEpochMs,
            difficulty = difficulty
        )

        val entities = generated.mapIndexed { idx, ga ->
            AlarmEntity(
                hour = ga.hour,
                minute = ga.minute,
                isEnabled = true,
                daysOfWeekBitmask = 0,
                difficulty = difficulty.name,
                problemCount = 3,
                label = "Seria #${idx + 1}",
                wakeUpCheckEnabled = wakeUpEnabled,
                wakeUpCheckMinutes = wakeUpMinutes,
                seriesId = seriesId,
                seriesPosition = idx,
                seriesTotal = count
            )
        }

        assertEquals(5, entities.size)
        for (i in 0 until count) {
            assertEquals("Seria #${i + 1}", entities[i].label)
            assertEquals(seriesId, entities[i].seriesId)
            assertEquals(i, entities[i].seriesPosition)
            assertEquals(count, entities[i].seriesTotal)
            assertTrue(entities[i].wakeUpCheckEnabled)
            assertEquals(10, entities[i].wakeUpCheckMinutes)
        }
    }

    @Test
    fun test_wakeUpCheck_decision_single_alarm() {
        val singleWithCheck = AlarmEntity(
            id = 1L,
            hour = 7,
            minute = 0,
            isEnabled = true,
            difficulty = "EASY",
            problemCount = 2,
            label = "Single",
            wakeUpCheckEnabled = true,
            wakeUpCheckMinutes = 5
        )
        assertTrue("Single alarm with wakeUpCheckEnabled must schedule check", com.budzik.alarm.ui.RingingActivity.shouldScheduleWakeUpCheck(singleWithCheck, emptyList()))

        val singleWithoutCheck = singleWithCheck.copy(wakeUpCheckEnabled = false)
        assertFalse("Single alarm with wakeUpCheckEnabled = false must NOT schedule check", com.budzik.alarm.ui.RingingActivity.shouldScheduleWakeUpCheck(singleWithoutCheck, emptyList()))
    }

    @Test
    fun test_wakeUpCheck_decision_series_only_on_last_alarm() {
        val sId = "series-test-123"
        val alarmsInSeries = listOf(
            AlarmEntity(id = 10L, hour = 6, minute = 0, isEnabled = true, difficulty = "EASY", problemCount = 1, label = "S1", wakeUpCheckEnabled = true, seriesId = sId, seriesPosition = 0, seriesTotal = 3),
            AlarmEntity(id = 11L, hour = 6, minute = 5, isEnabled = true, difficulty = "EASY", problemCount = 1, label = "S2", wakeUpCheckEnabled = true, seriesId = sId, seriesPosition = 1, seriesTotal = 3),
            AlarmEntity(id = 12L, hour = 6, minute = 10, isEnabled = true, difficulty = "EASY", problemCount = 1, label = "S3", wakeUpCheckEnabled = true, seriesId = sId, seriesPosition = 2, seriesTotal = 3)
        )

        // First alarm (pos 0) completes: later alarms exist (pos 1, 2)
        assertFalse("Alarm 1 in series must NOT schedule wake up check", com.budzik.alarm.ui.RingingActivity.shouldScheduleWakeUpCheck(alarmsInSeries[0], alarmsInSeries))

        // Second alarm (pos 1) completes: later alarm exists (pos 2)
        assertFalse("Alarm 2 in series must NOT schedule wake up check", com.budzik.alarm.ui.RingingActivity.shouldScheduleWakeUpCheck(alarmsInSeries[1], alarmsInSeries))

        // Third/last alarm (pos 2) completes: no later alarms
        assertTrue("Last alarm in series MUST schedule wake up check", com.budzik.alarm.ui.RingingActivity.shouldScheduleWakeUpCheck(alarmsInSeries[2], alarmsInSeries))
    }

    @Test
    fun test_wakeUpCheck_decision_series_with_disabled_later_alarms() {
        val sId = "series-test-456"
        val alarmsInSeries = listOf(
            AlarmEntity(id = 20L, hour = 7, minute = 0, isEnabled = true, difficulty = "MEDIUM", problemCount = 2, label = "S1", wakeUpCheckEnabled = true, seriesId = sId, seriesPosition = 0, seriesTotal = 3),
            AlarmEntity(id = 21L, hour = 7, minute = 5, isEnabled = true, difficulty = "MEDIUM", problemCount = 2, label = "S2", wakeUpCheckEnabled = true, seriesId = sId, seriesPosition = 1, seriesTotal = 3),
            AlarmEntity(id = 22L, hour = 7, minute = 10, isEnabled = false, difficulty = "MEDIUM", problemCount = 2, label = "S3", wakeUpCheckEnabled = true, seriesId = sId, seriesPosition = 2, seriesTotal = 3) // Disabled!
        )

        // pos 1 completes: pos 2 is disabled, so pos 1 is effectively the last active alarm
        assertTrue("Active alarm must schedule check if all subsequent alarms are disabled", com.budzik.alarm.ui.RingingActivity.shouldScheduleWakeUpCheck(alarmsInSeries[1], alarmsInSeries))
    }

    @Test
    fun test_wakeUpCheck_action_constants() {
        assertEquals("com.budzik.alarm.ACTION_TRIGGER_WAKE_UP_CHECK", WakeUpCheckReceiver.ACTION_TRIGGER_CHECK)
        assertEquals("com.budzik.alarm.ACTION_CONFIRM_WAKE_UP", WakeUpCheckReceiver.ACTION_CONFIRM_AWAKE)
        assertEquals("com.budzik.alarm.ACTION_WAKE_UP_TIMEOUT", WakeUpCheckReceiver.ACTION_CHECK_TIMEOUT)
    }
}
