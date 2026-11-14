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
        val publicTier: String? = null,
        /** 그 회원의 아바타 모습(S15P31A207-300). 크루 줄·탈퇴 회원·옛 서버는 null(성별 기본 모습). */
        val look: Look? = null
    )

    /**
     * 랭킹 줄 모습(서버 랭킹 Entry.look). [outfit]은 착용 부위(ItemSlot 이름) → 상점 아이템 그림 키이고 안 입은 부위는 키가 없다.
     * [bodyVariant]·[runAmount]는 /avatar/runner와 같은 규칙(0..2, 0..100)이다.
     */
    @Serializable
    data class Look(
        val faceKey: String? = null,
        val outfit: Map<String, String> = emptyMap(),
        val bodyVariant: Int = 0,
        val runAmount: Int = 0
    )
}
