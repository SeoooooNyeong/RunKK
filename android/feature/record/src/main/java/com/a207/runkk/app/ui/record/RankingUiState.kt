package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.Ranking

data class RankingUiState(
    val scope: RankingScope = RankingScope.PERSONAL,
    val period: AggregationPeriod = AggregationPeriod.WEEKLY,
    val isLoading: Boolean = true,
    val ranking: Ranking? = null,
    val error: UiText? = null,
    /** 고른 기간의 집계 기간과 마감까지 남은 시간(랭킹 v3). 읽을 때마다 지금 시각으로 다시 센다. */
    val window: RankingWindow? = null,
    /**
     * 받은 줄 얼굴 그림(얼굴 키 → 층, S15P31A207-300). 받지 못한 얼굴은 빈 목록(기본 머리, 다시 보이면 다시 받는다)이고, 아직 없는
     * 받을 키는 받는 중이다([rankingFaceLoading]).
     */
    val faces: Map<String, List<FaceLayer>> = emptyMap(),
    /**
     * 내 얼굴(내 아바타 얼굴 키·셀카 층, PM 2026-11-15). 내 순위 카드와 목록의 내 줄이 서버 줄 모습(하루 고정)보다 앞서 쓴다. 읽는 중이면
     * [RankingFace.LOADING], 모르면 null(내 순위 카드는 성별 기본 머리, 내 줄은 서버 줄 모습).
     */
    val myFace: RankingFace? = null
) {
    /** TOP3 시상대와 4위부터의 목록(FR-290). 랭킹을 읽기 전이면 null. */
    val podium: RankingPodium?
        get() = ranking?.let { RankingPodium.of(it.entries) }
}

/**
 * 받을 얼굴 키인가(AR-87): 셀카 머리(`head:`)와 셀카 부위 얼굴(`selfie:`)만 받는다. 기본 캐릭터 키, 얼굴 없음, 앱이 모르는
 * 접두어는 받을 것이 없어 바로 성별 기본 얼굴이다. 뷰모델(받기)과 얼굴 원(받는 중 자리)이 이 판정 하나를 함께 쓴다.
 */
internal fun isFetchableFace(faceKey: String?): Boolean =
    faceKey != null && (faceKey.startsWith(HEAD_PREFIX) || faceKey.startsWith(SELFIE_PREFIX))

/** 그 얼굴을 받는 중인가: 받을 키인데 아직 [faces]에 없다. 받기에 실패한 키는 빈 층으로 들어 있어 기본 얼굴이다(AR-88). */
internal fun rankingFaceLoading(faceKey: String?, faces: Map<String, List<FaceLayer>>): Boolean =
    isFetchableFace(faceKey) && faceKey !in faces

private const val HEAD_PREFIX = "head:"
private const val SELFIE_PREFIX = "selfie:"
