package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText

data class RecordBattleUiState(
    val isLoading: Boolean = true,
    val candidates: List<CandidateUi> = emptyList(),
    /** 유명인 거리 거르기(전체·5km·10km·하프·풀). 나와의 싸움은 쓰지 않는다. */
    val filterIndex: Int = 0,
    /** 고른 후보. 나와의 싸움은 바로 아래 "대결 시작"에, 유명인은 확인 대화상자에 쓴다. */
    val selected: CandidateUi? = null,
    val isConfirming: Boolean = false,
    val isStarting: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
)
