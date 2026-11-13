package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import java.time.Instant

/** 지난 크루런 한 건. [distance]가 null이면 거리 제한 없음, [runnerCount]는 함께 달린 인원(취소면 0). */
data class PastCrewRun(
    val postId: Long,
    val title: String,
    val scheduledAt: Instant,
    val distance: Distance?,
    val status: CrewRunStatus,
    val goingCount: Int,
    val runnerCount: Int
)
