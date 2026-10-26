package com.budzik.alarm.engine

interface AlarmEnforcementController {
    fun startRinging(alarmId: Long)
    fun stopRinging()
    fun enforceMaxVolume()
    fun isRebootTrapActive(): Boolean
}
