package com.a207.runkk.app.domain.run

import java.time.Duration

/** 정지 한도(FR-195). SRS (제안) 값이라 소유 클래스의 상수로 둔다(D-113). 지금 정지 하나의 길이로 센다. */
object PauseLimit {
    /** 자동 종료 예고(PAUSE_AUTO_END_SOON) 시점. */
    val WARN_AFTER: Duration = Duration.ofMinutes(55)

    /** 자동 종료(PAUSE_AUTO_ENDED) 시점. */
    val END_AFTER: Duration = Duration.ofMinutes(60)
}
