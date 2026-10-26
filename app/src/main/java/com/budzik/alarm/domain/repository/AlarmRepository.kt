package com.budzik.alarm.domain.repository

import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.data.local.RingingStateEntity
import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    fun getAllAlarms(): Flow<List<AlarmEntity>>
    suspend fun getAlarmById(id: Long): AlarmEntity?
    suspend fun getEnabledAlarms(): List<AlarmEntity>
    suspend fun insertAlarm(alarm: AlarmEntity): Long
    suspend fun updateAlarm(alarm: AlarmEntity)
    suspend fun deleteAlarm(id: Long)
    suspend fun deleteAllAlarms()

    suspend fun getRingingState(): RingingStateEntity?
    suspend fun setRingingState(state: RingingStateEntity)
    suspend fun clearRingingState()
}
