package com.budzik.alarm.e2e

import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.domain.model.MathProblem
import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.data.local.RingingStateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * Dual Track E2E Test Suite - Tier 1: Feature Coverage
 *
 * Verifies the primary behavior (happy path) and contracts of each feature in isolation.
 * Requirements covered:
 * - R3: Progressive Math Mission Engine (Easy, Medium, Hard, Answer Validation)
 * - R2: Batch Alarm Generation (Count, Timestamps, Progression, Difficulty)
 * - Room Persistence & Repository Operations (CRUD, filtering, bulk deletion)
 * - R1, R4: Scheduling & Anti-Cheat State Transitions (Volume Lock, Reboot Trap, Scheduler)
 *
 * Threshold: >= 5 distinct test cases per feature.
 */
class Tier1FeatureCoverageTest {

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
    // FEATURE 1: Math Engine - Easy Tier (1-digit addition, 1..9)
    // =========================================================================

    @Test
    fun test_tier1_math_easy_digit_range_and_solution_bounds() {
        for (i in 1..200) {
            val problem = mathGenerator.generateProblem(Difficulty.EASY)
            assertTrue("Easy operandA must be in 1..9, got ${problem.operandA}", problem.operandA in 1..9)
            assertTrue("Easy operandB must be in 1..9, got ${problem.operandB}", problem.operandB in 1..9)
            assertEquals("Easy solution must equal operandA + operandB", problem.operandA + problem.operandB, problem.solution)
            assertTrue("Easy solution must be in 2..18, got ${problem.solution}", problem.solution in 2..18)
        }
    }

    @Test
    fun test_tier1_math_easy_validate_correct_answer() {
        val problem = MathProblem(operandA = 4, operandB = 5, solution = 9)
        val isValid = mathGenerator.validateAnswer(problem, 9)
        assertTrue("Validation must return true for correct answer", isValid)
    }

    @Test
    fun test_tier1_math_easy_validate_incorrect_answer() {
        val problem = MathProblem(operandA = 3, operandB = 6, solution = 9)
        assertFalse("Validation must return false for off-by-one answer", mathGenerator.validateAnswer(problem, 8))
        assertFalse("Validation must return false for off-by-one answer", mathGenerator.validateAnswer(problem, 10))
    }

    @Test
    fun test_tier1_math_easy_validate_zero_or_negative_answer() {
        val problem = MathProblem(operandA = 2, operandB = 7, solution = 9)
        assertFalse("Validation must return false for answer = 0", mathGenerator.validateAnswer(problem, 0))
        assertFalse("Validation must return false for negative answer", mathGenerator.validateAnswer(problem, -9))
    }

    @Test
    fun test_tier1_math_easy_deterministic_with_fixed_seed() {
        val gen1 = ReferenceMathProblemGenerator(Random(12345))
        val gen2 = ReferenceMathProblemGenerator(Random(12345))

        for (i in 1..20) {
            val p1 = gen1.generateProblem(Difficulty.EASY)
            val p2 = gen2.generateProblem(Difficulty.EASY)
            assertEquals("Seeded generation must produce identical operandA", p1.operandA, p2.operandA)
            assertEquals("Seeded generation must produce identical operandB", p1.operandB, p2.operandB)
            assertEquals("Seeded generation must produce identical solution", p1.solution, p2.solution)
        }
    }

    // =========================================================================
    // FEATURE 2: Math Engine - Medium Tier (2-digit addition, 10..99)
    // =========================================================================

    @Test
    fun test_tier1_math_medium_digit_range_and_solution_bounds() {
        for (i in 1..200) {
            val problem = mathGenerator.generateProblem(Difficulty.MEDIUM)
            assertTrue("Medium operandA must be in 10..99, got ${problem.operandA}", problem.operandA in 10..99)
            assertTrue("Medium operandB must be in 10..99, got ${problem.operandB}", problem.operandB in 10..99)
            assertEquals("Medium solution must equal operandA + operandB", problem.operandA + problem.operandB, problem.solution)
            assertTrue("Medium solution must be in 20..198, got ${problem.solution}", problem.solution in 20..198)
        }
    }

