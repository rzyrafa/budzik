package com.budzik.alarm.e2e

import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.domain.model.GeneratedAlarm
import com.budzik.alarm.domain.model.MathProblem
import com.budzik.alarm.domain.generator.IBatchAlarmGenerator
import com.budzik.alarm.domain.generator.IMathProblemGenerator
import com.budzik.alarm.domain.generator.MathProblemGenerator
import com.budzik.alarm.domain.generator.BatchAlarmGenerator
import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.data.local.RingingStateEntity
import com.budzik.alarm.domain.repository.AlarmRepository
import com.budzik.alarm.engine.AlarmEnforcementController
import com.budzik.alarm.engine.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Reference Oracle for IMathProblemGenerator delegating directly to production [MathProblemGenerator].
 */
class ReferenceMathProblemGenerator(
    private val random: Random = Random.Default
) : IMathProblemGenerator {
    private val delegate = MathProblemGenerator(random)

    override fun generateProblem(difficulty: Difficulty): MathProblem =
        delegate.generateProblem(difficulty)

    override fun validateAnswer(problem: MathProblem, answer: Int): Boolean =
        delegate.validateAnswer(problem, answer)
}

/**
 * Reference Oracle for IBatchAlarmGenerator delegating directly to production [BatchAlarmGenerator].
 */
class ReferenceBatchAlarmGenerator : IBatchAlarmGenerator {
    private val delegate = BatchAlarmGenerator.INSTANCE

    override fun generateBatch(
        count: Int,
        intervalMinutes: Int,
        baseHour: Int,
        baseMinute: Int,
        baseEpochMs: Long,
        difficulty: Difficulty
    ): List<GeneratedAlarm> = delegate.generateBatch(
        count = count,
        intervalMinutes = intervalMinutes,
        baseHour = baseHour,
        baseMinute = baseMinute,
        baseEpochMs = baseEpochMs,
        difficulty = difficulty
    )
}

/**
 * In-memory test double for AlarmRepository conforming to PROJECT.md interface contract.
 */
class FakeAlarmRepository : AlarmRepository {
    private val mutex = Mutex()
    private val alarmsMap = mutableMapOf<Long, AlarmEntity>()
    private val _alarmsFlow = MutableStateFlow<List<AlarmEntity>>(emptyList())
    private var ringingState: RingingStateEntity? = null
    private var nextId = 1L

    override fun getAllAlarms(): Flow<List<AlarmEntity>> = _alarmsFlow.asStateFlow()

    override suspend fun getAlarmById(id: Long): AlarmEntity? = mutex.withLock {
        alarmsMap[id]
    }

    override suspend fun getEnabledAlarms(): List<AlarmEntity> = mutex.withLock {
        alarmsMap.values
            .filter { it.isEnabled }
            .sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    override suspend fun insertAlarm(alarm: AlarmEntity): Long = mutex.withLock {
        val id = if (alarm.id == 0L) nextId++ else alarm.id
        val entity = alarm.copy(id = id)
        alarmsMap[id] = entity
        updateFlow()
        id
    }

    override suspend fun updateAlarm(alarm: AlarmEntity) = mutex.withLock {
        alarmsMap[alarm.id] = alarm
        updateFlow()
    }

    override suspend fun deleteAlarm(id: Long) = mutex.withLock {
        alarmsMap.remove(id)
        updateFlow()
    }

    override suspend fun deleteAllAlarms() = mutex.withLock {
        alarmsMap.clear()
        updateFlow()
    }

    override suspend fun getRingingState(): RingingStateEntity? = mutex.withLock {
        ringingState
    }

    override suspend fun setRingingState(state: RingingStateEntity) = mutex.withLock {
        ringingState = state
    }

    override suspend fun clearRingingState() = mutex.withLock {
        ringingState = null
    }

    private fun updateFlow() {
        _alarmsFlow.value = alarmsMap.values
            .sortedWith(compareBy({ it.hour }, { it.minute }))
            .toList()
    }
}

/**
 * In-memory test double for AlarmScheduler recording scheduled and cancelled alarms.
 */
class FakeAlarmScheduler : AlarmScheduler {
    val scheduledAlarms = mutableListOf<AlarmEntity>()
    val cancelledAlarmIds = mutableListOf<Long>()

    override fun schedule(alarm: AlarmEntity) {
        scheduledAlarms.removeAll { it.id == alarm.id }
        scheduledAlarms.add(alarm)
    }

    override fun cancel(alarmId: Long) {
        scheduledAlarms.removeAll { it.id == alarmId }
        cancelledAlarmIds.add(alarmId)
    }

    override fun cancelAll(alarmIds: List<Long>) {
        scheduledAlarms.removeAll { alarmIds.contains(it.id) }
        cancelledAlarmIds.addAll(alarmIds)
    }

    override fun rescheduleAll(alarms: List<AlarmEntity>) {
        scheduledAlarms.clear()
        scheduledAlarms.addAll(alarms)
    }
}

/**
 * In-memory test double for AlarmEnforcementController tracking volume lock and reboot trap states.
 */
class FakeAlarmEnforcementController(
    val maxVolume: Int = 100
) : AlarmEnforcementController {
    var activeRingingAlarmId: Long? = null
    var isRinging: Boolean = false
    var currentVolume: Int = 0
    var isRebootTrapTriggered: Boolean = false

    override fun startRinging(alarmId: Long) {
        activeRingingAlarmId = alarmId
        isRinging = true
        currentVolume = maxVolume
        isRebootTrapTriggered = true
    }

    override fun stopRinging() {
        activeRingingAlarmId = null
        isRinging = false
        isRebootTrapTriggered = false
    }

    override fun enforceMaxVolume() {
        currentVolume = maxVolume
    }

    override fun isRebootTrapActive(): Boolean {
        return isRebootTrapTriggered && isRinging
    }

    fun simulateExternalVolumeChange(newVolume: Int) {
        currentVolume = newVolume
        if (isRinging) {
            enforceMaxVolume()
        }
    }
}

/**
 * Factory providing production generator instances for test suites.
 */
object TestComponentFactory {

    fun createMathGenerator(): IMathProblemGenerator = MathProblemGenerator.INSTANCE

    fun createMathGenerator(random: Random): IMathProblemGenerator = MathProblemGenerator(random)

    fun createBatchGenerator(): IBatchAlarmGenerator = BatchAlarmGenerator.INSTANCE
}
