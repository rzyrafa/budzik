package com.budzik.alarm.domain

import com.budzik.alarm.domain.generator.BatchAlarmGenerator
import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.domain.model.GeneratedAlarm
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Adversarial Stress Challenge Test Suite for [BatchAlarmGenerator].
 *
 * Authored by Challenger 2 (teamwork_preview_challenger_m2_2) to empirically stress-test:
 * 1. Extreme intervals (720 min, 1440 min, multi-week intervals)
 * 2. Large batch counts (100 alarms, 1000 alarms)
 * 3. Rollover starting at 23:59 boundary
 * 4. Leap hour / Daylight saving epoch progression
 * 5. Invariant verification (strict monotonic epoch progression, exact count, 0..23 hour, 0..59 minute)
 * 6. Extreme integer arithmetic boundaries / overflow detection
 */
class BatchAlarmGeneratorStressChallengeTest {

    private lateinit var generator: BatchAlarmGenerator

    @Before
    fun setUp() {
        generator = BatchAlarmGenerator()
    }

    // =========================================================================
    // 1. Extreme Intervals & Multi-Day Rollover
    // =========================================================================

    @Test
    fun testExtremeInterval_720Minutes_multiDayAlternation() {
        // 720 minutes = 12 hours
        // Count = 10, base = 14:15
        val baseHour = 14
        val baseMinute = 15
        val baseEpochMs = 1700000000000L
        val intervalMinutes = 720
        val count = 10

        val alarms = generator.generateBatch(
            count = count,
            intervalMinutes = intervalMinutes,
            baseHour = baseHour,
            baseMinute = baseMinute,
            baseEpochMs = baseEpochMs,
            difficulty = Difficulty.MEDIUM
        )

        assertEquals("Output count must exactly match requested count", count, alarms.size)

        for (i in alarms.indices) {
            val alarm = alarms[i]
            assertTrue("Hour must be within 0..23, got ${alarm.hour} at index $i", alarm.hour in 0..23)
            assertTrue("Minute must be within 0..59, got ${alarm.minute} at index $i", alarm.minute in 0..59)
            assertEquals("Minute should always remain 15", 15, alarm.minute)

            val expectedHour = if (i % 2 == 0) 14 else 2
            assertEquals("Hour at index $i must alternate between 14 and 2", expectedHour, alarm.hour)

            val expectedEpoch = baseEpochMs + (i.toLong() * intervalMinutes * 60_000L)
            assertEquals("Epoch timestamp at index $i must match formula", expectedEpoch, alarm.triggerEpochMs)
        }

        // Verify strictly monotonic epoch progression
        for (i in 1 until alarms.size) {
            val diff = alarms[i].triggerEpochMs - alarms[i - 1].triggerEpochMs
            assertEquals("Epoch delta must be exactly 720 minutes", 720L * 60_000L, diff)
        }
    }

    @Test
    fun testExtremeInterval_1440Minutes_fullDayRollover() {
        // 1440 minutes = 24 hours (1 full day)
        val count = 14
        val baseHour = 23
        val baseMinute = 59
        val baseEpochMs = 1700000000000L
        val intervalMinutes = 1440

        val alarms = generator.generateBatch(
            count = count,
            intervalMinutes = intervalMinutes,
            baseHour = baseHour,
            baseMinute = baseMinute,
            baseEpochMs = baseEpochMs,
            difficulty = Difficulty.HARD
        )

        assertEquals(count, alarms.size)

        for (i in alarms.indices) {
            val alarm = alarms[i]
            assertTrue("Hour in 0..23", alarm.hour in 0..23)
            assertTrue("Minute in 0..59", alarm.minute in 0..59)
            assertEquals("Hour must remain 23 for 24h step", 23, alarm.hour)
            assertEquals("Minute must remain 59 for 24h step", 59, alarm.minute)

            val expectedEpoch = baseEpochMs + (i.toLong() * 1440L * 60_000L)
            assertEquals(expectedEpoch, alarm.triggerEpochMs)
        }

        for (i in 1 until alarms.size) {
            val diff = alarms[i].triggerEpochMs - alarms[i - 1].triggerEpochMs
            assertEquals("24h epoch delta must equal 86_400_000 ms", 86_400_000L, diff)
        }
    }

    @Test
    fun testExtremeInterval_multiWeek10080Minutes() {
        // 10080 minutes = 7 days
        val count = 5
        val baseHour = 7
        val baseMinute = 30
        val baseEpochMs = 1700000000000L
        val intervalMinutes = 10080

        val alarms = generator.generateBatch(
            count = count,
            intervalMinutes = intervalMinutes,
            baseHour = baseHour,
            baseMinute = baseMinute,
            baseEpochMs = baseEpochMs,
            difficulty = Difficulty.EASY
        )

        assertEquals(count, alarms.size)
        for (i in alarms.indices) {
            assertEquals("Hour must remain 7", 7, alarms[i].hour)
            assertEquals("Minute must remain 30", 30, alarms[i].minute)
        }

        for (i in 1 until alarms.size) {
            val diff = alarms[i].triggerEpochMs - alarms[i - 1].triggerEpochMs
            assertEquals(10080L * 60_000L, diff)
        }
    }

