package com.budzik.alarm.domain.generator

import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.domain.model.GeneratedAlarm

/**
 * Production implementation of [IBatchAlarmGenerator].
 *
 * Implements batch alarm generation (Feature 7 & Feature 8 in PROJECT.md) according to
 * Requirement R2 in ORIGINAL_REQUEST.md:
 * - Generates [count] sequential alarms spaced by [intervalMinutes].
 * - Handles midnight rollovers seamlessly (e.g., 23:55 -> 00:00 -> 00:05), correctly advancing
 *   hour and minute components into the next day without past-timestamp scheduling errors.
 * - Produces strictly monotonically increasing epoch timestamps ([GeneratedAlarm.triggerEpochMs]).
 * - Propagates the configured [difficulty] tier to every generated alarm in the sequence.
 * - Defensive validation: rejects non-positive count/interval and out-of-range hours (0..23) and minutes (0..59).
 *
 * Zero Android SDK dependencies for fast, pure JVM execution.
 * Provides both class instantiation and JVM static singleton [INSTANCE] for direct reflection
 * compatibility with [com.budzik.alarm.e2e.TestComponentFactory].
 */
class BatchAlarmGenerator : IBatchAlarmGenerator {

    override fun generateBatch(
        count: Int,
        intervalMinutes: Int,
        baseHour: Int,
        baseMinute: Int,
        baseEpochMs: Long,
        difficulty: Difficulty
    ): List<GeneratedAlarm> {
        require(count > 0) { "Count must be > 0, got $count" }
        require(intervalMinutes > 0) { "Interval must be > 0, got $intervalMinutes" }
        require(baseHour in 0..23) { "Base hour must be between 0 and 23, got $baseHour" }
        require(baseMinute in 0..59) { "Base minute must be between 0 and 59, got $baseMinute" }

        val alarms = ArrayList<GeneratedAlarm>(count)
        val baseTotalMinutes = baseHour * 60 + baseMinute
        val intervalMs = intervalMinutes.toLong() * 60_000L

        for (i in 0 until count) {
            val totalMinutes = baseTotalMinutes + (i * intervalMinutes)
            val normalizedMinutes = totalMinutes % MINUTES_PER_DAY
            val hour = (normalizedMinutes / 60) % 24
            val minute = normalizedMinutes % 60
            val triggerEpochMs = baseEpochMs + (i.toLong() * intervalMs)

            alarms.add(
                GeneratedAlarm(
                    hour = hour,
                    minute = minute,
                    triggerEpochMs = triggerEpochMs,
                    difficulty = difficulty
                )
            )
        }

        return alarms
    }

    companion object : IBatchAlarmGenerator {
        private const val MINUTES_PER_DAY = 24 * 60

        /**
         * Singleton instance exposed as a JVM static field for reflection access
         * in test factories and dependency injection defaults.
         */
        @JvmField
        val INSTANCE: BatchAlarmGenerator = BatchAlarmGenerator()

        override fun generateBatch(
            count: Int,
            intervalMinutes: Int,
            baseHour: Int,
            baseMinute: Int,
            baseEpochMs: Long,
            difficulty: Difficulty
        ): List<GeneratedAlarm> = INSTANCE.generateBatch(
            count = count,
            intervalMinutes = intervalMinutes,
            baseHour = baseHour,
            baseMinute = baseMinute,
            baseEpochMs = baseEpochMs,
            difficulty = difficulty
        )
    }
}
