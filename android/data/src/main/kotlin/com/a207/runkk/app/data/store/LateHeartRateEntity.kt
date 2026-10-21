package com.a207.runkk.app.data.store

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 머리 정보 없는 기록의 늦은 심박 항목 한 행(D-77, domain.run.LateHeartRates). 심박은 압축 BLOB(PendingRunMapper)이다.
 * 기기의 심박 원자료라 심박 동의 철회 파기(purgeHeartRates)와 탈퇴(clear) 때 지운다(FR-029, TF-VA-31).
 * 이 형식은 data.store 밖으로 나가지 않는다.
 */
@Entity(tableName = "late_heart_rate")
internal class LateHeartRateEntity(
    @PrimaryKey val recordId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val heartRates: ByteArray,
    val savedAtMillis: Long
)
