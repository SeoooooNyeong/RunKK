package com.a207.runkk.watch.battle

import android.content.res.Resources
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.PlacementRoundState
import com.a207.runkk.app.domain.battle.RandomBattleGate
import com.a207.runkk.app.domain.run.FinishOutcome
import com.a207.runkk.watch.ui.R

/**
 * 워치 결과 화면의 머리말과 결과 문구(3.5, D-381, D-388, FR-249 표). 결과 종류마다 객체가 자기 머리말([title])과 문구([subtitle])를 낸다.
 * 흐름마다 자기 종류를 알므로 팩토리로 고른다: 자유런 [forFree], 배틀런 [forBattle](배치고사 판이면 [forPlacement]), 셀프런·셀럽런은 서버 대결이면
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

    /**
     * 배치고사 판 결과(워치 배치고사, FR-280·FR-281, D-487). 머리말은 판정([verdict])의 것을 그대로 두고, 문구는 가상 상대 이름 대신 이 판이
     * 배치고사에 어떻게 들어갔는지([line])다. [line]이 집계 중이면 화면이 판 셈을 다시 읽는다([isCounting]).
     */
    data class Placement(val verdict: WatchResultHeadline, val line: PlacementLine) : WatchResultHeadline {
        /** 판 셈이 아직 정해지지 않았다(PENDING): 2초마다 다시 읽는다(D-501). */
        val isCounting: Boolean get() = line == PlacementLine.Pending

        override fun title(): String = verdict.title()

        override fun subtitle(res: Resources): String = line.text(res)
    }

    /** 배치고사 판 결과 문구 한 줄. */
    sealed interface PlacementLine {
        fun text(res: Resources): String

        /** 판을 셌고 배치고사를 마쳤다: 「배치고사 완료 · 티어 확정 골드」. [tier]는 서버 Tier 이름이다. */
        data class Confirmed(val tier: String) : PlacementLine {
            override fun text(res: Resources): String =
                res.getString(R.string.result_placement_confirmed, tierText(res, tier))
        }

        /**
         * 판을 셌고 아직 배치 중이다: 「배치고사 [countedRounds]/3 · 임시 실버」. 임시 티어가 없으면(또는 진행을 읽지 못했으면) 「배치고사 n/3」만이다.
         */
        data class InProgress(val countedRounds: Int, val provisionalTier: String?) : PlacementLine {
            override fun text(res: Resources): String = if (provisionalTier == null) {
                res.getString(R.string.placement_round, countedRounds, RandomBattleGate.PLACEMENT_RUNS)
            } else {
                res.getString(
                    R.string.result_placement_provisional,
                    countedRounds,
                    RandomBattleGate.PLACEMENT_RUNS,
                    tierText(res, provisionalTier)
                )
            }
        }

        /** 이 판은 배치고사에 들어가지 않았다(포기·무효 기록 등, NOT_COUNTED). */
        data object NotCounted : PlacementLine {
            override fun text(res: Resources): String = res.getString(R.string.result_placement_not_counted)
        }

        /** 판 셈을 아직 모른다(PENDING): 「배치고사 결과를 집계하고 있어요」. */
        data object Pending : PlacementLine {
            override fun text(res: Resources): String = res.getString(R.string.result_placement_pending)
        }
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
         * 배치고사 판 결과(워치 배치고사, FR-280): 배치고사 대결이 아니거나([BattleResult.placementRound] 없음) 판정을 기다리는 중이면
         * [verdict] 그대로다. 아니면 판정 머리말에 판 셈 문구를 단다: 셌고 배치고사를 마쳤으면 티어 확정, 셌고 아직 배치 중이면 센 판 수와
         * 임시 티어(진행 [progress]를 읽지 못했으면 이 판 번호만), 세지 않았으면 반영 안 됨, 셈을 모르면(PENDING·모르는 값) 집계 중이다.
         */
        fun forPlacement(
            verdict: WatchResultHeadline,
            result: BattleResult,
            progress: PlacementStanding?
        ): WatchResultHeadline {
            val round = result.placementRound ?: return verdict
            if (verdict == Pending) return verdict
            val line = when (result.placementState) {
                PlacementRoundState.COUNTED -> when {
                    progress == null -> PlacementLine.InProgress(round, provisionalTier = null)
                    progress.isCompleted -> PlacementLine.Confirmed(progress.tier)
                    else -> PlacementLine.InProgress(progress.countedRounds, progress.provisionalTier)
                }

                PlacementRoundState.NOT_COUNTED -> PlacementLine.NotCounted

                PlacementRoundState.PENDING, null -> PlacementLine.Pending
            }
            return Placement(verdict, line)
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
         * 서버 대결로 달린 셀프런(서버 판정, W27, FR-250): 결과가 없거나 보류면 [Pending], 판정 WIN이면 BEST, LOSE면 GOOD이다.
         * 시간 차는 서버 도달 시간 차이고, 서버에 내 도달 시간이 없으면(카운트다운이 끝난 뒤 도착해 검증되지 않음 등) 워치가 잰
         * [localLeadSeconds](상대 완주 시간 − 내 도착 시간, 도착 전에 끝냈으면 null)로 쓴다. 둘 다 없으면 도착 전에 끝낸 것이라 STOP이다(R13 D1).
         * 시간 차가 판정과 어긋나면(검증 무효 등) 문구가 없다. 비김(DRAW)은 FR-249 표에 없어 배틀런처럼 FINISH다.
         */
        fun forSelfVerdict(result: BattleResult?, localLeadSeconds: Long? = null): WatchResultHeadline =
            verdict(result, localLeadSeconds) { isWin, gap ->
                if (isWin) Best(gap) else Good(gap)
            }

        /**
         * 서버 대결로 달린 셀럽런(서버 판정, W27, FR-250): [forSelfVerdict]와 같은 규칙으로 WIN·LOSE다. 이름은 고른 카드의
         * [celebrityName]을 쓴다(워치 계산과 같은 이름).
         */
        fun forCelebVerdict(
            result: BattleResult?,
            celebrityName: String,
            localLeadSeconds: Long? = null
        ): WatchResultHeadline = verdict(result, localLeadSeconds) { isWin, gap ->
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

        /**
         * 서버 판정을 이김 여부와 문구 시간으로 나눈다. 시간 차는 화면에 보이는 초 단위로 견준다(워치 계산과 같다). 서버에 내 도달 시간이
         * 없으면 [localLeadSeconds]를 쓰고, 그것도 없으면(도착 전 종료) STOP이다.
         */
        private inline fun verdict(
            result: BattleResult?,
            localLeadSeconds: Long?,
            headline: (isWin: Boolean, gapSeconds: Long?) -> WatchResultHeadline
        ): WatchResultHeadline {
            if (result == null || result.isPending) return Pending
            val mine = result.myReach
            val lead = if (mine != null) {
                result.opponentReach?.let { it.seconds - mine.seconds }
            } else {
                localLeadSeconds ?: return Stop
            }
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

/** 배치고사 판 수(공용 RandomBattleGate.PLACEMENT_RUNS). watch.ui는 domain.battle을 보지 않아 이 값으로 「n/3」의 분모를 쓴다. */
const val PLACEMENT_RUNS: Int = RandomBattleGate.PLACEMENT_RUNS

/** 서버 티어 코드 → 이름 문자열 리소스(폰 core.designsystem TierName과 같은 표). 모르는 코드면 null이다. 화면 tierName도 이 표를 쓴다. */
internal fun tierNameRes(tier: String): Int? = when (tier) {
    RandomBattleGate.UNRANKED -> R.string.tier_unranked
    "BRONZE" -> R.string.tier_bronze
    "SILVER" -> R.string.tier_silver
    "GOLD" -> R.string.tier_gold
    "PLATINUM" -> R.string.tier_platinum
    "MASTER" -> R.string.tier_master
    else -> null
}

/** 티어 이름(모르는 코드는 그대로). */
private fun tierText(res: Resources, tier: String): String = tierNameRes(tier)?.let(res::getString) ?: tier
