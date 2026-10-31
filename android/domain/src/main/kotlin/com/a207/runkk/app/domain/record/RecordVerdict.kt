package com.a207.runkk.app.domain.record

/** 기록 목록 행의 대결 승패(서버 RunSummaryDto.verdict, D-279). 판정 보류면 null이다. */
enum class RecordVerdict {
    WIN,
    LOSE,
    DRAW
}
