package com.a207.runkk.app.data.store

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 미전송 기록 Room 데이터베이스(13.2 "저장"). 앱 종료·재시작에도 남는다(FR-039).
 * 판 2: 머리 정보 없는 기록의 늦은 심박 항목 표(late_heart_rate, D-77)를 더했다. 표만 늘어 자동 이전으로 옮긴다.
 */
@Database(
    entities = [PendingRunEntity::class, LateHeartRateEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
internal abstract class PendingRunDatabase : RoomDatabase() {
    abstract fun pendingRunDao(): PendingRunDao

    abstract fun lateHeartRateDao(): LateHeartRateDao

    companion object {
        const val NAME = "runkk_pending_runs.db"
    }
}
