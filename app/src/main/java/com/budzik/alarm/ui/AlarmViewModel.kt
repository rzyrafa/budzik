package com.budzik.alarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.budzik.alarm.data.local.AlarmDatabase
import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.data.local.isNap
import com.budzik.alarm.domain.generator.BatchAlarmGenerator
import com.budzik.alarm.domain.model.AlarmType
import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.engine.AlarmSchedulerImpl
import com.budzik.alarm.engine.WakeUpCheckReceiver
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

/**
 * All alarm create/update/delete + scheduling logic for the main screen.
 * Every change goes through here so the database and AlarmManager never get out of sync.
 */
class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()
    private val dao = AlarmDatabase.getDatabase(application).alarmDao()
    private val scheduler = AlarmSchedulerImpl(application)

    val alarms: StateFlow<List<AlarmEntity>> = dao.getAllAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------------------------------------------------------- toggles

    fun setAlarmEnabled(alarm: AlarmEntity, enabled: Boolean) = viewModelScope.launch {
        val nextTrigger = if (enabled) {
            AlarmSchedulerImpl.calculateNextTriggerEpochMs(alarm.hour, alarm.minute, alarm.daysOfWeekBitmask)
        } else 0L
        applyEnabledState(alarm.copy(isEnabled = enabled, nextTriggerAt = nextTrigger))
    }

    /** Re-enables a whole series keeping its spacing, starting from the next occurrence of its first alarm. */
    fun setSeriesEnabled(seriesAlarms: List<AlarmEntity>, enabled: Boolean) = viewModelScope.launch {
        val first = seriesAlarms.firstOrNull() ?: return@launch
        val baseTrigger = if (enabled) {
            AlarmSchedulerImpl.calculateNextTriggerEpochMs(first.hour, first.minute, first.daysOfWeekBitmask)
        } else 0L
        val intervalMinutes = seriesIntervalMinutes(seriesAlarms)

        for (alarm in seriesAlarms) {
            val nextTrigger = if (enabled) {
                baseTrigger + alarm.seriesPosition.toLong() * intervalMinutes * 60_000L
            } else 0L
            applyEnabledState(alarm.copy(isEnabled = enabled, nextTriggerAt = nextTrigger))
        }
    }

    // ---------------------------------------------------------------- create / edit

    fun createSingleAlarm(
        hour: Int,
        minute: Int,
        difficulty: Difficulty,
        problemCount: Int,
        wakeUpEnabled: Boolean,
        wakeUpMinutes: Int
    ) = viewModelScope.launch {
        insertAndSchedule(
            AlarmEntity(
                hour = hour,
                minute = minute,
                isEnabled = true,
                daysOfWeekBitmask = 0,
                difficulty = difficulty.name,
                problemCount = problemCount,
                label = "Alarm",
                wakeUpCheckEnabled = wakeUpEnabled,
                wakeUpCheckMinutes = wakeUpMinutes,
                nextTriggerAt = AlarmSchedulerImpl.calculateNextTriggerEpochMs(hour, minute),
                type = AlarmType.SINGLE.name
            )
        )
    }

    fun createSeries(
        count: Int,
        intervalMinutes: Int,
        baseHour: Int,
        baseMinute: Int,
        difficulty: Difficulty,
        problemCount: Int,
        wakeUpEnabled: Boolean,
        wakeUpMinutes: Int
    ) = viewModelScope.launch {
        val generated = BatchAlarmGenerator.INSTANCE.generateBatch(
            count = count,
            intervalMinutes = intervalMinutes,
            baseHour = baseHour,
            baseMinute = baseMinute,
            baseEpochMs = AlarmSchedulerImpl.calculateNextTriggerEpochMs(baseHour, baseMinute),
            difficulty = difficulty
        )
        val seriesId = UUID.randomUUID().toString()
        generated.forEachIndexed { idx, ga ->
            insertAndSchedule(
                AlarmEntity(
                    hour = ga.hour,
                    minute = ga.minute,
                    isEnabled = true,
                    daysOfWeekBitmask = 0,
                    difficulty = difficulty.name,
                    problemCount = problemCount,
                    label = "Seria #${idx + 1}",
                    wakeUpCheckEnabled = wakeUpEnabled,
                    wakeUpCheckMinutes = wakeUpMinutes,
                    seriesId = seriesId,
                    seriesPosition = idx,
                    seriesTotal = count,
                    nextTriggerAt = ga.triggerEpochMs,
                    type = AlarmType.SERIES.name
                )
            )
        }
    }

    fun saveAlarm(updated: AlarmEntity) = viewModelScope.launch {
        val nextTrigger = if (updated.isEnabled) {
            AlarmSchedulerImpl.calculateNextTriggerEpochMs(updated.hour, updated.minute, updated.daysOfWeekBitmask)
        } else 0L
        applyEnabledState(updated.copy(nextTriggerAt = nextTrigger))
    }

    fun saveSeriesWakeUpSettings(seriesAlarms: List<AlarmEntity>, enabled: Boolean, minutes: Int) =
        viewModelScope.launch {
            for (alarm in seriesAlarms) {
                dao.updateAlarm(alarm.copy(wakeUpCheckEnabled = enabled, wakeUpCheckMinutes = minutes))
            }
        }

    // ---------------------------------------------------------------- delete

    fun deleteAlarm(alarm: AlarmEntity) = viewModelScope.launch {
        cancelEverywhere(alarm.id)
        dao.deleteAlarm(alarm.id)
    }

    fun deleteSeries(seriesId: String, seriesAlarms: List<AlarmEntity>) = viewModelScope.launch {
        seriesAlarms.forEach { cancelEverywhere(it.id) }
        dao.deleteSeries(seriesId)
    }

    fun clearAll() = viewModelScope.launch {
        alarms.value.forEach { cancelEverywhere(it.id) }
        dao.deleteAllAlarms()
    }

    // ---------------------------------------------------------------- nap

    /** Replaces any running nap with a new one ringing in [minutes]. */
    fun startNap(minutes: Int, requireMathProblem: Boolean) = viewModelScope.launch {
        for (oldNap in alarms.value.filter { it.isNap }) {
            cancelEverywhere(oldNap.id)
            dao.deleteAlarm(oldNap.id)
        }
        insertAndSchedule(
            timedAlarm(
                triggerMs = System.currentTimeMillis() + minutes * 60_000L,
                label = "Drzemka (+$minutes min)",
                type = AlarmType.NAP,
                problemCount = if (requireMathProblem) 1 else 0
            )
        )
    }

    fun cancelNap(nap: AlarmEntity) = deleteAlarm(nap)

    // ---------------------------------------------------------------- diagnostics

    fun createTestSingleAlarm() = viewModelScope.launch {
        insertAndSchedule(
            timedAlarm(System.currentTimeMillis() + 5_000L, "Test pojedynczy (5s)", AlarmType.TEST)
        )
    }

    fun createTestSeries() = viewModelScope.launch {
        val seriesId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        listOf(now + 5_000L, now + 15_000L).forEachIndexed { idx, triggerMs ->
            insertAndSchedule(
                timedAlarm(triggerMs, "Test Seria #${idx + 1}", AlarmType.TEST).copy(
                    seriesId = seriesId,
                    seriesPosition = idx,
                    seriesTotal = 2
                )
            )
        }
    }

    fun createTestNap() = viewModelScope.launch {
        insertAndSchedule(
            timedAlarm(System.currentTimeMillis() + 5_000L, "Drzemka (Test 5s)", AlarmType.NAP)
        )
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun insertAndSchedule(alarm: AlarmEntity) {
        val id = dao.insertAlarm(alarm)
        scheduler.schedule(alarm.copy(id = id))
    }

    private suspend fun applyEnabledState(alarm: AlarmEntity) {
        dao.updateAlarm(alarm)
        if (alarm.isEnabled) {
            scheduler.schedule(alarm)
        } else {
            cancelEverywhere(alarm.id)
        }
    }

    private fun cancelEverywhere(alarmId: Long) {
        scheduler.cancel(alarmId)
        WakeUpCheckReceiver.cancelCheck(app, alarmId)
    }

    private fun timedAlarm(
        triggerMs: Long,
        label: String,
        type: AlarmType,
        problemCount: Int = 1
    ): AlarmEntity {
        val cal = Calendar.getInstance().apply { timeInMillis = triggerMs }
        return AlarmEntity(
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            isEnabled = true,
            daysOfWeekBitmask = 0,
            difficulty = Difficulty.EASY.name,
            problemCount = problemCount,
            label = label,
            wakeUpCheckEnabled = false,
            wakeUpCheckMinutes = 5,
            nextTriggerAt = triggerMs,
            type = type.name
        )
    }

    companion object {
        /** Spacing of a series derived from its first two alarms (wrapping over midnight). */
        fun seriesIntervalMinutes(seriesAlarms: List<AlarmEntity>): Int {
            if (seriesAlarms.size < 2) return 5
            val m0 = seriesAlarms[0].hour * 60 + seriesAlarms[0].minute
            val m1 = seriesAlarms[1].hour * 60 + seriesAlarms[1].minute
            val diff = (m1 - m0 + 1440) % 1440
            return if (diff > 0) diff else 5
        }
    }
}
