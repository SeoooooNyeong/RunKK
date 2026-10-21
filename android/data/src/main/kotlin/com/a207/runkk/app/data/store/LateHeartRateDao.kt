package com.a207.runkk.app.data.store

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/** 늦은 심박 항목 표 접근(D-77). RoomPendingRunRepository만 쓴다. */
@Dao
internal interface LateHeartRateDao {
    @Upsert
    suspend fun upsert(entity: LateHeartRateEntity)

    @Query("SELECT * FROM late_heart_rate ORDER BY savedAtMillis, recordId")
    suspend fun all(): List<LateHeartRateEntity>

    @Query("SELECT * FROM late_heart_rate WHERE recordId = :recordId")
    suspend fun find(recordId: String): LateHeartRateEntity?

    @Query("DELETE FROM late_heart_rate WHERE recordId = :recordId")
    suspend fun delete(recordId: String)

    @Query("DELETE FROM late_heart_rate")
    suspend fun deleteAll()
}
