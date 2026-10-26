package com.budzik.alarm.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ringing_state")
data class RingingStateEntity(
    @PrimaryKey val id: Int = 1, // Singleton
    val alarmId: Long,
    val isRinging: Boolean,
    val difficulty: String,
    val problemCount: Int,
    val solvedCount: Int
)
