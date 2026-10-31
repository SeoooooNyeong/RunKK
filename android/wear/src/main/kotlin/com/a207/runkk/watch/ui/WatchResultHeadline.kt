package com.a207.runkk.watch.ui

import android.content.res.Resources
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.RecordBattleKind
import com.a207.runkk.app.domain.run.FinishOutcome

/**
 * 워치 결과 화면의 머리말과 결과 문구(3.5, D-381, D-388, FR-249 표). 결과 종류마다 객체가 자기 머리말([title])과 문구([subtitle])를 낸다.
 * 흐름마다 자기 종류를 알므로 팩토리 셋으로 고른다: 자유런 [forFree], 배틀런 [forBattle], 셀프·셀럽런 [forRecord]. 문구 표는 이 파일
 * 한 곳에만 있다.
 */
sealed interface WatchResultHeadline {
    /** 머리말(FINISH, GOAL, STOP, WIN, LOSE, BEST, GOOD). 판정을 기다리는 중([Pending])이면 빈 문자열이다. */
    fun title(): String

    /** 머리말 아래 결과 문구. 없으면 null. */
    fun subtitle(res: Resources): String?

    /** 자유런 목표 없음(무제한)으로 종료. */
    data object Finish : WatchResultHeadline {
        override fun title(): String = "FINISH"

        override fun subtitle(res: Resources): String? = null
    }

    /** 자유런 목표 거리에 닿은 뒤 종료. */
    data object Goal : WatchResultHeadline {
        override fun title(): String = "GOAL"

        override fun subtitle(res: Resources): String = res.getString(R.string.result_goal)
    }

    /** 도착(목표) 전 종료: 자유런 중간 종료, 배틀런 포기, 셀프·셀럽런 그만두기. */
    data object Stop : WatchResultHeadline {
        override fun title(): String = "STOP"

        override fun subtitle(res: Resources): String? = null
    }

    /** 이김. 배틀런은 상대가 남은 거리([gapMeters]), 셀럽런은 앞선 시간([gapSeconds])으로 문구를 낸다. 둘 다 없으면 문구가 없다. */
    data class Win(val opponentName: String, val gapMeters: Int? = null, val gapSeconds: Long? = null) :
        WatchResultHeadline {
        override fun title(): String = "WIN"

        override fun subtitle(res: Resources): String? = when {
            gapMeters != null -> res.getString(R.string.result_win_meters, opponentName, gapMeters)
            gapSeconds != null -> res.getString(R.string.result_win_seconds, opponentName, gapSeconds)
            else -> null
        }
    }

    /** 짐(상대가 먼저 도착). [gapSeconds]는 늦게 들어온 시간이고 모르면 문구가 없다. */
    data class Lose(val opponentName: String, val gapSeconds: Long?) : WatchResultHeadline {
        override fun title(): String = "LOSE"

        override fun subtitle(res: Resources): String? =
            gapSeconds?.let { res.getString(R.string.result_lose_seconds, opponentName, it) }
    }

    /** 셀프런 최고 기록보다 빠름. */
    data class Best(val gapSeconds: Long) : WatchResultHeadline {
        override fun title(): String = "BEST"

        override fun subtitle(res: Resources): String = res.getString(R.string.result_best, gapSeconds)
    }

    /** 셀프런 최고 기록보다 느림(같음 포함). */
    data class Good(val gapSeconds: Long) : WatchResultHeadline {
        override fun title(): String = "GOOD"

        override fun subtitle(res: Resources): String = res.getString(R.string.result_good, gapSeconds)
    }

    /** 배틀런 결과 보류(FR-108). 머리말 대신 "결과를 확인하고 있어요"를 보이고 판정이 오면 바꾼다. */
    data object Pending : WatchResultHeadline {
        override fun title(): String = ""

        override fun subtitle(res: Resources): String = res.getString(R.string.result_pending)
    }

    companion object {
        /** 자유런: 목표가 없으면 FINISH, 목표 거리에 닿았으면 GOAL, 목표 전에 끝냈으면 STOP. */
        fun forFree(outcome: FinishOutcome, goal: Distance?): WatchResultHeadline {
            if (goal == null || goal.meters <= 0) return Finish
            val distance = (outcome as? FinishOutcome.Saved)?.run?.distance ?: Distance.ZERO
            return if (distance.isAtLeast(goal)) Goal else Stop
        }

        /**
         * 배틀런: 결과가 없거나 보류면 [Pending], 내가 도착하지 않았으면(포기) STOP, 서버 판정 WIN이면 [gapMeters](도착 때 상대와의
         * 거리 차)로 WIN, LOSE면 도달 시간 차로 LOSE. 비김(DRAW)은 FR-249 표에 없어 FINISH로 보인다.
         */
        fun forBattle(result: BattleResult?, gapMeters: Int?): WatchResultHeadline {
            if (result == null || result.isPending) return Pending
            val mine = result.myReach ?: return Stop
            val name = result.opponentName.orEmpty()
            return when (result.verdict) {
                VERDICT_WIN -> Win(name, gapMeters = gapMeters?.let { kotlin.math.abs(it) })
                VERDICT_LOSE -> Lose(name, result.opponentReach?.let { mine.minus(it).seconds.coerceAtLeast(0) })
                else -> Finish
            }
        }

        /**
         * 셀프·셀럽런: [gapSeconds]는 상대 완주 시간 − 내 도착 시간(양수면 내가 빠름)이고 도착 전에 끝냈으면 null(STOP)이다.
         * 셀프런은 BEST·GOOD, 셀럽런은 [opponentName](셀럽 표시명)으로 WIN·LOSE다.
         */
        fun forRecord(kind: RecordBattleKind, gapSeconds: Long?, opponentName: String): WatchResultHeadline {
            if (gapSeconds == null) return Stop
            val isFaster = gapSeconds > 0
            val gap = kotlin.math.abs(gapSeconds)
            return when (kind) {
                RecordBattleKind.SELF -> if (isFaster) Best(gap) else Good(gap)

                RecordBattleKind.CELEBRITY -> if (isFaster) {
                    Win(
                        opponentName,
                        gapSeconds = gap
                    )
                } else {
                    Lose(opponentName, gap)
                }
            }
        }

        private const val VERDICT_WIN = "WIN"
        private const val VERDICT_LOSE = "LOSE"
    }
}
