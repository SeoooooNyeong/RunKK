package com.a207.runkk.app.ui.home

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.domain.record.Dashboard
import com.a207.runkk.app.domain.run.FreeRunGoal
import java.time.LocalDate

data class HomeUiState(
    val isLoading: Boolean = true,
    val dashboard: Dashboard? = null,
    /** 자유런 카드의 목표(코치 계획 따르기 켬 + 오늘 계획 → 오늘 계획, 그 밖 → 자유런 설정 목표, FR-048). */
    val freeRunGoal: FreeRunGoal = FreeRunGoal.NONE,
    val pendingCount: Int = 0,
    /** 고른 랜드마크 배경 키(FR-202). 우리 집 배경으로 깐다. null이면 기본 배경이다. */
    val backgroundKey: String? = null,
    /** 러닝 설정의 음성·진동 안내(자유런 칩 "음성 켬"·"진동 켬"). 읽지 못하면 서버 기본값(켬)이다. */
    val isRunVoiceOn: Boolean = true,
    val isRunVibrationOn: Boolean = true,
    val mode: HomeRunMode = HomeRunMode.FREE,
    val error: UiText? = null,
    val today: LocalDate? = null,
    val header: HomeHeader = HomeHeader(),
    val isHeaderLoading: Boolean = true,
    /** 아바타 얼굴 키(서버 renderKey: "default:<캐릭터>" 또는 "selfie:…"). 상단 프로필 원과 우리 집 아바타 자리가 그린다. */
    val faceKey: String? = null,
    /** 셀카 얼굴 부위 그림(셀카 얼굴일 때만). 기본 캐릭터도 셀카 그림도 없으면 "아바타" 자리 그림. */
    val faceLayers: List<FaceLayer> = emptyList(),
    /**
     * 배틀런 탭에서 고른 대결 거리(FR-080). 처음엔 대결 설정의 기본 대결 거리(지난번 매칭 거리, FR-109)이고 읽기 전·읽지 못하면 5km다.
     * "배틀런 시작"을 누르면 이 거리로 매칭을 요청한다.
     */
    val battleDistance: Distance = DEFAULT_BATTLE_DISTANCE
) {
    companion object {
        /** 랜덤 대결 거리(FR-080: 1·3·5·10km). */
        val BATTLE_DISTANCES: List<Distance> =
            listOf(Distance(1_000), Distance(3_000), Distance(5_000), Distance(10_000))

        /** 기본 대결 거리의 기본값(FR-109). */
        val DEFAULT_BATTLE_DISTANCE: Distance = Distance(5_000)
    }
}
