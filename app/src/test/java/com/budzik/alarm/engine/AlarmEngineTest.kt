package com.budzik.alarm.engine

import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.domain.generator.BatchAlarmGenerator
import com.budzik.alarm.domain.model.Difficulty
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class AlarmEngineTest {

    @Test
    fun test_calculateNextTriggerEpochMs_future_same_day() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseMs = cal.timeInMillis

        // Test time 09:15 on same day (ahead of 08:30)
        val triggerMs = AlarmSchedulerImpl.calculateNextTriggerEpochMs(9, 15, baseMs)

        val expected = Calendar.getInstance().apply {
            timeInMillis = baseMs
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals("Future time on same day should trigger today at 00 seconds", expected, triggerMs)
        assertTrue(triggerMs > baseMs)
    }

    @Test
    fun test_calculateNextTriggerEpochMs_past_rolls_to_next_day() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseMs = cal.timeInMillis

        // Test time 07:00 (already passed today)
        val triggerMs = AlarmSchedulerImpl.calculateNextTriggerEpochMs(7, 0, baseMs)

        val expected = Calendar.getInstance().apply {
            timeInMillis = baseMs
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1) // Tomorrow
        }.timeInMillis

        assertEquals("Past time should roll over to tomorrow at 00 seconds", expected, triggerMs)
        assertTrue(triggerMs > baseMs)
    }

    @Test
    fun test_alarmEntity_getEffectiveTriggerEpochMs() {
        val fixedNow = 1700000000000L

        // 1. When nextTriggerAt is in the future
        val futureEpoch = fixedNow + 5000L
        val alarmWithEpoch = AlarmEntity(
            hour = 6,
            minute = 0,
            isEnabled = true,
            daysOfWeekBitmask = 0,
            difficulty = "EASY",
            problemCount = 1,
            label = "Test 5s",
            nextTriggerAt = futureEpoch
        )
        assertEquals(futureEpoch, alarmWithEpoch.getEffectiveTriggerEpochMs(fixedNow))

        // 2. When nextTriggerAt is 0
        val alarmZeroEpoch = AlarmEntity(
            hour = 10,
            minute = 30,
            isEnabled = true,
            daysOfWeekBitmask = 0,
            difficulty = "EASY",
            problemCount = 1,
            label = "Standard Alarm",
            nextTriggerAt = 0L
        )
        val calculated = alarmZeroEpoch.getEffectiveTriggerEpochMs(fixedNow)
        assertTrue(calculated > fixedNow)
    }

    @Test
    fun test_quickNap_exact_epoch_calculation() {
        val now = System.currentTimeMillis()
        val napMinutes = 15
        val expectedTarget = now + (napMinutes * 60_000L)

        val cal = Calendar.getInstance().apply { timeInMillis = expectedTarget }
        val napAlarm = AlarmEntity(
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            isEnabled = true,
            daysOfWeekBitmask = 0,
            difficulty = Difficulty.EASY.name,
            problemCount = 1,
            label = "Drzemka (+15 min)",
            nextTriggerAt = expectedTarget
        )

        assertEquals("Nap alarm nextTriggerAt must equal target epoch timestamp", expectedTarget, napAlarm.nextTriggerAt)
        assertEquals(expectedTarget, napAlarm.getEffectiveTriggerEpochMs(now))
    }

    @Test
    fun test_diagnostic_test_series_timestamps() {
        val now = 1700000000000L
        val trigger1 = now + 5000L
        val trigger2 = now + 15000L

        val cal1 = Calendar.getInstance().apply { timeInMillis = trigger1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = trigger2 }

        val subAlarm1 = AlarmEntity(
            hour = cal1.get(Calendar.HOUR_OF_DAY),
            minute = cal1.get(Calendar.MINUTE),
            isEnabled = true,
            seriesId = "test-mini-series",
            seriesPosition = 0,
            seriesTotal = 2,
            difficulty = "EASY",
            problemCount = 1,
            label = "Test Seria #1",
            nextTriggerAt = trigger1
        )

        val subAlarm2 = AlarmEntity(
            hour = cal2.get(Calendar.HOUR_OF_DAY),
            minute = cal2.get(Calendar.MINUTE),
            isEnabled = true,
            seriesId = "test-mini-series",
            seriesPosition = 1,
            seriesTotal = 2,
            difficulty = "EASY",
            problemCount = 1,
            label = "Test Seria #2",
            nextTriggerAt = trigger2
        )

        assertEquals(trigger1, subAlarm1.getEffectiveTriggerEpochMs(now))
        assertEquals(trigger2, subAlarm2.getEffectiveTriggerEpochMs(now))
        assertEquals(10000L, trigger2 - trigger1)
    }

    @Test
    fun test_alarmEntity_getEffectiveTriggerEpochMs_when_now_greater_or_equal_trigger() {
        val targetEpoch = 1700000005000L // 5 seconds in future
        val alarm = AlarmEntity(
            hour = 6,
            minute = 0,
            isEnabled = true,
            daysOfWeekBitmask = 0,
            difficulty = "EASY",
            problemCount = 1,
            label = "Test 5s",
            nextTriggerAt = targetEpoch
        )

        // 1. Before trigger time
        val beforeMs = targetEpoch - 1000L
        assertEquals("Before trigger time: must return targetEpoch", targetEpoch, alarm.getEffectiveTriggerEpochMs(beforeMs))
        assertFalse("Should not trigger before target", beforeMs >= alarm.getEffectiveTriggerEpochMs(beforeMs))

        // 2. Exactly at trigger time
        val exactMs = targetEpoch
        assertEquals("Exactly at trigger time: MUST return targetEpoch (NOT tomorrow!)", targetEpoch, alarm.getEffectiveTriggerEpochMs(exactMs))
        assertTrue("Must trigger at exact trigger time", exactMs >= alarm.getEffectiveTriggerEpochMs(exactMs))

        // 3. After trigger time (e.g. 500ms or 20 seconds late due to device sleep)
        val afterMs = targetEpoch + 20_000L
        assertEquals("After trigger time: MUST return targetEpoch (NOT roll over to tomorrow before firing!)", targetEpoch, alarm.getEffectiveTriggerEpochMs(afterMs))
        assertTrue("Must trigger immediately even when waking up late", afterMs >= alarm.getEffectiveTriggerEpochMs(afterMs))
    }

    @Test
    fun test_calculateNextTriggerEpochMs_with_daysOfWeekBitmask() {
        // Monday 10:00:00 (Calendar.MONDAY = 2, bitIndex 0)
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val mon10am = cal.timeInMillis

        // Mon-Fri bitmask: 31 (bits 0,1,2,3,4)
        // 1. Time at 11:00 on Monday -> should trigger today at 11:00
        val triggerToday = AlarmSchedulerImpl.calculateNextTriggerEpochMs(11, 0, 31, mon10am)
        val expectedToday = Calendar.getInstance().apply {
            timeInMillis = mon10am
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expectedToday, triggerToday)

        // 2. Time at 09:00 on Monday (already passed) -> should trigger Tuesday at 09:00
        val triggerTue = AlarmSchedulerImpl.calculateNextTriggerEpochMs(9, 0, 31, mon10am)
        val expectedTue = Calendar.getInstance().apply {
            timeInMillis = mon10am
            add(Calendar.DAY_OF_YEAR, 1) // Tuesday
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expectedTue, triggerTue)

        // Weekend bitmask: 96 (bits 5 and 6 -> Sat, Sun)
        // From Monday 10:00 -> next is Saturday at 09:00
        val triggerSat = AlarmSchedulerImpl.calculateNextTriggerEpochMs(9, 0, 96, mon10am)
        val expectedSat = Calendar.getInstance().apply {
            timeInMillis = mon10am
            set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expectedSat, triggerSat)
    }
}
