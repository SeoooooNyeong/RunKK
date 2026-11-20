package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText

data class RecordBattleUiState(
    val isLoading: Boolean = true,
    /** 받아 둔 후보 사본을 그리는 중(뒤에서 새로 받는다, S15P31A207-345 2차). 받는 동안에도 로딩 원 대신 목록을 그린다. */
    val isShowingCopy: Boolean = false,
    val candidates: List<CandidateUi> = emptyList(),
    /** 유명인 거리 거르기(전체·5km·10km·하프·풀). 나와의 싸움은 쓰지 않는다. */
    val filterIndex: Int = 0,
    /** 고른 후보. 고르면 화면 아래 시작 버튼(셀프런 "n km 기록 깨러 가기", 셀럽런 "대결하기")이 보인다. */
    val selected: CandidateUi? = null,
    val isStarting: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
)
