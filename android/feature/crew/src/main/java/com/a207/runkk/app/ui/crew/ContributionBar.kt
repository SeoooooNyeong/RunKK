package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.RaceStatus

/**
 * 기여도 보드의 막대 하나(FR-140): 표시명, 기여 거리, 가장 많이 달린 크루원 대비 막대 길이(0~1), 본인 여부. 순위 숫자는 두지 않는다.
 */
data class ContributionBar(val displayName: String, val distance: Distance, val fraction: Float, val isMe: Boolean)

/** 기여 거리 내림차순 막대(FR-140, TC-7497). 같은 거리는 받은 순서대로 둔다. */
internal fun contributionBars(race: RaceStatus): List<ContributionBar> {
    val sorted = race.contributors.sortedByDescending { it.distance.meters }
    val max = sorted.firstOrNull()?.distance?.meters?.takeIf { it > 0 } ?: return sorted.map {
        ContributionBar(it.displayName, it.distance, 0f, it.isMe)
    }
    return sorted.map { ContributionBar(it.displayName, it.distance, it.distance.meters.toFloat() / max, it.isMe) }
}

/** 월간 기여도 막대(와이어 E2): 크루 랭킹 응답을 거리 내림차순 막대로. 본인은 표시명이 [meName]과 같은 줄이다. */
internal fun rankingBars(entries: List<CrewRanking.Entry>, meName: String?): List<ContributionBar> {
    val sorted = entries.sortedByDescending { it.distance.meters }
    val max = sorted.firstOrNull()?.distance?.meters?.takeIf { it > 0 }
    return sorted.map {
        ContributionBar(
            it.displayName,
            it.distance,
            max?.let { m -> it.distance.meters.toFloat() / m } ?: 0f,
            isMe = meName != null && it.displayName == meName
        )
    }
}

/** 목표 진행률(정수 %). 합산 거리가 목표를 넘어도 100에서 자른다(FR-140, TF-VB-22). 목표가 없으면 null. */
internal fun goalProgressLabel(race: RaceStatus): Int? = race.goalProgressPercent?.coerceIn(0, MAX_PERCENT)

private const val MAX_PERCENT = 100
