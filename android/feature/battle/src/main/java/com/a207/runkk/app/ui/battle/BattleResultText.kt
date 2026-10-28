package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.domain.battle.BattleResult
import java.time.Duration

/**
 * 판정 문구(시안 HiBattleResult "[상대]님보다 42초 먼저 도착했어요!"). 상대·도달 시간을 모르면 일반 문구다.
 * 패배는 상대가 실제로 먼저 도착했을 때(두 도달 시간이 있고 상대가 빠름)만 상대와 시간 차를 쓰고, 그 밖의 패배(내 기록 무효,
 * 포기, 미도착, 이유 모름)는 "대결에서 졌어요"다(배틀 QA 6번).
 */
internal fun verdictMessage(verdict: Verdict?, result: BattleResult?): UiText {
    val name = result?.opponentName?.takeIf { it.isNotBlank() }
    val mine = result?.myReach
    val theirs = result?.opponentReach
    val gap = if (mine != null && theirs != null) gapText(theirs.minus(mine).abs()) else null
    val theyArrivedFirst = mine != null && theirs != null && theirs < mine
    return when (verdict) {
        Verdict.WIN -> when {
            name != null && gap != null -> uiText(R.string.battle_result_win_name_gap, name, gap)
            name != null -> uiText(R.string.battle_result_win_name, name)
            else -> uiText(R.string.battle_result_win)
        }

        Verdict.LOSE ->
            if (name != null && gap != null && theyArrivedFirst) {
                uiText(R.string.battle_result_lose_name_gap, name, gap)
            } else {
                uiText(R.string.battle_result_lose)
            }

        Verdict.DRAW ->
            if (name != null) uiText(R.string.battle_result_draw_name, name) else uiText(R.string.battle_result_draw)

        null -> uiText(R.string.battle_result_checking)
    }
}

/** 시간 차 문구: 42초, 1분 5초. */
internal fun gapText(gap: Duration): UiText {
    val seconds = gap.seconds
    return if (seconds < SECONDS_PER_MINUTE) {
        uiText(R.string.battle_gap_seconds, seconds)
    } else {
        val minutes = seconds / SECONDS_PER_MINUTE
        val rest = seconds % SECONDS_PER_MINUTE
        if (rest ==
            0L
        ) {
            uiText(R.string.battle_gap_minutes, minutes)
        } else {
            uiText(R.string.battle_gap_minutes_seconds, minutes, rest)
        }
    }
}

/** 이력의 기록 차이(본인 − 상대): 음수면 "42초 빠름", 양수면 "12초 느림", 0이면 "같은 기록". */
internal fun gapLabel(gap: Duration): UiText = when {
    gap.isNegative -> uiText(R.string.battle_gap_faster, gapText(gap.abs()))
    gap.isZero -> uiText(R.string.battle_gap_same)
    else -> uiText(R.string.battle_gap_slower, gapText(gap))
}

/** 이번 주 전적 표기: 3승 1패(무승부가 있으면 1무를 더한다). 보드 D4 "[n]승 [n]패". */
internal fun weeklyLabel(weekly: BattleResult.Weekly): UiText = weeklySpoken(weekly)

/** 이번 주 전적 읽기: 3승 1패(무승부가 있으면 1무를 더한다). */
internal fun weeklySpoken(weekly: BattleResult.Weekly): UiText = if (weekly.draws > 0) {
    uiText(R.string.battle_weekly_spoken_draw, weekly.wins, weekly.losses, weekly.draws)
} else {
    uiText(R.string.battle_weekly_spoken, weekly.wins, weekly.losses)
}

private const val SECONDS_PER_MINUTE = 60L
