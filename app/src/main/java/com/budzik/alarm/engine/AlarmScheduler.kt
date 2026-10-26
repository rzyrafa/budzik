package com.budzik.alarm.engine

import com.budzik.alarm.data.local.AlarmEntity

interface AlarmScheduler {
    fun schedule(alarm: AlarmEntity)
    fun cancel(alarmId: Long)
    fun cancelAll(alarmIds: List<Long>)
    fun rescheduleAll(alarms: List<AlarmEntity>)
}
