package com.budzik.alarm.domain

import com.budzik.alarm.domain.generator.BatchAlarmGenerator
import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.domain.model.GeneratedAlarm
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive Unit Test Suite for [BatchAlarmGenerator].
 *
 * Verifies domain logic engine requirements:
 * - Requirement R2: Multi-Alarm Generator & interval calculation
 * - Feature 7: Sequential alarm generation for N alarms at interval M minutes with exact timestamps
 * - Feature 8: Midnight Rollover Calculation past 23:59 without past-timestamp scheduling errors
 * - Acceptance Criteria: Automated tests verify BatchAlarmGenerator generates expected count
 *   with exact calculated timestamps spaced by the specified interval.
 */
class BatchAlarmGeneratorTest {

    private lateinit var generator: BatchAlarmGenerator

    @Before
    fun setUp() {
        generator = BatchAlarmGenerator()
    }

    // =========================================================================
    // 1. Standard Batch Generation & Count Verification
    // =========================================================================

    @Test
    fun testGenerateBatch_exactCountGenerated() {
        val testCounts = listOf(1, 2, 5, 10, 25, 50)
        for (count in testCounts) {
            val alarms = generator.generateBatch(
                count = count,
                intervalMinutes = 5,
                baseHour = 7,
                baseMinute = 0,
                baseEpochMs = 1700000000000L,
                difficulty = Difficulty.EASY
            )
            assertEquals("Batch size must exactly equal requested count $count", count, alarms.size)
        }
    }

    @Test
    fun testGenerateBatch_singleAlarmPreservesBaseParameters() {
        val baseHour = 14
        val baseMinute = 25
        val baseEpochMs = 1700000000000L
        val difficulty = Difficulty.HARD

        val alarms = generator.generateBatch(
            count = 1,
            intervalMinutes = 15,
            baseHour = baseHour,
            baseMinute = baseMinute,
            baseEpochMs = baseEpochMs,
            difficulty = difficulty
        )

        assertEquals(1, alarms.size)
        val single = alarms[0]
        assertEquals(baseHour, single.hour)
        assertEquals(baseMinute, single.minute)
        assertEquals(baseEpochMs, single.triggerEpochMs)
        assertEquals(difficulty, single.difficulty)
    }

    // =========================================================================
    // 2. Interval Spacing & Exact Epoch Timestamps
    // =========================================================================

    @Test
    fun testGenerateBatch_exactIntervalTimestampSpacing() {
        val baseEpochMs = 1700000000000L
        val intervalMinutes = 7
        val intervalMs = 7 * 60_000L
        val count = 8

        val alarms = generator.generateBatch(
            count = count,
            intervalMinutes = intervalMinutes,
            baseHour = 8,
            baseMinute = 0,
            baseEpochMs = baseEpochMs,
            difficulty = Difficulty.MEDIUM
        )

        for (i in 0 until count) {
            val expectedEpochMs = baseEpochMs + (i * intervalMs)
            assertEquals(
                "Alarm index $i trigger timestamp must match base + (i * intervalMs)",
                expectedEpochMs,
                alarms[i].triggerEpochMs
            )
        }
    }

    @Test
    fun testGenerateBatch_strictlyMonotonicEpochTimestamps() {
        val count = 20
        val alarms = generator.generateBatch(
            count = count,
            intervalMinutes = 10,
            baseHour = 22,
            baseMinute = 30,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.EASY
        )

        for (i in 1 until count) {
            assertTrue(
                "Consecutive alarms must have strictly increasing epoch timestamps",
                alarms[i].triggerEpochMs > alarms[i - 1].triggerEpochMs
            )
            assertEquals(
                "Consecutive alarms must differ by exactly interval duration",
                10 * 60_000L,
                alarms[i].triggerEpochMs - alarms[i - 1].triggerEpochMs
            )
        }
    }

    // =========================================================================
    // 3. Hour & Minute Progression
    // =========================================================================

    @Test
    fun testGenerateBatch_minuteProgressionWithinSameHour() {
        val alarms = generator.generateBatch(
            count = 4,
            intervalMinutes = 10,
            baseHour = 9,
            baseMinute = 0,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.EASY
        )

        assertEquals(9, alarms[0].hour); assertEquals(0, alarms[0].minute)
        assertEquals(9, alarms[1].hour); assertEquals(10, alarms[1].minute)
        assertEquals(9, alarms[2].hour); assertEquals(20, alarms[2].minute)
        assertEquals(9, alarms[3].hour); assertEquals(30, alarms[3].minute)
    }

    @Test
    fun testGenerateBatch_hourProgressionAcrossDaytimeHourBoundary() {
        val alarms = generator.generateBatch(
            count = 4,
            intervalMinutes = 20,
            baseHour = 9,
            baseMinute = 40,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.MEDIUM
        )

        assertEquals(9, alarms[0].hour); assertEquals(40, alarms[0].minute)
        assertEquals(10, alarms[1].hour); assertEquals(0, alarms[1].minute)
        assertEquals(10, alarms[2].hour); assertEquals(20, alarms[2].minute)
        assertEquals(10, alarms[3].hour); assertEquals(40, alarms[3].minute)
    }