    @Test
    fun test_tier1_math_medium_validate_correct_answer() {
        val problem = MathProblem(operandA = 47, operandB = 38, solution = 85)
        assertTrue("Validation must return true for exact sum", mathGenerator.validateAnswer(problem, 85))
    }

    @Test
    fun test_tier1_math_medium_validate_off_by_one_answer() {
        val problem = MathProblem(operandA = 25, operandB = 55, solution = 80)
        assertFalse("Validation must fail for solution - 1", mathGenerator.validateAnswer(problem, 79))
        assertFalse("Validation must fail for solution + 1", mathGenerator.validateAnswer(problem, 81))
    }

    @Test
    fun test_tier1_math_medium_validate_single_digit_rejected() {
        val problem = MathProblem(operandA = 12, operandB = 15, solution = 27)
        assertFalse("Single digit input must not validate for medium problem", mathGenerator.validateAnswer(problem, 7))
        assertFalse("Truncated digit input must not validate", mathGenerator.validateAnswer(problem, 2))
    }

    @Test
    fun test_tier1_math_medium_boundary_min_sum() {
        val problem = MathProblem(operandA = 10, operandB = 10, solution = 20)
        assertTrue("Min Medium boundary problem must validate", mathGenerator.validateAnswer(problem, 20))
        assertEquals(20, problem.solution)
    }

    // =========================================================================
    // FEATURE 3: Math Engine - Hard Tier (4-digit addition, 1000..9999)
    // =========================================================================

    @Test
    fun test_tier1_math_hard_digit_range_and_solution_bounds() {
        for (i in 1..200) {
            val problem = mathGenerator.generateProblem(Difficulty.HARD)
            assertTrue("Hard operandA must be in 1000..9999, got ${problem.operandA}", problem.operandA in 1000..9999)
            assertTrue("Hard operandB must be in 1000..9999, got ${problem.operandB}", problem.operandB in 1000..9999)
            assertEquals("Hard solution must equal operandA + operandB", problem.operandA + problem.operandB, problem.solution)
            assertTrue("Hard solution must be in 2000..19998, got ${problem.solution}", problem.solution in 2000..19998)
        }
    }

    @Test
    fun test_tier1_math_hard_validate_correct_answer() {
        val problem = MathProblem(operandA = 4829, operandB = 3171, solution = 8000)
        assertTrue("Validation must return true for correct 4-digit sum", mathGenerator.validateAnswer(problem, 8000))
    }

    @Test
    fun test_tier1_math_hard_validate_swapped_digits_rejected() {
        val problem = MathProblem(operandA = 1234, operandB = 5678, solution = 6912)
        assertFalse("Swapped digits answer 6921 must be rejected", mathGenerator.validateAnswer(problem, 6921))
        assertFalse("Reversed answer must be rejected", mathGenerator.validateAnswer(problem, 2196))
    }

    @Test
    fun test_tier1_math_hard_validate_negative_solution_rejected() {
        val problem = MathProblem(operandA = 2000, operandB = 3000, solution = 5000)
        assertFalse("Negative value must be rejected", mathGenerator.validateAnswer(problem, -5000))
    }

    @Test
    fun test_tier1_math_hard_max_sum_boundary() {
        val problem = MathProblem(operandA = 9999, operandB = 9999, solution = 19998)
        assertTrue("Max hard boundary sum 19998 must be valid", mathGenerator.validateAnswer(problem, 19998))
        assertEquals(19998, problem.solution)
    }

    // =========================================================================
    // FEATURE 4: Batch Alarm Generation (R2)
    // =========================================================================

    @Test
    fun test_tier1_batch_exact_alarm_count_generated() {
        val count = 5
        val alarms = batchGenerator.generateBatch(
            count = count,
            intervalMinutes = 5,
            baseHour = 7,
            baseMinute = 0,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.EASY
        )
        assertEquals("Batch generator must produce exactly requested count of alarms", count, alarms.size)
    }

