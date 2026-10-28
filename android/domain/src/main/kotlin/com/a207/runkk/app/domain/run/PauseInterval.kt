package com.a207.runkk.app.domain.run

import java.time.Instant

/** 닫힌 정지 구간(FR-194). 서버 running.PauseInterval·JSON `pauses[{from, to}]`와 같은 항목이다. */
data class PauseInterval(val from: Instant, val to: Instant)
