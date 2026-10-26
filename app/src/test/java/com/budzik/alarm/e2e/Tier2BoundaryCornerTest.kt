package com.budzik.alarm.e2e

import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.domain.model.MathProblem
import com.budzik.alarm.data.local.AlarmEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Dual Track E2E Test Suite - Tier 2: Boundary & Corner Cases
 *
 * Exercises extreme values, edge boundaries, midnight rollovers, invalid arguments,
 * empty databases, and defensive error handling.
 *
 * Requirements covered:
 * - R3: Math Engine Boundary Conditions (Min/Max digits, overflow resilience)
 * - R2: Midnight Rollover Calculations & Extreme Batch Limits
 * - R2, R1: Invalid Input Rejection & Parameter Boundaries
 * - Data Persistence: Empty Set Operations & Non-Existent ID Queries
 * - R4: Volume Enforcement & Reboot Trap Edge States
 *
 * Threshold: >= 5 distinct test cases per feature.
 */
class Tier2BoundaryCornerTest {

    private lateinit var mathGenerator: ReferenceMathProblemGenerator
    private lateinit var batchGenerator: ReferenceBatchAlarmGenerator
    private lateinit var repository: FakeAlarmRepository
    private lateinit var scheduler: FakeAlarmScheduler
    private lateinit var enforcement: FakeAlarmEnforcementController

    @Before
    fun setUp() {
        mathGenerator = ReferenceMathProblemGenerator()
        batchGenerator = ReferenceBatchAlarmGenerator()
        repository = FakeAlarmRepository()
        scheduler = FakeAlarmScheduler()
        enforcement = FakeAlarmEnforcementController(maxVolume = 100)
    }

    // =========================================================================
    // FEATURE 1: Math Engine Boundary & Corner Cases
    // =========================================================================

    @Test
    fun test_tier2_math_easy_min_boundaries() {
        val problem = MathProblem(operandA = 1, operandB = 1, solution = 2)
        assertTrue("Min Easy sum 1+1=2 must validate", mathGenerator.validateAnswer(problem, 2))
        assertEquals(2, problem.solution)
    }

    @Test
    fun test_tier2_math_easy_max_boundaries() {
        val problem = MathProblem(operandA = 9, operandB = 9, solution = 18)
        assertTrue("Max Easy sum 9+9=18 must validate", mathGenerator.validateAnswer(problem, 18))
        assertEquals(18, problem.solution)
    }

    @Test
    fun test_tier2_math_medium_min_max_boundaries() {
        val minProblem = MathProblem(operandA = 10, operandB = 10, solution = 20)
        assertTrue("Min Medium sum 10+10=20 must validate", mathGenerator.validateAnswer(minProblem, 20))

        val maxProblem = MathProblem(operandA = 99, operandB = 99, solution = 198)
        assertTrue("Max Medium sum 99+99=198 must validate", mathGenerator.validateAnswer(maxProblem, 198))
    }

    @Test
    fun test_tier2_math_hard_min_max_boundaries() {
        val minProblem = MathProblem(operandA = 1000, operandB = 1000, solution = 2000)
        assertTrue("Min Hard sum 1000+1000=2000 must validate", mathGenerator.validateAnswer(minProblem, 2000))

        val maxProblem = MathProblem(operandA = 9999, operandB = 9999, solution = 19998)
        assertTrue("Max Hard sum 9999+9999=19998 must validate", mathGenerator.validateAnswer(maxProblem, 19998))
    }

    @Test
    fun test_tier2_math_validate_answer_integer_overflow_safety() {
        val problem = MathProblem(operandA = 5000, operandB = 5000, solution = 10000)
        // Extreme inputs must return false safely without crashing or throwing
        assertFalse("Integer.MAX_VALUE must not match", mathGenerator.validateAnswer(problem, Int.MAX_VALUE))
        assertFalse("Integer.MIN_VALUE must not match", mathGenerator.validateAnswer(problem, Int.MIN_VALUE))
    }

