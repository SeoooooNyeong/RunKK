package com.a207.runkk.app.ui.battle

import androidx.annotation.StringRes
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.domain.battle.BattleResult
import java.time.Duration

/**
 * 판정 문구(D-283): 상대 종류 × 판정 표 하나. 상대 이름 꾸밈("님")은 사람 상대(랜덤·친구)만 붙인다(OpponentKind.isPerson).
 * - 배틀런·친구 승 "바람처럼님보다 42초 먼저 도착했어요", 패는 상대가 실제로 먼저 도착했을 때만 상대와 시간 차를 쓰고(D-201) 그 밖은
 *   "대결에서 졌어요"(배틀 QA 6번).
 * - 셀프런 승 "10월 2일의 나보다 14초 빨랐어요", 패 "10월 2일의 나보다 14초 늦었어요".
 * - 셀럽런 승 "김선교 대공보다 42초 먼저 도착했어요", 패 "김선교 대공보다 2분 20초 늦게 도착했어요".
 * 상대·도달 시간을 모르면 일반 문구다.
 */
internal fun verdictMessage(kind: OpponentKind, verdict: Verdict?, result: BattleResult?): UiText {
    val name = result?.opponentName?.takeIf { it.isNotBlank() }
    val mine = result?.myReach
    val theirs = result?.opponentReach
    val gap = if (mine != null && theirs != null) gapText(theirs.minus(mine).abs()) else null
    val theyArrivedFirst = mine != null && theirs != null && theirs < mine
    return when (verdict) {
        Verdict.WIN -> when {
            name != null && gap != null -> uiText(winRes(kind), name, gap)
            name != null && kind.isPerson -> uiText(R.string.battle_result_win_name, name)
            else -> uiText(R.string.battle_result_win)
        }

        Verdict.LOSE -> when {
            name == null || gap == null || !theyArrivedFirst -> uiText(R.string.battle_result_lose)
            else -> uiText(loseRes(kind), name, gap)
        }

        Verdict.DRAW ->
            if (name != null &&
                kind.isPerson
            ) {
                uiText(R.string.battle_result_draw_name, name)
            } else {
                uiText(R.string.battle_result_draw)
            }

        null -> uiText(R.string.battle_result_checking)
    }
}

@StringRes
private fun winRes(kind: OpponentKind): Int = when (kind) {
    OpponentKind.RANDOM, OpponentKind.FRIEND -> R.string.battle_result_win_name_gap
    OpponentKind.SELF -> R.string.battle_result_win_self
    OpponentKind.CELEBRITY -> R.string.battle_result_win_celeb
}

@StringRes
private fun loseRes(kind: OpponentKind): Int = when (kind) {
    OpponentKind.RANDOM, OpponentKind.FRIEND -> R.string.battle_result_lose_name_gap
    OpponentKind.SELF -> R.string.battle_result_lose_self
    OpponentKind.CELEBRITY -> R.string.battle_result_lose_celeb
}

/** 결과 쪽 큰 제목(D-283): 승리!·패배·무승부, 셀프런 승리는 신기록!. */
@StringRes
internal fun headlineRes(kind: OpponentKind, verdict: Verdict): Int = if (kind == OpponentKind.SELF &&
    verdict == Verdict.WIN
) {
    R.string.battle_result_headline_new_record
} else {
    verdict.headlineRes
}

/** 결과 화면 제목(D-283): 대결 결과(랜덤·친구), 셀프런 결과, 셀럽런 결과. */
@StringRes
internal fun resultTitleRes(kind: OpponentKind): Int = when (kind) {
    OpponentKind.RANDOM, OpponentKind.FRIEND -> R.string.battle_result_title
    OpponentKind.SELF -> R.string.battle_result_title_self
    OpponentKind.CELEBRITY -> R.string.battle_result_title_celeb
}

/** 시간 차 문구: 42초, 1분, 1분 5초. 시간 단위로 올리지 않는다(60분). */
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

private const val SECONDS_PER_MINUTE = 60L
