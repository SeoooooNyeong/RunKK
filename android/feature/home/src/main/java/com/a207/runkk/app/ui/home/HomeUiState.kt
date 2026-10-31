package com.a207.runkk.app.ui.home

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer

/**
 * 홈(A-4, D-276): 상단(닉네임·티어·Lv, 리워드 잔액), 이번 주 거리 막대와 연속 일수, 자유런 카드의 음성·진동, 아바타.
 * 오늘·누적 요약, 랜드마크, 잔디, 횟수 달성률은 v0.14에서 홈에서 뺐다.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val error: UiText? = null,
    val header: HomeHeader = HomeHeader(),
    val isHeaderLoading: Boolean = true,
    /**
     * 본인 요약(성별)을 한 번이라도 읽었는가. 읽기 전에는 아바타 자리를 비운다: 첫 읽기가 실패한 채 남자로 그렸다가 다시 읽은 뒤
     * 여자로 바뀌지 않게 한다(FR-162, 시험 TF-V12-02).
     */
    val isAvatarReady: Boolean = false,
    /** 이번 주 달린 거리(weekly.actualMeters). 읽기 전·목표 없음이면 0이다. */
    val weeklyDistance: Distance = Distance.ZERO,
    /** 이번 주 목표 거리(weekly.goalMeters). 목표가 없거나 옛 서버면 null이다. */
    val weeklyGoal: Distance? = null,
    /** 오늘 기준 연속 주 수(streakWeeks). 홈은 주 단위만 보인다(사용자 요청 2026-10-31). */
    val streakWeeks: Int = 0,
    /** 러닝 설정의 음성·진동 안내(자유런 카드). 읽지 못하면 서버 기본값(켬)이다. */
    val isRunVoiceOn: Boolean = true,
    val isRunVibrationOn: Boolean = true,
    /** 본인 아바타 얼굴 키·부위 그림·배경 키. 아바타 꾸미기(AVATAR_CUSTOMIZE)가 열려 있을 때만 화면이 쓴다(D-254). */
    val faceKey: String? = null,
    val faceLayers: List<FaceLayer> = emptyList(),
    val backgroundKey: String? = null,
    /** 자유런 시작 전 위치 동의 시트(A-6, D-267). */
    val isConsentSheetVisible: Boolean = false,
    /** 한 번 보이고 지우는 안내(위치 동의 실패 등). */
    val message: UiText? = null
) {
    /** 이번 주 목표 대비 달린 비율(0~1). 목표가 없으면 0이다. */
    val weeklyProgress: Float
        get() {
            val goal = weeklyGoal?.meters?.takeIf { it > 0 } ?: return 0f
            return (weeklyDistance.meters.toFloat() / goal).coerceIn(0f, 1f)
        }
}