    @Test
    fun test_tier1_batch_exact_timestamp_interval_spacing() {
        val baseEpochMs = 1700000000000L
        val intervalMinutes = 10
        val intervalMs = 10 * 60_000L
        val count = 4

        val alarms = batchGenerator.generateBatch(
            count = count,
            intervalMinutes = intervalMinutes,
            baseHour = 6,
            baseMinute = 30,
            baseEpochMs = baseEpochMs,
            difficulty = Difficulty.MEDIUM
        )

        for (i in 0 until count) {
            val expectedTimestamp = baseEpochMs + (i * intervalMs)
            assertEquals("Alarm index $i must match calculated epoch millis", expectedTimestamp, alarms[i].triggerEpochMs)
        }
    }

    @Test
    fun test_tier1_batch_hour_minute_progression() {
        val alarms = batchGenerator.generateBatch(
            count = 4,
            intervalMinutes = 15,
            baseHour = 8,
            baseMinute = 0,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.EASY
        )

        assertEquals(8, alarms[0].hour); assertEquals(0, alarms[0].minute)
        assertEquals(8, alarms[1].hour); assertEquals(15, alarms[1].minute)
        assertEquals(8, alarms[2].hour); assertEquals(30, alarms[2].minute)
        assertEquals(8, alarms[3].hour); assertEquals(45, alarms[3].minute)
    }

    @Test
    fun test_tier1_batch_difficulty_propagation() {
        for (diff in Difficulty.values()) {
            val alarms = batchGenerator.generateBatch(
                count = 3,
                intervalMinutes = 5,
                baseHour = 9,
                baseMinute = 0,
                baseEpochMs = 1700000000000L,
                difficulty = diff
            )
            alarms.forEach { alarm ->
                assertEquals("Difficulty $diff must be assigned to every generated alarm", diff, alarm.difficulty)
            }
        }
    }

    @Test
    fun test_tier1_batch_single_alarm_generation() {
        val alarms = batchGenerator.generateBatch(
            count = 1,
            intervalMinutes = 10,
            baseHour = 11,
            baseMinute = 45,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.HARD
        )
        assertEquals(1, alarms.size)
        assertEquals(11, alarms[0].hour)
        assertEquals(45, alarms[0].minute)
    }

    // =========================================================================
    // FEATURE 5: Data Persistence & Repository Operations
    // =========================================================================

    @Test
    fun test_tier1_repo_insert_and_retrieve_alarm_by_id() = runBlocking {
        val entity = AlarmEntity(
            hour = 7,
            minute = 30,
            isEnabled = true,
            daysOfWeekBitmask = 0,
            difficulty = "EASY",
            problemCount = 3,
            label = "Morning Alarm"
        )
        val id = repository.insertAlarm(entity)
        assertTrue("Inserted ID must be positive", id > 0)

        val retrieved = repository.getAlarmById(id)
        assertNotNull("Retrieved alarm must not be null", retrieved)
        assertEquals(7, retrieved!!.hour)
        assertEquals(30, retrieved.minute)
        assertEquals("Morning Alarm", retrieved.label)
        assertEquals("EASY", retrieved.difficulty)
    }

    @Test
    fun test_tier1_repo_get_enabled_alarms_filters_disabled() = runBlocking {
        val alarm1 = AlarmEntity(hour = 6, minute = 0, isEnabled = true, daysOfWeekBitmask = 0, difficulty = "EASY", problemCount = 1, label = "A1")
        val alarm2 = AlarmEntity(hour = 7, minute = 0, isEnabled = false, daysOfWeekBitmask = 0, difficulty = "EASY", problemCount = 1, label = "A2")
        val alarm3 = AlarmEntity(hour = 8, minute = 0, isEnabled = true, daysOfWeekBitmask = 0, difficulty = "EASY", problemCount = 1, label = "A3")

        repository.insertAlarm(alarm1)
        repository.insertAlarm(alarm2)
        repository.insertAlarm(alarm3)

        val enabled = repository.getEnabledAlarms()
        assertEquals("Enabled list must contain exactly 2 alarms", 2, enabled.size)
        assertTrue(enabled.all { it.isEnabled })
    }

