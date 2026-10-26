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
 * Dual Track E2E Test Suite - Tier 3: Cross-Feature Combinations
 *
 * Verifies complex pairwise and cross-module interactions between:
 * - Batch Alarm Generation + Math Problem Difficulty Tiers
 * - Multi-Alarm Batch Creation + Bulk Clear All + AlarmScheduler Cancellation
 * - Active Ringing + Device Reboot + Reboot Trap Recovery + Math Dismissal Lifecycle
 * - Alarm Recurrence Bitmasks + Alarm Rescheduling Lifecycle
 * - Hardware Volume Lock Resilience + Multi-Problem Math Engine Progression
 * - Batch Creation + Selective Alarm Disabling + Partial Rescheduling
 */
class Tier3CrossFeatureTest {

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

    @Test
    fun test_tier3_batch_generation_with_all_difficulty_tiers_persisted_to_repository() = runBlocking {
        // Generate batches across all three difficulty tiers
        val tiers = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)

        for ((index, tier) in tiers.withIndex()) {
            val generated = batchGenerator.generateBatch(
                count = 3,
                intervalMinutes = 5,
                baseHour = 7 + index,
                baseMinute = 0,
                baseEpochMs = 1700000000000L + (index * 3600000L),
                difficulty = tier
            )

            // Persist to repository as AlarmEntity
            for (g in generated) {
                repository.insertAlarm(
                    AlarmEntity(
                        hour = g.hour,
                        minute = g.minute,
                        isEnabled = true,
                        daysOfWeekBitmask = 0,
                        difficulty = g.difficulty.name,
                        problemCount = when (tier) {
                            Difficulty.EASY -> 1
                            Difficulty.MEDIUM -> 3
                            Difficulty.HARD -> 5
                        },
                        label = "Batch ${tier.name} at ${g.hour}:${g.minute}"
                    )
                )
            }
        }

        val allAlarms = repository.getAllAlarms().first()
        assertEquals("Total of 9 alarms (3 per tier) must be stored", 9, allAlarms.size)

