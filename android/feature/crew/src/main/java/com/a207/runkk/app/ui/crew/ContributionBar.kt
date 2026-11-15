package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.RaceStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

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

/**
 * 목표 진행률(정수 %). 100을 넘으면 넘는 값 그대로 보인다(105% 등, 사용자 결정 C2·D-657). 막대 채움은 [goalFillFraction]이
 * 1에서 자른다. 목표가 없으면 null.
 */
internal fun goalProgressLabel(race: RaceStatus): Int? = race.goalProgressPercent?.coerceAtLeast(0)

/** 전체 진행 막대(barCrew) 채움 0~1: 목표가 있으면 min(1, 진행률 ÷ 100), 목표가 없으면 꽉 찬다(피그마 F-06). */
internal fun goalFillFraction(race: RaceStatus): Float =
    goalProgressLabel(race)?.let { (it / PERCENT.toFloat()).coerceIn(0f, 1f) } ?: if (race.goal == null) 1f else 0f

/** 내 기여 구간(보라) 채움 0~1: 전체 채움 × 내 기여율(contract-W5 W5-D2). */
internal fun myFillFraction(race: RaceStatus): Float =
    goalFillFraction(race) * (myShareLabel(race) / PERCENT.toFloat()).coerceIn(0f, 1f)

/**
 * 내 기여율 정수 %(서버 myShare = ⌊내 기여 ÷ 합계 × 100⌋, 0~100 정수). 모르면 0. 1 미만 소수(옛 시험 자료의 0~1 비율)만 비율로 읽는다:
 * shareLabel처럼 1.0을 100%로 읽으면 서버의 1%가 100%가 되기 때문이다.
 */
internal fun myShareLabel(race: RaceStatus): Int {
    val share = race.myShare ?: return 0
    val percent = if (share > 0.0 && share < 1.0) share * PERCENT else share
    return percent.toInt().coerceIn(0, PERCENT)
}

/**
 * 「참여 n명」: 이번 주 기여 거리가 0보다 큰 줄 수(탈퇴자 기여는 한 줄). 축하 카드 participantCount와 같은 정의다(contract-W5 W5-D1).
 */
internal fun participantCount(race: RaceStatus): Int = race.contributors.count { it.distance.meters > 0 }

/** 그 레이스 주의 월요일. 마감(일요일 24시 = 다음 주 월요일 0시)에서 1초 앞으로 당겨 그 주 안으로 둔다. */
internal fun raceWeekStart(race: RaceStatus, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    race.closesAt.minusSeconds(1).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private const val PERCENT = 100
