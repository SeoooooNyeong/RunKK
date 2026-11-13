package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import java.time.Instant

/**
 * 크루 상세의 최근 마친 크루런 한 건(피그마 F-02 "10.21 (수) 저녁 7:30", "[n]명 참여 · 총 [n]km", contract-W4 1절).
 * 참가자 이름 같은 개인 정보는 없다(결정 C9). [scheduledAt]은 약속 시각이다(실제 출발은 3초 뒤).
 */
data class RecentCrewRun(
    val postId: Long,
    val scheduledAt: Instant,
    val participantCount: Int,
    val totalDistance: Distance
)
