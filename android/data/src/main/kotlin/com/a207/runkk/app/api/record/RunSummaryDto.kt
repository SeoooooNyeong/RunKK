package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

/**
 * 기록 요약 한 줄(서버 RecordDtos.RunSummaryDto). v0.14 목록 행 필드(battleType·verdict·pending·newRecord·newRecords·routeShape,
 * 서버 D-233·D-234·D-292)는 옛 서버가 주지 않을 수 있어 모두 기본값을 둔다(D-289). routeShape는 [[x,y]…](0~1, 최대 24점)이다.
 */
@Serializable
internal data class RunSummaryDto(
    val recordId: String,
    val startedAt: String,
    val distanceMeters: Long,
    val elapsedSeconds: Long,
    val averagePaceSecondsPerKm: Int? = null,
    val valid: Boolean,
    val mode: String,
    val cardKind: String,
    val battleType: String? = null,
    val verdict: String? = null,
    val pending: Boolean = false,
    val newRecord: Boolean = false,
    val newRecords: List<NewRecordDto>? = null,
    val routeShape: List<List<Double>>? = null
) {
    /** 신기록 종류(서버 NewRecordDto). kind는 LONGEST·BASE_DISTANCE, distance는 StandardDistance 이름(LONGEST면 null). */
    @Serializable
    data class NewRecordDto(val kind: String? = null, val distance: String? = null)
}
