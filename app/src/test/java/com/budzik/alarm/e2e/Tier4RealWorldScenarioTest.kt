package com.budzik.alarm.e2e

import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.data.local.RingingStateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Dual Track E2E Test Suite - Tier 4: Real-World Application Scenarios
 *
 * Implements full life-cycle user workflows that combine user interactions,
 * background services, persistence, math missions, and anti-cheat mechanisms.
 *
 * Scenarios:
 * 1. Heavy Sleeper: 5 sequential alarms at 5-minute intervals, solving 1st alarm, one-click clearing rest.
 * 2. Student Anti-Cheat Evasion: Hard 4-digit math alarm, hardware volume press and device reboot cheat attempts,
 *    reboot trap recovery, and forced mathematical puzzle solution.
 * 3. Night-Shift Worker: Midnight-crossing batch generation (23:45 -> 00:15), chronological ordering,
 *    and selective alarm management.
 * 4. Weekday Recurrence & Error Recovery: Mon-Fri recurring alarm, user input mistakes,
 *    progression state machine, and clean dismissal.
 * 5. Battery Saver & Emergency Clear All: Bulk alarms created, low battery optimization prompt,
 *    and atomic one-click purge across Room DB and AlarmManager.
 */
class Tier4RealWorldScenarioTest {

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

    /**
     * Scenario 1: Heavy Sleeper Workflow
     * User needs 5 alarms every 5 minutes from 06:30 to guarantee waking up.
     * Alarm 1 triggers. User wakes up, solves the Medium math equations (2-digit addition),
     * and dismisses alarm 1. Feeling alert, the user uses "Clear All" to delete the remaining 4 alarms.
     */
    @Test
    fun test_tier4_scenario1_heavy_sleeper_batch_alarms_and_partial_dismissal() = runBlocking {
        // Step 1: User creates 5 alarms starting at 06:30 spaced by 5 min with Medium difficulty
        val baseEpochMs = 1700000000000L
        val batchAlarms = batchGenerator.generateBatch(
            count = 5,
            intervalMinutes = 5,
            baseHour = 6,
            baseMinute = 30,
            baseEpochMs = baseEpochMs,
            difficulty = Difficulty.MEDIUM
        )
        assertEquals(5, batchAlarms.size)

        // Step 2: System schedules all 5 alarms in DB & AlarmManager
        val alarmIds = mutableListOf<Long>()
        for (b in batchAlarms) {
            val id = repository.insertAlarm(
                AlarmEntity(
                    hour = b.hour,
                    minute = b.minute,
                    isEnabled = true,
                    daysOfWeekBitmask = 0,
                    difficulty = b.difficulty.name,
                    problemCount = 3,
                    label = "Heavy Sleeper Alarm"
                )
            )
            alarmIds.add(id)
            scheduler.schedule(repository.getAlarmById(id)!!)
        }
        assertEquals(5, scheduler.scheduledAlarms.size)

        // Step 3: Alarm 1 (06:30) triggers
        val firstAlarmId = alarmIds[0]
        enforcement.startRinging(firstAlarmId)
        repository.setRingingState(
            RingingStateEntity(
                alarmId = firstAlarmId,
                isRinging = true,
                difficulty = "MEDIUM",
                problemCount = 3,
                solvedCount = 0
            )
        )
        assertTrue(enforcement.isRinging)

        // Step 4: User solves the 3 required Medium math problems on-screen
        var solved = 0
        while (solved < 3) {
            val problem = mathGenerator.generateProblem(Difficulty.MEDIUM)
            assertTrue(problem.operandA in 10..99)
            assertTrue(problem.operandB in 10..99)
            assertTrue(mathGenerator.validateAnswer(problem, problem.solution))
            solved++
        }
        assertEquals(3, solved)

        // Step 5: Alarm 1 is dismissed
        enforcement.stopRinging()
        repository.clearRingingState()
        assertFalse(enforcement.isRinging)

        // Step 6: User is now fully awake and taps "Clear All" to dismiss the remaining 4 scheduled alarms
        val remainingAlarms = repository.getAllAlarms().first()
        scheduler.cancelAll(remainingAlarms.map { it.id })
        repository.deleteAllAlarms()

        // Step 7: Verifications
        assertTrue("All alarms removed from repository", repository.getAllAlarms().first().isEmpty())
        assertTrue("All alarms removed from scheduler", scheduler.scheduledAlarms.isEmpty())
        assertEquals(5, scheduler.cancelledAlarmIds.size)
    }

