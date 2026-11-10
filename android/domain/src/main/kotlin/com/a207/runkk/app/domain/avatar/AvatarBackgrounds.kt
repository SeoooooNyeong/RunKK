package com.a207.runkk.app.domain.avatar

import com.a207.runkk.app.core.model.Distance
import java.time.Instant

/**
 * 아바타 배경 목록(서버 BackgroundsResponse, FR-201, FR-202): 고른 배경 키(null이면 기본)와 랜드마크 배경마다 해금 여부.
 * [Item.stage]는 서버 단계 이름(DOMESTIC·EAST_ASIA·INTERCONTINENTAL), [Item.distance]는 해금에 필요한 누적 거리다.
 */
data class AvatarBackgrounds(
    val selectedKey: String?,
    val items: List<Item>,
    /** 내 누적 유효 거리(W8). */
    val totalDistance: Distance = Distance(0)
) {
    data class Item(
        val key: String,
        val name: String,
        val stage: String,
        val distance: Distance,
        val isUnlocked: Boolean,
        val unlockedAt: Instant?,
        /** 해금까지 남은 거리(해금했으면 0, W8 "n km 더 달리면 해금"). */
        val remaining: Distance = Distance(0)
    )
}
