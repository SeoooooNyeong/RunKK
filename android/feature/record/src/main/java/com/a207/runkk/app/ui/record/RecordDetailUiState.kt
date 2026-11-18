package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.record.RecordDetail

/**
 * 기록 상세(G-9). [isConfirmingDelete]는 삭제 확인 대화상자, [isDeleting]은 삭제 요청 중, [message]는 한 번 보일 문구, [runBody]는 지표
 * 카드 아래 런바디 진입 카드다.
 */
data class RecordDetailUiState(
    val isLoading: Boolean = true,
    val recordId: String? = null,
    val detail: RecordDetail? = null,
    val error: UiText? = null,
    val isConfirmingDelete: Boolean = false,
    val isDeleting: Boolean = false,
    val message: UiText? = null,
    val runBody: RecordRunBody = RecordRunBody.Hidden
)

/**
 * 기록 상세의 런바디 진입 카드 상태(피그마 G-9·G-9b). 런바디 기능이 꺼졌거나(FEATURE_CLOSED) 유효 러닝이 아니면 [Hidden], 분석 대기·중이면
 * [Analyzing], 결과가 있으면 세 지표의 [Ready], 실패·데이터 부족·일시 장애면 [Unavailable](눌러 들어가면 그 상태 화면을 본다).
 */
sealed interface RecordRunBody {
    data object Hidden : RecordRunBody

    data object Analyzing : RecordRunBody

    data object Unavailable : RecordRunBody

    /** 세 지표 점수(운동 강도·지구력·페이스 안정성). 점수가 없는 지표는 null이라 미리보기에서 뺀다. */
    data class Ready(val intensity: Int?, val endurance: Int?, val stability: Int?) : RecordRunBody
}
