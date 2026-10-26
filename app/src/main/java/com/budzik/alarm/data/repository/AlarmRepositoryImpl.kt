package com.budzik.alarm.data.repository

import com.budzik.alarm.data.local.AlarmDao
import com.budzik.alarm.data.local.AlarmEntity
import com.budzik.alarm.data.local.RingingStateDao
import com.budzik.alarm.data.local.RingingStateEntity
import com.budzik.alarm.domain.repository.AlarmRepository
import kotlinx.coroutines.flow.Flow

class AlarmRepositoryImpl(
    private val alarmDao: AlarmDao,
    private val ringingStateDao: RingingStateDao
) : AlarmRepository {

    override fun getAllAlarms(): Flow<List<AlarmEntity>> = alarmDao.getAllAlarms()

    override suspend fun getAlarmById(id: Long): AlarmEntity? = alarmDao.getAlarmById(id)

    override suspend fun getEnabledAlarms(): List<AlarmEntity> = alarmDao.getEnabledAlarms()

    override suspend fun insertAlarm(alarm: AlarmEntity): Long = alarmDao.insertAlarm(alarm)

    override suspend fun updateAlarm(alarm: AlarmEntity) = alarmDao.updateAlarm(alarm)

    override suspend fun deleteAlarm(id: Long) = alarmDao.deleteAlarm(id)

    override suspend fun deleteAllAlarms() = alarmDao.deleteAllAlarms()

    override suspend fun getRingingState(): RingingStateEntity? = ringingStateDao.getRingingState()

    override suspend fun setRingingState(state: RingingStateEntity) = ringingStateDao.setRingingState(state)

    override suspend fun clearRingingState() = ringingStateDao.clearRingingState()
}
