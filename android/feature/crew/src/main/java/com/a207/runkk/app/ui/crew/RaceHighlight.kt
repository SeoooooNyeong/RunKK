package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.crew.RaceStatus

/**
 * 레이스 탭에 새로 반영된 거리 강조(설계 05-32 [임시:D-성-889], [임시:FR-성-369]). [isGoal]이면 이번 주 목표 카드, [rows]의 줄(기여자 키)은
 * 러닝 보드 줄을 2초 강조한다. [added]는 직전 성공 조회보다 늘어난 거리(「+0.84」)이고, 직전 값이 없으면 비어 있다(증가량을 추정하지 않는다).
 */
data class RaceHighlight(val isGoal: Boolean, val rows: Set<String>, val added: Map<String, Distance> = emptyMap())

/** 기여자 한 줄의 키: 회원 ID, 없으면(탈퇴) 표시명. */
internal fun contributorKey(memberId: Long?, displayName: String): String =
    memberId?.let { "m:$it" } ?: "n:$displayName"

/**
 * 직전 성공 조회 [previous]와 새 조회 [latest] 사이의 확정 거리 증가로 강조를 정한다. 같은 스냅숏이면 null(중복 강조 금지). 직전 값이
 * 없으면 결과 복귀([hasReturn])일 때만 목표 카드와 내 줄을 강조하고 증가량은 비운다. 다른 주(합계가 줄었다)는 강조하지 않는다.
 */
internal fun raceHighlightOf(previous: RaceStatus?, latest: RaceStatus, hasReturn: Boolean): RaceHighlight? {
    if (previous == null) {
        if (!hasReturn) return null
        val mine = latest.contributors.filter { it.isMe }.map { contributorKey(it.memberId, it.displayName) }.toSet()
        return RaceHighlight(isGoal = true, rows = mine)
    }
    val before = previous.contributors.associate { contributorKey(it.memberId, it.displayName) to it.distance.meters }
    val added = latest.contributors.mapNotNull { c ->
        val key = contributorKey(c.memberId, c.displayName)
        val gain = c.distance.meters - (before[key] ?: 0L)
        if (gain > 0) key to Distance(gain) else null
    }.toMap()
    val isGoal = latest.total.meters > previous.total.meters
    if (added.isEmpty() && !isGoal) return null
    return RaceHighlight(isGoal = isGoal, rows = added.keys, added = added)
}
