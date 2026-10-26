package com.budzik.alarm.domain.generator

import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.domain.model.GeneratedAlarm

interface IBatchAlarmGenerator {
    fun generateBatch(
        count: Int,
        intervalMinutes: Int,
        baseHour: Int,
        baseMinute: Int,
        baseEpochMs: Long,
        difficulty: Difficulty
    ): List<GeneratedAlarm>
}
