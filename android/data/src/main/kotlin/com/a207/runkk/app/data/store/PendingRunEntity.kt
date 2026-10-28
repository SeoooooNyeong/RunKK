package com.a207.runkk.app.data.store

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 미전송 러닝 기록 한 행(6.1.14 data.store). 경로·불안정 구간·심박·정지 구간은 압축 BLOB(PendingRunMapper)이다.
 * 판 3에서 정지 구간(pauses, 자유런만, FR-194)을 더했다. 판 2에서 옮긴 행은 빈 BLOB(정지 없음)이다.
 * sentAtMillis가 null이면 미전송 기록이고, 값이 있으면 이미 보낸 기록의 머리 정보다(경로·심박 없이 최근 보낸 30건만 남긴다, D-77).
 * 머리 정보는 그 기록에 늦게 온 심박(FR-043)을 다시 보내려고 남긴다(RoomPendingRunRepository.appendHeartRates).
 * 이 형식은 data.store 밖으로 나가지 않는다.
 */
@Entity(tableName = "pending_run")
internal class PendingRunEntity(
    @PrimaryKey val recordId: String,
    val mode: String,
    val cardKind: String,
    val battleId: Long?,
    val crewRunPostId: Long?,
    val startedAtMillis: Long,
    val elapsedMillis: Long,
    val distanceMeters: Long,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val route: ByteArray,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val unstable: ByteArray,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val heartRates: ByteArray,
    val uploadState: String,
    val savedAtMillis: Long,
    val sentAtMillis: Long?,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB, defaultValue = "x''") val pauses: ByteArray
) {
    fun copy(heartRates: ByteArray = this.heartRates, sentAtMillis: Long? = this.sentAtMillis): PendingRunEntity =
        PendingRunEntity(
            recordId = recordId,
            mode = mode,
            cardKind = cardKind,
            battleId = battleId,
            crewRunPostId = crewRunPostId,
            startedAtMillis = startedAtMillis,
            elapsedMillis = elapsedMillis,
            distanceMeters = distanceMeters,
            route = route,
            unstable = unstable,
            heartRates = heartRates,
            uploadState = uploadState,
            savedAtMillis = savedAtMillis,
            sentAtMillis = sentAtMillis,
            pauses = pauses
        )

    /** 보낸 기록의 머리 정보: 경로·불안정 구간·심박·정지 구간을 비우고 보낸 시각을 남긴다. */
    fun toSentHeader(sentAt: Long, empty: ByteArray): PendingRunEntity = PendingRunEntity(
        recordId = recordId,
        mode = mode,
        cardKind = cardKind,
        battleId = battleId,
        crewRunPostId = crewRunPostId,
        startedAtMillis = startedAtMillis,
        elapsedMillis = elapsedMillis,
        distanceMeters = distanceMeters,
        route = empty,
        unstable = empty,
        heartRates = empty,
        uploadState = uploadState,
        savedAtMillis = savedAtMillis,
        sentAtMillis = sentAt,
        pauses = empty
    )
}
