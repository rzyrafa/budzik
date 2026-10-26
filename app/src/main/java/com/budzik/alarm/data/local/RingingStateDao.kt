package com.budzik.alarm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RingingStateDao {
    @Query("SELECT * FROM ringing_state WHERE id = 1")
    suspend fun getRingingState(): RingingStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setRingingState(state: RingingStateEntity)

    @Query("DELETE FROM ringing_state")
    suspend fun clearRingingState()
}
