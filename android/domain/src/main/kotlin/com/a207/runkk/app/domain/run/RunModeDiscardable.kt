package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.RunMode

/**
 * 측정 화면에서 취소(기록을 남기지 않는 종료)할 수 있는 모드인가. 자유런만 참이다. 대결(셀프·셀럽 대결 포함)·크루런은 상대·서버에
 * 보고하는 포기 흐름이 따로 있어 취소하지 않는다. RunSession.discard가 이것만 읽는다.
 */
val RunMode.isDiscardable: Boolean
    get() = this == RunMode.FREE
