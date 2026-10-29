package com.a207.runkk.app.domain.record

/**
 * 기록 목록 행의 대결 종류(서버 RunSummaryDto.battleType, D-279). 고스트는 서버가 RANDOM으로 묶어 보낸다(historyGroup).
 * 대결이 아닌 기록은 null이다.
 */
enum class RecordBattleType {
    RANDOM,
    FRIEND,
    SELF,
    CELEBRITY
}
