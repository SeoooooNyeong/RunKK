package com.a207.runkk.app.domain.record

/**
 * 기록 상세의 런바디 진입 카드 상태(피그마 G-9·G-9b). 런바디 기능이 꺼졌거나(FEATURE_CLOSED) 유효 러닝이 아니면 [Hidden], 분석 대기·중이면
 * [Analyzing], 결과가 있으면 세 지표의 [Ready], 실패·데이터 부족·일시 장애면 [Unavailable](눌러 들어가면 그 상태 화면을 본다).
 */
sealed interface RunBodyPreview {
    data object Hidden : RunBodyPreview

    data object Analyzing : RunBodyPreview

    data object Unavailable : RunBodyPreview

    /** 세 지표 점수(운동 강도·지구력·페이스 안정성). 점수가 없는 지표는 null이라 미리보기에서 뺀다. */
    data class Ready(val intensity: Int?, val endurance: Int?, val stability: Int?) : RunBodyPreview
}
