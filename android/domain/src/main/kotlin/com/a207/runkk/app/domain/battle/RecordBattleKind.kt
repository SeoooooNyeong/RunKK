package com.a207.runkk.app.domain.battle

/**
 * 기록 대결 종류(셀프런·셀럽런, FR-258~FR-260). 상대 기록 미리 받기(GET /battles/record-tracks?kind=)의 질의 값이고,
 * 이름은 서버 BattleType의 상수 SELF·CELEBRITY와 같다(contract-v2 2절).
 */
enum class RecordBattleKind {
    SELF,
    CELEBRITY
}
