package com.a207.runkk.app.data.store

import androidx.room.Database
import androidx.room.RoomDatabase

/** 미전송 기록 Room 데이터베이스(13.2 "저장"). 앱 종료·재시작에도 남는다(FR-039). */
@Database(entities = [PendingRunEntity::class], version = 1, exportSchema = true)
internal abstract class PendingRunDatabase : RoomDatabase() {
    abstract fun pendingRunDao(): PendingRunDao

    companion object {
        const val NAME = "runkk_pending_runs.db"
    }
}
