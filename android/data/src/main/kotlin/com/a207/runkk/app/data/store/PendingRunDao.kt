package com.a207.runkk.app.data.store

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** 미전송 기록 표 접근(6.1.14). RoomPendingRunRepository만 쓴다(화면은 PendingRunRepository 포트로 읽는다, 13.2). */
@Dao
internal interface PendingRunDao {
    @Upsert
    suspend fun upsert(entity: PendingRunEntity)

    @Query("SELECT * FROM pending_run WHERE sentAtMillis IS NULL ORDER BY savedAtMillis, recordId")
    suspend fun pending(): List<PendingRunEntity>

    @Query("SELECT * FROM pending_run WHERE sentAtMillis IS NULL ORDER BY savedAtMillis, recordId")
    fun observePending(): Flow<List<PendingRunEntity>>

    @Query("SELECT * FROM pending_run WHERE recordId = :recordId")
    suspend fun find(recordId: String): PendingRunEntity?

    @Query("UPDATE pending_run SET route = :empty")
    suspend fun replaceRoutes(empty: ByteArray)

    @Query("UPDATE pending_run SET heartRates = :empty")
    suspend fun replaceHeartRates(empty: ByteArray)

    @Query("DELETE FROM pending_run WHERE sentAtMillis IS NOT NULL AND sentAtMillis < :before")
    suspend fun deleteSentBefore(before: Long)

    @Query("DELETE FROM pending_run")
    suspend fun deleteAll()
}