    /**
     * Scenario 2: Student Anti-Cheat Evasion Workflow
     * Student sets a 4-digit addition Hard alarm to ensure waking up for an exam.
     * Alarm rings at 07:00 at max volume. Student attempts to silence with volume keys (blocked).
     * Student reboots the phone to kill the app.
     * BootReceiver runs on boot, detects Reboot Trap in storage, and resumes full-volume ringing immediately.
     * Student is forced to solve 4-digit addition problems to finally dismiss the alarm.
     */
    @Test
    fun test_tier4_scenario2_student_anti_cheat_reboot_trap_evasion_and_forced_solution() = runBlocking {
        // Step 1: Create Hard math alarm
        val examAlarmId = repository.insertAlarm(
            AlarmEntity(
                hour = 7,
                minute = 0,
                isEnabled = true,
                daysOfWeekBitmask = 0,
                difficulty = "HARD",
                problemCount = 3,
                label = "Final Exam Wake-up"
            )
        )

        // Step 2: Alarm triggers at 07:00
        enforcement.startRinging(examAlarmId)
        repository.setRingingState(
            RingingStateEntity(
                alarmId = examAlarmId,
                isRinging = true,
                difficulty = "HARD",
                problemCount = 3,
                solvedCount = 0
            )
        )
        assertTrue(enforcement.isRinging)
        assertEquals(100, enforcement.currentVolume)

        // Step 3: Student tries volume-down bypass
        enforcement.simulateExternalVolumeChange(15)
        assertEquals("Volume down bypass blocked, volume locked to 100", 100, enforcement.currentVolume)

        // Step 4: Student forces hard device reboot (kills active process)
        // Controller state is reset
        val rebootedController = FakeAlarmEnforcementController(maxVolume = 100)
        assertFalse(rebootedController.isRinging)

        // Step 5: Android BootReceiver fires LOCKED_BOOT_COMPLETED / BOOT_COMPLETED
        val trapState = repository.getRingingState()
        assertNotNull("Direct Boot storage maintains trap record", trapState)
        assertTrue(trapState!!.isRinging)
        assertEquals(examAlarmId, trapState.alarmId)

        // Trap immediately forces ringing resumption at 100% volume
        rebootedController.startRinging(trapState.alarmId)
        assertTrue("Alarm resumes ringing upon restart", rebootedController.isRinging)
        assertEquals(100, rebootedController.currentVolume)

        // Step 6: Student accepts defeat and solves the 3 Hard math problems
        var solved = 0
        while (solved < 3) {
            val problem = mathGenerator.generateProblem(Difficulty.HARD)
            assertTrue(problem.operandA in 1000..9999)
            assertTrue(problem.operandB in 1000..9999)
            // Invalid answer attempt first
            assertFalse(mathGenerator.validateAnswer(problem, problem.solution + 10))
            // Correct answer submission
            assertTrue(mathGenerator.validateAnswer(problem, problem.solution))
            solved++
        }

        // Step 7: Alarm is dismissed legitimately
        rebootedController.stopRinging()
        repository.clearRingingState()

        assertFalse("Alarm stopped ringing", rebootedController.isRinging)
        assertNull("Ringing state cleared from storage", repository.getRingingState())
    }

    /**
     * Scenario 3: Night-Shift Worker Midnight Crossing Workflow
     * Shift worker sets 4 alarms starting at 23:45 spaced by 10 minutes to wake up for midnight shift.
     * Alarms cross midnight: 23:45, 23:55, 00:05, 00:15.
     * Verifies correct calculation, sorting, and management.
     */
    @Test
    fun test_tier4_scenario3_shift_worker_midnight_crossing_batch_workflow() = runBlocking {
        val baseEpoch = 1700000000000L
        val alarms = batchGenerator.generateBatch(
            count = 4,
            intervalMinutes = 10,
            baseHour = 23,
            baseMinute = 45,
            baseEpochMs = baseEpoch,
            difficulty = Difficulty.EASY
        )

        // Chronological check
        assertEquals(23, alarms[0].hour); assertEquals(45, alarms[0].minute)
        assertEquals(23, alarms[1].hour); assertEquals(55, alarms[1].minute)
        assertEquals(0, alarms[2].hour); assertEquals(5, alarms[2].minute)
        assertEquals(0, alarms[3].hour); assertEquals(15, alarms[3].minute)

        // Persist to repository
        val storedIds = mutableListOf<Long>()
        for (a in alarms) {
            val id = repository.insertAlarm(
                AlarmEntity(
                    hour = a.hour,
                    minute = a.minute,
                    isEnabled = true,
                    daysOfWeekBitmask = 0,
                    difficulty = a.difficulty.name,
                    problemCount = 1,
                    label = "Night Shift Alarm"
                )
            )
            storedIds.add(id)
        }

        // Worker wakes up at 23:55 (alarm 2) and disables the 00:05 and 00:15 alarms
        val alarm3 = repository.getAlarmById(storedIds[2])!!
        val alarm4 = repository.getAlarmById(storedIds[3])!!
        repository.updateAlarm(alarm3.copy(isEnabled = false))
        repository.updateAlarm(alarm4.copy(isEnabled = false))

        val enabled = repository.getEnabledAlarms()
        assertEquals(2, enabled.size)
        assertEquals(listOf(storedIds[0], storedIds[1]), enabled.map { it.id })
    }

