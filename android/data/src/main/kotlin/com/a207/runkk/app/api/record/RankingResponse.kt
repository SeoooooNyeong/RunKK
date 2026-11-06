package com.a207.runkk.app.api.record

import kotlinx.serialization.Serializable

@Serializable
internal data class RankingResponse(
    val entries: List<Entry> = emptyList(),
    val mine: Entry? = null,
    val gapToNextMeters: Long? = null,
    val aggregatedAt: String? = null,
    val notice: String? = null
) {
    @Serializable
    data class Entry(
        val rank: Int,
        val name: String,
        val distanceMeters: Long,
        /** 개인 랭킹 줄 회원의 표시 성별(MALE·FEMALE, D-327), 크루 줄·옛 서버는 null. */
        val shownGender: String? = null,
        /** 조회 때 회원의 지금 티어(UNRANKED = 배치 중, 05-03 5.7.3.3 P7). 탈퇴·크루 줄·옛 서버는 null. */
        val publicTier: String? = null
    )
}