    // =========================================================================
    // 4. Midnight Rollover (Feature 8)
    // =========================================================================

    @Test
    fun testGenerateBatch_midnightRollover_simpleCrossing() {
        // 23:55, count 3, interval 5 min -> 23:55, 00:00, 00:05
        val alarms = generator.generateBatch(
            count = 3,
            intervalMinutes = 5,
            baseHour = 23,
            baseMinute = 55,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.EASY
        )

        assertEquals(3, alarms.size)
        assertEquals(23, alarms[0].hour); assertEquals(55, alarms[0].minute)
        assertEquals(0, alarms[1].hour); assertEquals(0, alarms[1].minute)
        assertEquals(0, alarms[2].hour); assertEquals(5, alarms[2].minute)
    }

    @Test
    fun testGenerateBatch_midnightRollover_exact2359Boundary() {
        // 23:59, count 2, interval 1 min -> 23:59, 00:00
        val alarms = generator.generateBatch(
            count = 2,
            intervalMinutes = 1,
            baseHour = 23,
            baseMinute = 59,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.EASY
        )

        assertEquals(2, alarms.size)
        assertEquals(23, alarms[0].hour); assertEquals(59, alarms[0].minute)
        assertEquals(0, alarms[1].hour); assertEquals(0, alarms[1].minute)
    }

    @Test
    fun testGenerateBatch_midnightRollover_multiHourProgression() {
        // 23:40, count 5, interval 30 min -> 23:40, 00:10, 00:40, 01:10, 01:40
        val alarms = generator.generateBatch(
            count = 5,
            intervalMinutes = 30,
            baseHour = 23,
            baseMinute = 40,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.MEDIUM
        )

        assertEquals(5, alarms.size)
        assertEquals(23, alarms[0].hour); assertEquals(40, alarms[0].minute)
        assertEquals(0, alarms[1].hour); assertEquals(10, alarms[1].minute)
        assertEquals(0, alarms[2].hour); assertEquals(40, alarms[2].minute)
        assertEquals(1, alarms[3].hour); assertEquals(10, alarms[3].minute)
        assertEquals(1, alarms[4].hour); assertEquals(40, alarms[4].minute)
    }

    @Test
    fun testGenerateBatch_midnightRollover_monotonicEpochAcrossMidnight() {
        val baseEpoch = 1700000000000L
        val alarms = generator.generateBatch(
            count = 6,
            intervalMinutes = 20,
            baseHour = 23,
            baseMinute = 0,
            baseEpochMs = baseEpoch,
            difficulty = Difficulty.HARD
        )

        for (i in 1 until alarms.size) {
            assertTrue(
                "Trigger epoch ms must increase across midnight rollover",
                alarms[i].triggerEpochMs > alarms[i - 1].triggerEpochMs
            )
        }
    }

    @Test
    fun testGenerateBatch_multiDaySpanningRollover() {
        // 20 alarms spaced by 120 minutes (2 hours) starting at 22:00 -> spans over 38 hours
        val alarms = generator.generateBatch(
            count = 20,
            intervalMinutes = 120,
            baseHour = 22,
            baseMinute = 0,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.HARD
        )

        assertEquals(20, alarms.size)
        for (alarm in alarms) {
            assertTrue("Hour must always remain in 0..23", alarm.hour in 0..23)
            assertTrue("Minute must always remain in 0..59", alarm.minute in 0..59)
        }

        // Verify specific multi-day wraps
        assertEquals(22, alarms[0].hour) // Day 1 22:00
        assertEquals(0, alarms[1].hour)  // Day 2 00:00
        assertEquals(2, alarms[2].hour)  // Day 2 02:00
        assertEquals(22, alarms[12].hour) // Day 2 22:00 (24h later)
        assertEquals(0, alarms[13].hour)  // Day 3 00:00
    }

    // =========================================================================
    // 5. Difficulty Propagation
    // =========================================================================

    @Test
    fun testGenerateBatch_difficultyPropagatedToAllAlarms() {
        for (diff in Difficulty.values()) {
            val alarms = generator.generateBatch(
                count = 4,
                intervalMinutes = 5,
                baseHour = 6,
                baseMinute = 30,
                baseEpochMs = 1700000000000L,
                difficulty = diff
            )

            assertTrue(
                "All generated alarms must have difficulty $diff",
                alarms.all { it.difficulty == diff }
            )
        }
    }

    // =========================================================================
    // 6. Parameter Validation & Defensive Error Handling
    // =========================================================================

