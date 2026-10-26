package com.budzik.alarm.engine

import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.domain.model.AlarmType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RealignTriggerTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Warsaw"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    private fun alarm(hour: Int, minute: Int, nextTriggerAt: Long, type: AlarmType = AlarmType.SINGLE) = AlarmEntity(
        id = 1,
        hour = hour,
        minute = minute,
        isEnabled = true,
        difficulty = "EASY",
        problemCount = 1,
        label = "Alarm",
        nextTriggerAt = nextTriggerAt,
        type = type.name
    )

    private fun localHourMinute(epochMs: Long): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        return cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
    }

    @Test
    fun timezone_change_moves_alarm_to_same_local_time() {
        val now = System.currentTimeMillis()
        val trigger = AlarmSchedulerImpl.calculateNextTriggerEpochMs(7, 0, now)
        val stored = alarm(7, 0, trigger)

        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
        val realigned = AlarmSchedulerImpl.realignTriggerToWallClock(stored, now)

        assertEquals(7 to 0, localHourMinute(realigned))
    }

    @Test
    fun unchanged_timezone_keeps_trigger() {
        val now = System.currentTimeMillis()
        val trigger = AlarmSchedulerImpl.calculateNextTriggerEpochMs(7, 0, now)
        assertEquals(trigger, AlarmSchedulerImpl.realignTriggerToWallClock(alarm(7, 0, trigger), now))
    }

    @Test
    fun nap_keeps_relative_epoch() {
        val now = System.currentTimeMillis()
        val napTrigger = now + 20 * 60_000L
        val nap = alarm(0, 0, napTrigger, type = AlarmType.NAP)

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals(napTrigger, AlarmSchedulerImpl.realignTriggerToWallClock(nap, now))
    }
}