    // =========================================================================
    // 2. Large Batch Counts
    // =========================================================================

    @Test
    fun testLargeBatchCount_100Alarms_interval1Minute() {
        val count = 100
        val baseHour = 23
        val baseMinute = 0
        val baseEpochMs = 1700000000000L

        val alarms = generator.generateBatch(
            count = count,
            intervalMinutes = 1,
            baseHour = baseHour,
            baseMinute = baseMinute,
            baseEpochMs = baseEpochMs,
            difficulty = Difficulty.EASY
        )

        assertEquals(100, alarms.size)

        // Index 0: 23:00
        assertEquals(23, alarms[0].hour); assertEquals(0, alarms[0].minute)
        // Index 59: 23:59
        assertEquals(23, alarms[59].hour); assertEquals(59, alarms[59].minute)
        // Index 60: 00:00 (midnight crossover)
        assertEquals(0, alarms[60].hour); assertEquals(0, alarms[60].minute)
        // Index 99: 00:39
        assertEquals(0, alarms[99].hour); assertEquals(39, alarms[99].minute)

        // Invariant: strictly monotonic
        for (i in 1 until alarms.size) {
            val diff = alarms[i].triggerEpochMs - alarms[i - 1].triggerEpochMs
            assertEquals(60_000L, diff)
            assertTrue("Hour in 0..23", alarms[i].hour in 0..23)
            assertTrue("Minute in 0..59", alarms[i].minute in 0..59)
        }
    }

    @Test
    fun testLargeBatchCount_1000Alarms_interval15Minutes() {
        val count = 1000
        val baseHour = 0
        val baseMinute = 0
        val baseEpochMs = 1700000000000L
        val intervalMinutes = 15

        val alarms = generator.generateBatch(
            count = count,
            intervalMinutes = intervalMinutes,
            baseHour = baseHour,
            baseMinute = baseMinute,
            baseEpochMs = baseEpochMs,
            difficulty = Difficulty.HARD
        )

        assertEquals(1000, alarms.size)

        for (i in alarms.indices) {
            val alarm = alarms[i]
            assertTrue("Hour in 0..23 at $i", alarm.hour in 0..23)
            assertTrue("Minute in 0..59 at $i", alarm.minute in 0..59)
            assertEquals("Difficulty must be propagated", Difficulty.HARD, alarm.difficulty)
        }

        for (i in 1 until alarms.size) {
            val diff = alarms[i].triggerEpochMs - alarms[i - 1].triggerEpochMs
            assertEquals("Epoch diff at index $i must be 15 min", 15L * 60_000L, diff)
        }
    }

    // =========================================================================
    // 3. Midnight Rollover Starting at 23:59
    // =========================================================================

    @Test
    fun testRolloverStartingAt2359_consecutiveMinutes() {
        val count = 5
        val alarms = generator.generateBatch(
            count = count,
            intervalMinutes = 1,
            baseHour = 23,
            baseMinute = 59,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.EASY
        )

        assertEquals(5, alarms.size)

        // 23:59 -> 00:00 -> 00:01 -> 00:02 -> 00:03
        assertEquals(23, alarms[0].hour); assertEquals(59, alarms[0].minute)
        assertEquals(0, alarms[1].hour); assertEquals(0, alarms[1].minute)
        assertEquals(0, alarms[2].hour); assertEquals(1, alarms[2].minute)
        assertEquals(0, alarms[3].hour); assertEquals(2, alarms[3].minute)
        assertEquals(0, alarms[4].hour); assertEquals(3, alarms[4].minute)

        for (i in 1 until alarms.size) {
            assertEquals(60_000L, alarms[i].triggerEpochMs - alarms[i - 1].triggerEpochMs)
        }
    }

    @Test
    fun testRolloverStartingAt2359_with61MinuteInterval() {
        val count = 5
        val alarms = generator.generateBatch(
            count = count,
            intervalMinutes = 61,
            baseHour = 23,
            baseMinute = 59,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.MEDIUM
        )

        assertEquals(5, alarms.size)

        // i=0: 23:59
        // i=1: 23:59 + 61m = 25:00 -> 01:00
        // i=2: 01:00 + 61m = 02:01
        // i=3: 02:01 + 61m = 03:02
        // i=4: 03:02 + 61m = 04:03
        assertEquals(23, alarms[0].hour); assertEquals(59, alarms[0].minute)
        assertEquals(1, alarms[1].hour); assertEquals(0, alarms[1].minute)
        assertEquals(2, alarms[2].hour); assertEquals(1, alarms[2].minute)
        assertEquals(3, alarms[3].hour); assertEquals(2, alarms[3].minute)
        assertEquals(4, alarms[4].hour); assertEquals(3, alarms[4].minute)

        for (i in 1 until alarms.size) {
            assertEquals(61L * 60_000L, alarms[i].triggerEpochMs - alarms[i - 1].triggerEpochMs)
        }
    }

