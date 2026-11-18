package com.a207.runkk.app.domain.record

/**
 * 기록 하나의 런바디 진입 카드 상태를 읽는 포트(기록 상세). 구현은 ui.record.runbody가 런바디 분석 결과(GET /runbody/sessions/{recordId})로
 * 정한다. 기록 상세(ui.record)는 런바디 형식을 모른다.
 */
fun interface RunBodyPreviewSource {
    suspend fun preview(recordId: String): RunBodyPreview
}
