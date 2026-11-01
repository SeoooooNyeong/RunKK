package com.a207.runkk.watch.battle

import android.content.res.Resources
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.run.FinishOutcome
import com.a207.runkk.watch.ui.R

/**
 * 워치 결과 화면의 머리말과 결과 문구(3.5, D-381, D-388, FR-249 표). 결과 종류마다 객체가 자기 머리말([title])과 문구([subtitle])를 낸다.
 * 흐름마다 자기 종류를 알므로 팩토리로 고른다: 자유런 [forFree], 배틀런 [forBattle], 셀프런·셀럽런은 서버 대결이면
 * [forSelfVerdict]·[forCelebVerdict](서버 판정, W27), 서버에 닿지 않은 회면 [forSelf]·[forCeleb](워치 계산). 기록 대결 종류를
 * 매개변수로 넘겨 안에서 가르지 않는다(design-review 23장 R73, 제어 결합 없음). 문구 표는 이 파일 한 곳에만 있다.
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

    /**
     * 짐(상대가 먼저 도착, 셀럽런은 같은 시간 포함, W28). [gapSeconds]는 늦게 들어온 시간이고 모르면 문구가 없다. 0이면(같은 시간)
     * "n초 늦게" 대신 "먼저 들어오지 못했어요"다.
     */
    data class Lose(val opponentName: String, val gapSeconds: Long?) : WatchResultHeadline {
        override fun title(): String = "LOSE"

        override fun subtitle(res: Resources): String? = when (gapSeconds) {
            null -> null
            0L -> res.getString(R.string.result_lose_same, opponentName)
            else -> res.getString(R.string.result_lose_seconds, opponentName, gapSeconds)
        }
    }

    /** 셀프런 최고 기록보다 빠름. [gapSeconds]는 단축한 시간이고 모르면 문구가 없다. */
    data class Best(val gapSeconds: Long?) : WatchResultHeadline {
        override fun title(): String = "BEST"

        override fun subtitle(res: Resources): String? = gapSeconds?.let { res.getString(R.string.result_best, it) }
    }

    /**
     * 셀프런 최고 기록보다 느림(같은 시간 포함, W28). [gapSeconds]는 모자란 시간이고 모르면 문구가 없다. 0이면(같은 시간)
     * "최고 기록과 같았어요"다.
     */
    data class Good(val gapSeconds: Long?) : WatchResultHeadline {
        override fun title(): String = "GOOD"

        override fun subtitle(res: Resources): String? = when (gapSeconds) {
            null -> null
            0L -> res.getString(R.string.result_good_same)
            else -> res.getString(R.string.result_good, gapSeconds)
        }
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
         * 서버에 닿지 않은 셀프런(워치 계산, FR-250): [gapSeconds]는 최고 기록 − 내 도착 시간(초, 양수면 내가 빠름)이고 도착 전에
         * 끝냈으면 null(STOP)이다. 빠르면 BEST, 같은 시간(0)을 포함해 느리면 GOOD이다(W28).
         */
        fun forSelf(gapSeconds: Long?): WatchResultHeadline = signedGap(gapSeconds) { isWin, gap ->
            if (isWin) Best(gap) else Good(gap)
        }

        /**
         * 서버에 닿지 않은 셀럽런(워치 계산, FR-250): [gapSeconds]는 셀럽 완주 시간 − 내 도착 시간이고 도착 전에 끝냈으면 null(STOP)이다.
         * 빠르면 [celebrityName](셀럽 표시명)으로 WIN, 같은 시간(0)을 포함해 느리면 LOSE다(W28).
         */
        fun forCeleb(gapSeconds: Long?, celebrityName: String): WatchResultHeadline =
            signedGap(gapSeconds) { isWin, gap ->
                if (isWin) Win(celebrityName, gapSeconds = gap) else Lose(celebrityName, gap)
            }

        /**
         * 서버 대결로 달린 셀프런(서버 판정, W27, FR-250): 결과가 없거나 보류면 [Pending], 내가 도착하지 않았으면 STOP, 판정 WIN이면
         * BEST, LOSE면 GOOD이다. 시간 차는 도달 시간 차이고 판정과 어긋나면(검증 무효 등) 문구가 없다. 비김(DRAW)은 FR-249 표에 없어
         * 배틀런처럼 FINISH다.
         */
        fun forSelfVerdict(result: BattleResult?): WatchResultHeadline = verdict(result) { isWin, gap ->
            if (isWin) Best(gap) else Good(gap)
        }

        /**
         * 서버 대결로 달린 셀럽런(서버 판정, W27, FR-250): [forSelfVerdict]와 같은 규칙으로 WIN·LOSE다. 이름은 고른 카드의
         * [celebrityName]을 쓴다(워치 계산과 같은 이름).
         */
        fun forCelebVerdict(result: BattleResult?, celebrityName: String): WatchResultHeadline =
            verdict(result) { isWin, gap ->
                if (isWin) Win(celebrityName, gapSeconds = gap) else Lose(celebrityName, gap)
            }

        /** 워치 계산의 부호 있는 시간 차를 이김 여부와 문구 시간으로 나눈다. 도착 전에 끝냈으면(null) STOP이다. */
        private inline fun signedGap(
            gapSeconds: Long?,
            headline: (isWin: Boolean, gapSeconds: Long) -> WatchResultHeadline
        ): WatchResultHeadline {
            if (gapSeconds == null) return Stop
            return headline(gapSeconds > 0, kotlin.math.abs(gapSeconds))
        }

        /** 서버 판정을 이김 여부와 문구 시간으로 나눈다. 시간 차는 화면에 보이는 초 단위로 견준다(워치 계산과 같다). */
        private inline fun verdict(
            result: BattleResult?,
            headline: (isWin: Boolean, gapSeconds: Long?) -> WatchResultHeadline
        ): WatchResultHeadline {
            if (result == null || result.isPending) return Pending
            val mine = result.myReach ?: return Stop
            val lead = result.opponentReach?.let { it.seconds - mine.seconds }
            return when (result.verdict) {
                VERDICT_WIN -> headline(true, lead?.takeIf { it > 0 })
                VERDICT_LOSE -> headline(false, lead?.let { -it }?.takeIf { it >= 0 })
                else -> Finish
            }
        }

        private const val VERDICT_WIN = "WIN"
        private const val VERDICT_LOSE = "LOSE"
    }
}
