package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

/**
 * 나와의 싸움·유명인 후보(서버 battle.RecordBattleCandidates). id는 유명인 기록 ID(나와의 싸움은 null). myBestSeconds는 유명인 후보의
 * 그 거리 내 최고 기록(초, v0.14 서버 D-238)이고 없으면 null이다. celebrityFigure는 셀럽 그림 키(lee·hwang·son, 나와의 싸움·옛 서버면 null)다.
 */
@Serializable
internal data class CandidatesResponse(val candidates: List<Candidate> = emptyList()) {
    @Serializable
    data class Candidate(
        val id: Long? = null,
        val displayName: String,
        val distanceMeters: Long,
        val finishSeconds: Long? = null,
        val recordedOn: String? = null,
        val sourceLabel: String? = null,
        val estimated: Boolean = false,
        val myBestSeconds: Long? = null,
        val celebrityFigure: String? = null
    )
}