        // Verify tier distribution
        assertEquals(3, allAlarms.count { it.difficulty == "EASY" && it.problemCount == 1 })
        assertEquals(3, allAlarms.count { it.difficulty == "MEDIUM" && it.problemCount == 3 })
        assertEquals(3, allAlarms.count { it.difficulty == "HARD" && it.problemCount == 5 })
    }

    @Test
    fun test_tier3_batch_create_and_bulk_clear_cancels_all_in_scheduler() = runBlocking {
        // 1. Generate a batch of 8 alarms
        val generated = batchGenerator.generateBatch(
            count = 8,
            intervalMinutes = 5,
            baseHour = 6,
            baseMinute = 30,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.MEDIUM
        )

        // 2. Persist to DB and schedule in AlarmManager
        val insertedIds = mutableListOf<Long>()
        for (g in generated) {
            val id = repository.insertAlarm(
                AlarmEntity(
                    hour = g.hour,
                    minute = g.minute,
                    isEnabled = true,
                    daysOfWeekBitmask = 0,
                    difficulty = g.difficulty.name,
                    problemCount = 3,
                    label = "Morning Alarm"
                )
            )
            insertedIds.add(id)
            val entity = repository.getAlarmById(id)!!
            scheduler.schedule(entity)
        }

        assertEquals(8, repository.getAllAlarms().first().size)
        assertEquals(8, scheduler.scheduledAlarms.size)

        // 3. Perform One-Click "Clear All":
        // Must cancel every alarm in scheduler AND delete from repository
        val snapshot = repository.getAllAlarms().first()
        scheduler.cancelAll(snapshot.map { it.id })
        repository.deleteAllAlarms()
        repository.clearRingingState()

        // 4. Verification
        assertTrue("Repository must be completely empty", repository.getAllAlarms().first().isEmpty())
        assertTrue("Scheduler active list must be empty", scheduler.scheduledAlarms.isEmpty())
        assertEquals("All 8 alarm IDs must be registered as cancelled", 8, scheduler.cancelledAlarmIds.size)
        assertTrue(scheduler.cancelledAlarmIds.containsAll(insertedIds))
    }

    @Test
    fun test_tier3_active_ringing_device_reboot_trap_recovery_and_dismissal() = runBlocking {
        // 1. Alarm 42 goes off and rings
        val alarmId = 42L
        enforcement.startRinging(alarmId)
        repository.setRingingState(
            RingingStateEntity(
                alarmId = alarmId,
                isRinging = true,
                difficulty = "HARD",
                problemCount = 3,
                solvedCount = 0
            )
        )

        assertTrue(enforcement.isRinging)
        assertTrue(enforcement.isRebootTrapActive())

        // 2. USER HARD REBOOTS PHONE TO CHEAT! (Simulate system shutdown and reboot)
        val rebootedEnforcement = FakeAlarmEnforcementController(maxVolume = 100)
        assertFalse(rebootedEnforcement.isRinging)

        // 3. BootReceiver runs on BOOT_COMPLETED / LOCKED_BOOT_COMPLETED
        val persistedRingingState = repository.getRingingState()
        assertNotNull("Reboot trap state must persist in storage across reboot", persistedRingingState)
        assertTrue(persistedRingingState!!.isRinging)

        // 4. Trap springs: RebootReceiver immediately restarts ringing at maximum volume
        rebootedEnforcement.startRinging(persistedRingingState.alarmId)
        assertTrue("Alarm resumes ringing at peak volume upon reboot", rebootedEnforcement.isRinging)
        assertEquals(100, rebootedEnforcement.currentVolume)

        // 5. User realizes cheat failed, must solve 3 Hard math problems to dismiss
        var solvedCount = persistedRingingState.solvedCount
        val totalCount = persistedRingingState.problemCount
        val difficulty = Difficulty.valueOf(persistedRingingState.difficulty)

        while (solvedCount < totalCount) {
            val problem = mathGenerator.generateProblem(difficulty)
            val isValid = mathGenerator.validateAnswer(problem, problem.solution)
            assertTrue("Math problem must be validly solved", isValid)
            solvedCount++
        }
        assertEquals(3, solvedCount)

        // 6. Legitimate dismissal: only when all equations are solved
        repository.clearRingingState()
        rebootedEnforcement.stopRinging()

        assertFalse(rebootedEnforcement.isRinging)
        assertFalse(rebootedEnforcement.isRebootTrapActive())
        assertNull(repository.getRingingState())

        // 7. Verify subsequent reboot will not trigger trap again
        val postDismissalBootEnforcement = FakeAlarmEnforcementController()
        val postDismissalState = repository.getRingingState()
        assertNull("Ringing state remains clean after legitimate dismissal", postDismissalState)
        assertFalse("Subsequent reboot does NOT trigger alarm", postDismissalBootEnforcement.isRebootTrapActive())
    }

    @Test
    fun test_tier3_recurring_alarm_bitmask_and_time_recalculation() = runBlocking {
        // Bitmask for Mon-Fri: 1 + 2 + 4 + 8 + 16 = 31
        val weekdayBitmask = 31
        val weekdayAlarm = AlarmEntity(
            hour = 7,
            minute = 0,
            isEnabled = true,
            daysOfWeekBitmask = weekdayBitmask,
            difficulty = "EASY",
            problemCount = 1,
            label = "Weekday Work Alarm"
        )
        val id = repository.insertAlarm(weekdayAlarm)

        // Bitmask for Sat-Sun: 32 + 64 = 96
        val weekendBitmask = 96
        val weekendAlarm = AlarmEntity(
            hour = 9,
            minute = 30,
            isEnabled = true,
            daysOfWeekBitmask = weekendBitmask,
            difficulty = "MEDIUM",
            problemCount = 2,
            label = "Weekend Alarm"
        )
        repository.insertAlarm(weekendAlarm)

        val enabled = repository.getEnabledAlarms()
        assertEquals(2, enabled.size)

        // Verify days of week bitmask parsing
        val monFri = enabled.first { it.id == id }
        assertEquals(weekdayBitmask, monFri.daysOfWeekBitmask)
        assertTrue("Monday bit (bit 0) active", (monFri.daysOfWeekBitmask and 1) != 0)
        assertTrue("Friday bit (bit 4) active", (monFri.daysOfWeekBitmask and 16) != 0)
        assertFalse("Saturday bit (bit 5) inactive", (monFri.daysOfWeekBitmask and 32) != 0)
        assertFalse("Sunday bit (bit 6) inactive", (monFri.daysOfWeekBitmask and 64) != 0)

        // Reschedule all enabled alarms
        scheduler.rescheduleAll(enabled)
        assertEquals(2, scheduler.scheduledAlarms.size)
    }

    @Test
    fun test_tier3_volume_lock_resilience_during_multi_step_math_solving() {
        val totalEquations = 3
        enforcement.startRinging(alarmId = 77L)
        assertEquals(100, enforcement.currentVolume)

        var solved = 0
        while (solved < totalEquations) {
            val problem = mathGenerator.generateProblem(Difficulty.MEDIUM)

            // User attempts wrong answer first
            assertFalse(mathGenerator.validateAnswer(problem, problem.solution + 5))
            // External volume rocker press occurs during wrong attempt
            enforcement.simulateExternalVolumeChange(20)
            assertEquals("Volume must instantly rebound to 100", 100, enforcement.currentVolume)
            assertTrue("Still ringing despite wrong answer", enforcement.isRinging)

            // User then inputs correct answer
            assertTrue(mathGenerator.validateAnswer(problem, problem.solution))
            solved++

            // Until final equation, alarm must NOT stop
            if (solved < totalEquations) {
                assertTrue("Must still be ringing at problem $solved of $totalEquations", enforcement.isRinging)
            }
        }

        // Final equation solved: dismiss
        enforcement.stopRinging()
        assertFalse("Must stop ringing after completing all equations", enforcement.isRinging)
    }

    @Test
    fun test_tier3_batch_alarms_selective_toggle_and_reschedule() = runBlocking {
        // Create 5 batch alarms
        val generated = batchGenerator.generateBatch(
            count = 5,
            intervalMinutes = 5,
            baseHour = 8,
            baseMinute = 0,
            baseEpochMs = 1700000000000L,
            difficulty = Difficulty.EASY
        )

        val insertedIds = mutableListOf<Long>()
        for (g in generated) {
            val id = repository.insertAlarm(
                AlarmEntity(
                    hour = g.hour,
                    minute = g.minute,
                    isEnabled = true,
                    daysOfWeekBitmask = 0,
                    difficulty = g.difficulty.name,
                    problemCount = 1,
                    label = "Batch Alarm"
                )
            )
            insertedIds.add(id)
        }

        // Toggle alarms at index 1 and 3 to disabled
        val alarmToDisable1 = repository.getAlarmById(insertedIds[1])!!
        val alarmToDisable2 = repository.getAlarmById(insertedIds[3])!!
        repository.updateAlarm(alarmToDisable1.copy(isEnabled = false))
        repository.updateAlarm(alarmToDisable2.copy(isEnabled = false))

        // Query enabled alarms
        val enabledAlarms = repository.getEnabledAlarms()
        assertEquals(3, enabledAlarms.size)
        assertEquals(listOf(insertedIds[0], insertedIds[2], insertedIds[4]), enabledAlarms.map { it.id })

        // Reschedule in scheduler
        scheduler.rescheduleAll(enabledAlarms)
        assertEquals(3, scheduler.scheduledAlarms.size)
        assertTrue(scheduler.scheduledAlarms.none { it.id == insertedIds[1] || it.id == insertedIds[3] })
    }
}
