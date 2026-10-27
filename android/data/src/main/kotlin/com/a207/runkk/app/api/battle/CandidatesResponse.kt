package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

/** 나와의 싸움·유명인 후보(서버 battle.RecordBattleCandidates). id는 유명인 기록 ID(나와의 싸움은 null). */
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
        val estimated: Boolean = false
    )
}
