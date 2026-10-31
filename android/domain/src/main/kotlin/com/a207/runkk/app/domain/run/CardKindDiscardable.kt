package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind

/**
 * 기록을 남기지 않고 끝낼 수 있는 카드 종류인가(D-266). 자유런 취소와 셀프런·셀럽런 그만두기는 참이고, 배틀런(랜덤·고스트·친구)과
 * 크루런은 상대·서버가 결과를 남기므로 거짓이다. RunSession.discard가 이것만 읽는다.
 */
val CardKind.isDiscardable: Boolean
    get() = this == CardKind.FREE_RUN || this == CardKind.SELF_RUN || this == CardKind.CELEB_RUN