    // =========================================================================
    // 4. Leap Hour & Daylight Transitions (Physical Epoch Invariants)
    // =========================================================================

    @Test
    fun testDaylightTransition_physicalEpochProgressionIsStrictlyLinear() {
        // Physical time progression: an hour in UTC epoch is invariant (3,600,000 ms).
        // European Summer Time switch: March 29, 2026 01:00 UTC = 1774746000000L
        val baseEpochMs = 1774746000000L
        val count = 6
        val intervalMinutes = 60

        val alarms = generator.generateBatch(
            count = count,
            intervalMinutes = intervalMinutes,
            baseHour = 1,
            baseMinute = 0,
            baseEpochMs = baseEpochMs,
            difficulty = Difficulty.EASY
        )

        assertEquals(count, alarms.size)

        // Verify that epoch timestamps progress purely monotonically by interval * 60_000 ms
        for (i in 1 until alarms.size) {
            val delta = alarms[i].triggerEpochMs - alarms[i - 1].triggerEpochMs
            assertEquals("Epoch delta across leap hour must remain exactly 3600000 ms", 3600000L, delta)
        }
    }

    // =========================================================================
    // 5. Invariant Stress: Output Count, In-Range Bounds & Monotonicity
    // =========================================================================

    @Test
    fun testInvariantComprehensiveMatrix_variousCountsAndIntervals() {
        val testConfigs = listOf(
            Triple(1, 1, 0),
            Triple(1, 1440, 23),
            Triple(10, 3, 23),
            Triple(50, 17, 12),
            Triple(100, 720, 11),
            Triple(250, 45, 18)
        )

        for ((testCount, testInterval, startHour) in testConfigs) {
            val alarms = generator.generateBatch(
                count = testCount,
                intervalMinutes = testInterval,
                baseHour = startHour,
                baseMinute = 45,
                baseEpochMs = 1700000000000L,
                difficulty = Difficulty.EASY
            )

            // Invariant 1: Count
            assertEquals("Count invariant violated", testCount, alarms.size)

            // Invariant 2: Hour in 0..23, Minute in 0..59
            for (alarm in alarms) {
                assertTrue("Hour bounds violated: ${alarm.hour}", alarm.hour in 0..23)
                assertTrue("Minute bounds violated: ${alarm.minute}", alarm.minute in 0..59)
            }

            // Invariant 3: Monotonic progression
            val expectedDelta = testInterval.toLong() * 60_000L
            for (i in 1 until alarms.size) {
                val actualDelta = alarms[i].triggerEpochMs - alarms[i - 1].triggerEpochMs
                assertEquals("Monotonic step invariant violated at $i", expectedDelta, actualDelta)
            }
        }
    }

    // =========================================================================
    // 6. Adversarial Stress: 32-bit Integer Overflow Boundary Investigation
    // =========================================================================

    @Test
    fun testAdversarial_largeIntervalUnderMaxIntBoundary() {
        // Test an interval of 1,000,000 minutes (~694 days) with count 10
        // totalMinutes at i=9: base + 9 * 1_000_000 = 9_000_000 minutes (fits in Int)
        val alarms = generator.generateBatch(
            count = 10,
            intervalMinutes = 1_000_000,
            baseHour = 10,
            baseMinute = 30,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.MEDIUM
        )

        assertEquals(10, alarms.size)
        for (alarm in alarms) {
            assertTrue("Hour in range: ${alarm.hour}", alarm.hour in 0..23)
            assertTrue("Minute in range: ${alarm.minute}", alarm.minute in 0..59)
        }
    }

    @Test
    fun testAdversarial_extremeIntOverflowCausesNegativeHour() {
        // When (i * intervalMinutes) overflows Int.MAX_VALUE (e.g. 2 * 2_000_000_000 = 4_000_000_000 > Int.MAX_VALUE),
        // totalMinutes wraps around to negative, causing negative hour and minute in current implementation!
        val alarms = generator.generateBatch(
            count = 3,
            intervalMinutes = 2_000_000_000,
            baseHour = 10,
            baseMinute = 0,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.EASY
        )

        assertEquals(3, alarms.size)
        val overflowAlarm = alarms[2] // i = 2
        // We empirically document whether this violates the invariant hour in 0..23
        println("Empirical finding: index 2 hour = ${overflowAlarm.hour}, minute = ${overflowAlarm.minute}")
    }
}

