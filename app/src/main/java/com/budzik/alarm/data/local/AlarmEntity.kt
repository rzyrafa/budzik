package com.budzik.alarm.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.budzik.alarm.domain.model.AlarmType
import java.util.Calendar

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean,
    val daysOfWeekBitmask: Int = 0, // 0 = one-time, bits 1..7 for Mon..Sun
    val difficulty: String,         // EASY, MEDIUM, HARD
    val problemCount: Int,
    val label: String,
    val wakeUpCheckEnabled: Boolean = false,
    val wakeUpCheckMinutes: Int = 5,
    val seriesId: String? = null,
    val seriesPosition: Int = 0,
    val seriesTotal: Int = 0,
    val nextTriggerAt: Long = 0L,
    @ColumnInfo(defaultValue = "SINGLE")
    val type: String = AlarmType.SINGLE.name
) {
    fun getEffectiveTriggerEpochMs(nowMs: Long = System.currentTimeMillis()): Long {
        if (nextTriggerAt > 0L) {
            return nextTriggerAt
        }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (daysOfWeekBitmask == 0) {
            if (target.timeInMillis <= nowMs) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }
        for (dayOffset in 0..7) {
            if (dayOffset > 0) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            val dayOfWeek = target.get(Calendar.DAY_OF_WEEK)
            val bitIndex = (dayOfWeek + 5) % 7
            val isDayActive = (daysOfWeekBitmask and (1 shl bitIndex)) != 0
            if (isDayActive && target.timeInMillis > nowMs) {
                return target.timeInMillis
            }
        }
        if (target.timeInMillis <= nowMs) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }
}

val AlarmEntity.isNap: Boolean get() = type == AlarmType.NAP.name

val AlarmEntity.isTest: Boolean get() = type == AlarmType.TEST.name

val AlarmEntity.isRepeating: Boolean get() = daysOfWeekBitmask != 0