    /**
     * Scenario 4: Weekday Recurring Alarm with User Errors Workflow
     * User configures a Mon-Fri recurring alarm with 3 Easy math problems.
     * Alarm triggers. Half-asleep user enters incorrect answers twice.
     * System provides feedback, retains volume at max, and advances only on correct answers.
     */
    @Test
    fun test_tier4_scenario4_weekday_recurring_alarm_with_miscalculation_and_error_recovery() = runBlocking {
        // Mon-Fri bitmask: 1 + 2 + 4 + 8 + 16 = 31
        val alarmId = repository.insertAlarm(
            AlarmEntity(
                hour = 7,
                minute = 15,
                isEnabled = true,
                daysOfWeekBitmask = 31,
                difficulty = "EASY",
                problemCount = 3,
                label = "Workdays Wakeup"
            )
        )

        enforcement.startRinging(alarmId)
        repository.setRingingState(
            RingingStateEntity(
                alarmId = alarmId,
                isRinging = true,
                difficulty = "EASY",
                problemCount = 3,
                solvedCount = 0
            )
        )

        val targetCount = 3
        var currentSolved = 0

        while (currentSolved < targetCount) {
            val problem = mathGenerator.generateProblem(Difficulty.EASY)

            // User makes half-asleep mistakes on first problem
            if (currentSolved == 0) {
                assertFalse("Mistake 1 must not validate", mathGenerator.validateAnswer(problem, problem.solution + 2))
                assertFalse("Mistake 2 must not validate", mathGenerator.validateAnswer(problem, 0))
                assertTrue("Alarm still ringing after mistakes", enforcement.isRinging)
                assertEquals(100, enforcement.currentVolume)
            }

            // User enters correct answer
            assertTrue("Correct answer validates", mathGenerator.validateAnswer(problem, problem.solution))
            currentSolved++
        }

        assertEquals(targetCount, currentSolved)
        enforcement.stopRinging()
        repository.clearRingingState()

        assertFalse(enforcement.isRinging)
        assertNull(repository.getRingingState())
    }

    /**
     * Scenario 5: Battery Saver & Emergency Clear All Workflow
     * 10 alarms are stored in the database.
     * Battery drops to 5%, system battery saver mode engaged.
     * User launches app and executes single-action "Clear All".
     * Cancels all 10 alarms from AlarmManager, deletes all rows in Room DB,
     * and clears any ringing state in a single atomic action.
     */
    @Test
    fun test_tier4_scenario5_battery_saver_and_emergency_clear_all_workflow() = runBlocking {
        // Create 10 alarms
        val batch = batchGenerator.generateBatch(
            count = 10,
            intervalMinutes = 10,
            baseHour = 5,
            baseMinute = 0,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.EASY
        )

        for (b in batch) {
            val id = repository.insertAlarm(
                AlarmEntity(
                    hour = b.hour,
                    minute = b.minute,
                    isEnabled = true,
                    daysOfWeekBitmask = 0,
                    difficulty = b.difficulty.name,
                    problemCount = 1,
                    label = "Batch Alarm"
                )
            )
            scheduler.schedule(repository.getAlarmById(id)!!)
        }

        assertEquals(10, repository.getAllAlarms().first().size)
        assertEquals(10, scheduler.scheduledAlarms.size)

        // User performs "Clear All"
        val allAlarms = repository.getAllAlarms().first()
        val allIds = allAlarms.map { it.id }
        scheduler.cancelAll(allIds)
        repository.deleteAllAlarms()
        repository.clearRingingState()

        // Verification
        assertTrue("Database is completely purged", repository.getAllAlarms().first().isEmpty())
        assertTrue("Scheduler active list is empty", scheduler.scheduledAlarms.isEmpty())
        assertEquals(10, scheduler.cancelledAlarmIds.size)
        assertNull("Ringing state is clean", repository.getRingingState())
    }
}
