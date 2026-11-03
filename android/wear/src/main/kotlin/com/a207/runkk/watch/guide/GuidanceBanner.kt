package com.a207.runkk.watch.guide

import java.time.Duration
import java.time.Instant

/** 측정 화면 위에 잠깐 띄우는 안내 문구 하나(3.2, FR-254). [shownAt]부터 2초(제안) 보인다. */
data class GuidanceBanner(val text: String, val shownAt: Instant) {
    /** [now]가 띄운 뒤 2초 이상 지났으면 참(정각 포함). */
    fun isExpired(now: Instant): Boolean = !now.isBefore(shownAt.plus(SHOWN_FOR))

    companion object {
        /** 화면 문구를 보이는 시간(FR-254 제안 2초). */
        val SHOWN_FOR: Duration = Duration.ofSeconds(2)
    }
}
