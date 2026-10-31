package com.a207.runkk.app.domain.record

/**
 * 기록 목록 행의 신기록 종류 하나(서버 NewRecordDto, D-292·D-295). [distance]는 서버 StandardDistance 이름(KM1~FULL)이고
 * [Kind.LONGEST]면 null이다.
 */
data class NewRecordMark(val kind: Kind, val distance: String?) {
    enum class Kind {
        LONGEST,
        BASE_DISTANCE
    }
}