    // =========================================================================
    // FEATURE 2: Batch Generation Limits & Rollovers
    // =========================================================================

    @Test
    fun test_tier2_batch_midnight_rollover_2355_to_0000() {
        // 23:55, count 3, interval 5 min -> 23:55, 00:00, 00:05
        val alarms = batchGenerator.generateBatch(
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
    fun test_tier2_batch_midnight_multi_hour_rollover() {
        // 23:40, count 5, interval 30 min -> 23:40, 00:10, 00:40, 01:10, 01:40
        val alarms = batchGenerator.generateBatch(
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
    fun test_tier2_batch_midnight_exact_crossing_at_2359() {
        // 23:59, count 2, interval 1 min -> 23:59, 00:00
        val alarms = batchGenerator.generateBatch(
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
    fun test_tier2_batch_rollover_epoch_monotonicity() {
        val baseEpochMs = 1700000000000L
        val alarms = batchGenerator.generateBatch(
            count = 6,
            intervalMinutes = 20,
            baseHour = 23,
            baseMinute = 0,
            baseEpochMs = baseEpochMs,
            difficulty = Difficulty.HARD
        )

        for (i in 1 until alarms.size) {
            assertTrue(
                "Trigger epoch ms must be strictly monotonically increasing across midnight rollover",
                alarms[i].triggerEpochMs > alarms[i - 1].triggerEpochMs
            )
        }
    }

    @Test
    fun test_tier2_batch_extreme_count_generation() {
        // 60 alarms spaced by 1 minute spans full hour correctly
        val alarms = batchGenerator.generateBatch(
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

    // =========================================================================
    // FEATURE 3: Invalid Input Rejection & Parameter Boundaries
    // =========================================================================

    @Test
    fun test_tier2_batch_invalid_zero_count_throws_exception() {
        try {
            batchGenerator.generateBatch(count = 0, intervalMinutes = 5, baseHour = 8, baseMinute = 0, baseEpochMs = 1700000000000L, difficulty = Difficulty.EASY)
            fail("Count = 0 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("Expected count error message", e.message?.contains("Count", ignoreCase = true) == true)
        }
    }

    @Test
    fun test_tier2_batch_invalid_negative_count_throws_exception() {
        try {
            batchGenerator.generateBatch(count = -5, intervalMinutes = 5, baseHour = 8, baseMinute = 0, baseEpochMs = 1700000000000L, difficulty = Difficulty.EASY)
            fail("Negative count must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Success
        }
    }

    @Test
    fun test_tier2_batch_invalid_zero_interval_throws_exception() {
        try {
            batchGenerator.generateBatch(count = 5, intervalMinutes = 0, baseHour = 8, baseMinute = 0, baseEpochMs = 1700000000000L, difficulty = Difficulty.EASY)
            fail("Interval = 0 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("Expected interval error message", e.message?.contains("Interval", ignoreCase = true) == true)
        }
    }

    @Test
    fun test_tier2_batch_invalid_negative_interval_throws_exception() {
        try {
            batchGenerator.generateBatch(count = 5, intervalMinutes = -10, baseHour = 8, baseMinute = 0, baseEpochMs = 1700000000000L, difficulty = Difficulty.EASY)
            fail("Negative interval must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Success
        }
    }

    @Test
    fun test_tier2_batch_invalid_hour_boundary_throws_exception() {
        try {
            batchGenerator.generateBatch(count = 2, intervalMinutes = 5, baseHour = 24, baseMinute = 0, baseEpochMs = 1700000000000L, difficulty = Difficulty.EASY)
            fail("Hour = 24 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Success
        }

        try {
            batchGenerator.generateBatch(count = 2, intervalMinutes = 5, baseHour = -1, baseMinute = 0, baseEpochMs = 1700000000000L, difficulty = Difficulty.EASY)
            fail("Hour = -1 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Success
        }
    }

    @Test
    fun test_tier2_batch_invalid_minute_boundary_throws_exception() {
        try {
            batchGenerator.generateBatch(count = 2, intervalMinutes = 5, baseHour = 12, baseMinute = 60, baseEpochMs = 1700000000000L, difficulty = Difficulty.EASY)
            fail("Minute = 60 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Success
        }

        try {
            batchGenerator.generateBatch(count = 2, intervalMinutes = 5, baseHour = 12, baseMinute = -1, baseEpochMs = 1700000000000L, difficulty = Difficulty.EASY)
            fail("Minute = -1 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Success
        }
    }

    // =========================================================================
    // FEATURE 4: Empty Sets & Boundary States
    // =========================================================================

    @Test
    fun test_tier2_repo_empty_database_get_all_returns_empty_list() = runBlocking {
        val alarms = repository.getAllAlarms().first()
        assertTrue("Fresh repository must emit empty alarm list", alarms.isEmpty())
    }

    @Test
    fun test_tier2_repo_empty_database_get_by_id_returns_null() = runBlocking {
        val nonExistent = repository.getAlarmById(9999L)
        assertNull("Non-existent alarm query must return null", nonExistent)
    }

    @Test
    fun test_tier2_repo_delete_all_on_empty_database_is_safe_noop() = runBlocking {
        // Calling deleteAllAlarms on an empty database must not throw
        repository.deleteAllAlarms()
        assertTrue(repository.getAllAlarms().first().isEmpty())
    }

    @Test
    fun test_tier2_repo_delete_non_existent_alarm_id_is_safe_noop() = runBlocking {
        repository.deleteAlarm(8888L)
        assertTrue(repository.getAllAlarms().first().isEmpty())
    }

    @Test
    fun test_tier2_repo_get_ringing_state_when_none_set_returns_null() = runBlocking {
        assertNull("Default ringing state must be null", repository.getRingingState())
    }

    // =========================================================================
    // FEATURE 5: Anti-Cheat & Volume Enforcement Boundaries
    // =========================================================================

    @Test
    fun test_tier2_volume_enforce_zero_volume_snapped_to_max() {
        enforcement.startRinging(alarmId = 1L)
        enforcement.simulateExternalVolumeChange(0) // Total silence attempt
        assertEquals("Volume 0 attempt must snap immediately to 100", 100, enforcement.currentVolume)
    }

    @Test
    fun test_tier2_volume_enforce_repeated_rapid_volume_drops() {
        enforcement.startRinging(alarmId = 2L)
        for (volumeAttempt in listOf(90, 80, 50, 20, 10, 0, 5, 0)) {
            enforcement.simulateExternalVolumeChange(volumeAttempt)
            assertEquals("Every rapid drop must be neutralized to max 100", 100, enforcement.currentVolume)
        }
    }

    @Test
    fun test_tier2_reboot_trap_query_without_active_session_returns_false() {
        assertFalse("Reboot trap must be false when no alarm is ringing", enforcement.isRebootTrapActive())
    }

    @Test
    fun test_tier2_reboot_trap_double_clear_is_safe() = runBlocking {
        repository.clearRingingState()
        repository.clearRingingState() // Repeated clear
        assertNull(repository.getRingingState())
    }

    @Test
    fun test_tier2_reboot_trap_persists_across_controller_recreation() = runBlocking {
        // Simulate app process writing trap state to repository
        val stateEntity = com.budzik.alarm.data.local.RingingStateEntity(
            alarmId = 999L,
            isRinging = true,
            difficulty = "HARD",
            problemCount = 5,
            solvedCount = 0
        )
        repository.setRingingState(stateEntity)

        // New controller instance spawned upon reboot
        val freshEnforcement = FakeAlarmEnforcementController()
        val persisted = repository.getRingingState()
        assertNotNull("Persisted ringing state must survive process restart", persisted)
        assertTrue(persisted!!.isRinging)
        assertEquals(999L, persisted.alarmId)

        // Fresh controller restores ringing state
        if (persisted.isRinging) {
            freshEnforcement.startRinging(persisted.alarmId)
        }
        assertTrue("Fresh controller resumes ringing upon reboot", freshEnforcement.isRebootTrapActive())
    }
}
