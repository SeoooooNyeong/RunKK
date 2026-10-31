package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.RunMode

/**
 * 일시정지할 수 있는 모드인가(FR-194). 자유런만 참이다. 앱에서 "정지 가능 모드"를 답하는 곳은 이 속성 하나다(D-137):
 * RunSession.pause와 워치 표시의 isPausable이 이것만 읽는다.
 */
val RunMode.isPausable: Boolean
    get() = this == RunMode.FREE
