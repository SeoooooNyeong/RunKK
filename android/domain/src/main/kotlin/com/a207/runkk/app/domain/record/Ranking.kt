package com.a207.runkk.app.domain.record

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** 랭킹(서버 analysis.RankingView, FR-070·FR-071). 집계 스냅숏이 아직 없으면 [aggregatedAt]이 null이다("집계 준비 중"). */
data class Ranking(
    val entries: List<Entry>,
    val mine: Entry?,
    val gapToNext: Distance?,
    val aggregatedAt: Instant?,
    val notice: String?
) {
    /**
     * 한 줄. [shownGender]는 그 회원의 표시 성별(D-327: 다른 러너의 캐릭터를 이 값으로 그린다), 크루 줄·옛 서버는 null.
     * [publicTier]는 조회 때 회원의 지금 티어(서버 Tier 이름, UNRANKED = 배치 중, D-486)이고 탈퇴·크루 줄·옛 서버는 null이다.
     * 임시 티어는 담기지 않는다(본인 줄도 공개 티어). [look]은 그 회원의 아바타 모습(S15P31A207-300)이고 크루 줄·탈퇴 회원·옛 서버는
     * null이다(성별 기본 모습으로 그린다).
     */
    data class Entry(
        val rank: Int,
        val name: String,
        val distance: Distance,
        val shownGender: Gender? = null,
        val publicTier: String? = null,
        val look: RunnerLook? = null
    )
}
