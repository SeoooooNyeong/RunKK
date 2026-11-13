package com.a207.runkk.app.data.store

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 미전송 기록 Room 데이터베이스(13.2 "저장"). 앱 종료·재시작에도 남는다(FR-039).
 * 판 2: 머리 정보 없는 기록의 늦은 심박 항목 표(late_heart_rate, D-77)를 더했다. 표만 늘어 자동 이전으로 옮긴다.
 * 판 3: pending_run에 정지 구간 칼럼(pauses, 기본 빈 BLOB)을 더했다(FR-194, CR34-14). 칼럼만 늘어 자동 이전으로 옮긴다.
 * 판 4: pending_run에 시작 기기 칼럼(startDevice, 기본 PHONE)을 더했다(W23, D-378). 칼럼만 늘어 자동 이전으로 옮긴다.
 * 판 5: pending_run에 서버 거부 사유 칼럼(rejectedReason, null 허용)을 더했다(R12 D2-b). 칼럼만 늘어 자동 이전으로 옮긴다.
 * 판 6: pending_run에 움직임 표본 칼럼(motions, 기본 빈 BLOB)을 더했다(S15P31A207-242). 칼럼만 늘어 자동 이전으로 옮긴다.
 */
@Database(
    entities = [PendingRunEntity::class, LateHeartRateEntity::class],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6)
    ]
)
internal abstract class PendingRunDatabase : RoomDatabase() {
    abstract fun pendingRunDao(): PendingRunDao

    abstract fun lateHeartRateDao(): LateHeartRateDao

    companion object {
        const val NAME = "runkk_pending_runs.db"
    }
}