    @Test
    fun test_tier1_repo_update_alarm_state() = runBlocking {
        val entity = AlarmEntity(hour = 9, minute = 15, isEnabled = true, daysOfWeekBitmask = 0, difficulty = "MEDIUM", problemCount = 3, label = "Update Me")
        val id = repository.insertAlarm(entity)

        val retrieved = repository.getAlarmById(id)!!
        val updated = retrieved.copy(isEnabled = false, label = "Updated Label")
        repository.updateAlarm(updated)

        val afterUpdate = repository.getAlarmById(id)!!
        assertFalse("isEnabled must be updated to false", afterUpdate.isEnabled)
        assertEquals("Updated Label", afterUpdate.label)
    }

    @Test
    fun test_tier1_repo_delete_single_alarm() = runBlocking {
        val id1 = repository.insertAlarm(AlarmEntity(hour = 10, minute = 0, isEnabled = true, daysOfWeekBitmask = 0, difficulty = "HARD", problemCount = 5, label = "D1"))
        val id2 = repository.insertAlarm(AlarmEntity(hour = 11, minute = 0, isEnabled = true, daysOfWeekBitmask = 0, difficulty = "HARD", problemCount = 5, label = "D2"))

        repository.deleteAlarm(id1)

        assertNull("Deleted alarm must be null when queried", repository.getAlarmById(id1))
        assertNotNull("Non-deleted alarm must remain present", repository.getAlarmById(id2))
        assertEquals(1, repository.getAllAlarms().first().size)
    }

    @Test
    fun test_tier1_repo_delete_all_alarms_clears_table() = runBlocking {
        for (i in 1..5) {
            repository.insertAlarm(AlarmEntity(hour = i, minute = 0, isEnabled = true, daysOfWeekBitmask = 0, difficulty = "EASY", problemCount = 1, label = "Alarm $i"))
        }
        assertEquals(5, repository.getAllAlarms().first().size)

        repository.deleteAllAlarms()

        val afterDelete = repository.getAllAlarms().first()
        assertTrue("Table must be empty after deleteAllAlarms", afterDelete.isEmpty())
    }

    // =========================================================================
    // FEATURE 6: Scheduling & Anti-Cheat State Transitions
    // =========================================================================

    @Test
    fun test_tier1_state_start_ringing_sets_active_flag() {
        assertFalse("Initially not ringing", enforcement.isRinging)
        enforcement.startRinging(alarmId = 101L)
        assertTrue("isRinging must be true after startRinging", enforcement.isRinging)
        assertEquals(101L, enforcement.activeRingingAlarmId)
    }

    @Test
    fun test_tier1_state_volume_enforced_to_max() {
        enforcement.startRinging(alarmId = 202L)
        assertEquals(100, enforcement.currentVolume)

        enforcement.simulateExternalVolumeChange(30)
        assertEquals("Anti-cheat must force volume back to max 100", 100, enforcement.currentVolume)
    }

    @Test
    fun test_tier1_state_reboot_trap_active_while_ringing() {
        enforcement.startRinging(alarmId = 303L)
        assertTrue("Reboot trap must be active during ringing session", enforcement.isRebootTrapActive())
    }

    @Test
    fun test_tier1_state_stop_ringing_clears_active_and_trap() {
        enforcement.startRinging(alarmId = 404L)
        enforcement.stopRinging()

        assertFalse("isRinging must be false after stopRinging", enforcement.isRinging)
        assertNull("activeRingingAlarmId must be null after stopRinging", enforcement.activeRingingAlarmId)
        assertFalse("Reboot trap must be inactive after stopRinging", enforcement.isRebootTrapActive())
    }

    @Test
    fun test_tier1_state_scheduler_cancel_removes_from_scheduled() {
        val alarm = AlarmEntity(id = 505L, hour = 7, minute = 0, isEnabled = true, daysOfWeekBitmask = 0, difficulty = "EASY", problemCount = 3, label = "Scheduled")
        scheduler.schedule(alarm)
        assertEquals(1, scheduler.scheduledAlarms.size)

        scheduler.cancel(505L)
        assertTrue("Cancelled alarm must be removed from scheduledAlarms", scheduler.scheduledAlarms.isEmpty())
        assertTrue("Cancelled ID must be tracked", scheduler.cancelledAlarmIds.contains(505L))
    }
}
