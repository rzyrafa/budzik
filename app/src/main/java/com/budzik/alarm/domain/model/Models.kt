package com.budzik.alarm.domain.model

/** Kind of alarm, stored in [com.budzik.alarm.data.local.AlarmEntity.type] instead of guessing from the label. */
enum class AlarmType {
    SINGLE,
    SERIES,
    NAP,
    TEST
}

enum class Difficulty {
    EASY,
    MEDIUM,
    HARD
}

data class MathProblem(
    val operandA: Int,
    val operandB: Int,
    val solution: Int
)

data class GeneratedAlarm(
    val hour: Int,
    val minute: Int,
    val triggerEpochMs: Long,
    val difficulty: Difficulty
)