    @Test
    fun testGenerateBatch_zeroCount_throwsIllegalArgumentException() {
        try {
            generator.generateBatch(
                count = 0,
                intervalMinutes = 5,
                baseHour = 8,
                baseMinute = 0,
                baseEpochMs = 1700000000000L,
                difficulty = Difficulty.EASY
            )
            fail("Count = 0 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Error message must mention Count",
                e.message?.contains("Count", ignoreCase = true) == true
            )
        }
    }

    @Test
    fun testGenerateBatch_negativeCount_throwsIllegalArgumentException() {
        try {
            generator.generateBatch(
                count = -5,
                intervalMinutes = 5,
                baseHour = 8,
                baseMinute = 0,
                baseEpochMs = 1700000000000L,
                difficulty = Difficulty.EASY
            )
            fail("Negative count must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Success
        }
    }

    @Test
    fun testGenerateBatch_zeroInterval_throwsIllegalArgumentException() {
        try {
            generator.generateBatch(
                count = 5,
                intervalMinutes = 0,
                baseHour = 8,
                baseMinute = 0,
                baseEpochMs = 1700000000000L,
                difficulty = Difficulty.EASY
            )
            fail("Interval = 0 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Error message must mention Interval",
                e.message?.contains("Interval", ignoreCase = true) == true
            )
        }
    }

    @Test
    fun testGenerateBatch_negativeInterval_throwsIllegalArgumentException() {
        try {
            generator.generateBatch(
                count = 5,
                intervalMinutes = -10,
                baseHour = 8,
                baseMinute = 0,
                baseEpochMs = 1700000000000L,
                difficulty = Difficulty.EASY
            )
            fail("Negative interval must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Success
        }
    }

    @Test
    fun testGenerateBatch_invalidBaseHour_throwsIllegalArgumentException() {
        // Hour = 24
        try {
            generator.generateBatch(
                count = 2,
                intervalMinutes = 5,
                baseHour = 24,
                baseMinute = 0,
                baseEpochMs = 1700000000000L,
                difficulty = Difficulty.EASY
            )
            fail("Hour = 24 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Success
        }

        // Hour = -1
        try {
            generator.generateBatch(
                count = 2,
                intervalMinutes = 5,
                baseHour = -1,
                baseMinute = 0,
                baseEpochMs = 1700000000000L,
                difficulty = Difficulty.EASY
            )
            fail("Hour = -1 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Success
        }
    }

    @Test
    fun testGenerateBatch_invalidBaseMinute_throwsIllegalArgumentException() {
        // Minute = 60
        try {
            generator.generateBatch(
                count = 2,
                intervalMinutes = 5,
                baseHour = 12,
                baseMinute = 60,
                baseEpochMs = 1700000000000L,
                difficulty = Difficulty.EASY
            )
            fail("Minute = 60 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Success
        }

        // Minute = -1
        try {
            generator.generateBatch(
                count = 2,
                intervalMinutes = 5,
                baseHour = 12,
                baseMinute = -1,
                baseEpochMs = 1700000000000L,
                difficulty = Difficulty.EASY
            )
            fail("Minute = -1 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Success
        }
    }

    // =========================================================================
    // 7. Extremes, Boundaries & Performance
    // =========================================================================

    @Test
    fun testGenerateBatch_boundaryMinTime_0000() {
        val alarms = generator.generateBatch(
            count = 3,
            intervalMinutes = 1,
            baseHour = 0,
            baseMinute = 0,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.EASY
        )
        assertEquals(0, alarms[0].hour); assertEquals(0, alarms[0].minute)
        assertEquals(0, alarms[1].hour); assertEquals(1, alarms[1].minute)
        assertEquals(0, alarms[2].hour); assertEquals(2, alarms[2].minute)
    }

    @Test
    fun testGenerateBatch_largeCount_60AlarmsSpanFullHour() {
        val alarms = generator.generateBatch(
            count = 60,
            intervalMinutes = 1,
            baseHour = 10,
            baseMinute = 0,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.EASY
        )
        assertEquals(60, alarms.size)
        assertEquals(10, alarms[0].hour); assertEquals(0, alarms[0].minute)
        assertEquals(10, alarms[59].hour); assertEquals(59, alarms[59].minute)
    }

    @Test
    fun testGenerateBatch_companionSingletonDelegation() {
        val count = 3
        val fromInstance = generator.generateBatch(
            count = count,
            intervalMinutes = 5,
            baseHour = 7,
            baseMinute = 30,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.MEDIUM
        )

        val fromCompanion = BatchAlarmGenerator.generateBatch(
            count = count,
            intervalMinutes = 5,
            baseHour = 7,
            baseMinute = 30,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.MEDIUM
        )

        val fromStaticField = BatchAlarmGenerator.INSTANCE.generateBatch(
            count = count,
            intervalMinutes = 5,
            baseHour = 7,
            baseMinute = 30,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.MEDIUM
        )

        assertEquals("Instance and companion results must match", fromInstance, fromCompanion)
        assertEquals("Instance and static field results must match", fromInstance, fromStaticField)
    }
}
