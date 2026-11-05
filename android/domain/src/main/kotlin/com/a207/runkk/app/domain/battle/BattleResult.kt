package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 대결 결과(서버 battle.BattleResultView + 적립 리워드, FR-093·FR-108). [opponentName]은 상대 표시명, [recordId]는 이 대결의
 * 본인 러닝 기록 ID(기록 카드·상세 쪽, 아직 없으면 null), [myReach]·[opponentReach]는 도달 시간이다. [rows]는 셀프런만 두 줄
 * (기록, 평균 페이스)이고 나머지는 빈 목록이다(서버 D-229). [type]은 서버 이력 분류(고스트는 RANDOM, 친구 대결은 FRIEND)이고 모르면
 * null, [totalRewardPoints]는 이 대결로 적립한 합계(대결 + 본인 기록, 옛 서버면 null), [evaluation]은 본인 기록이 일으킨 티어
 * 평가(없으면 null, D-284)다. 이번 주 전적(weekly)은 서버가 폐기 예정이라 읽지 않는다(D-283). [opponentGender]는 상대 성별(상대 그림을
 * 고른다)이고 셀럽·과거의 나·옛 서버면 null이다. [celebrityFigure]는 셀럽 그림 키(셀럽 대결이 아니거나 없으면 null)다.
 * [placementRound]는 배치고사 대결이면 만든 때의 판 번호(1~3), [placementState]는 그 판이 배치고사에 들어갔는지(FR-280, D-487)이고
 * 배치고사 대결이 아니거나 모르는 값이면 null이다.
 */
data class BattleResult(
    val battleId: Long,
    val isPending: Boolean,
    val verdict: String?,
    val rows: List<Row>,
    val rewardPoints: Int?,
    val isNewRecord: Boolean,
    val opponentName: String? = null,
    val recordId: String? = null,
    val myReach: Duration? = null,
    val opponentReach: Duration? = null,
    val type: BattleHistory.Type? = null,
    val totalRewardPoints: Int? = null,
    val evaluation: Evaluation? = null,
    val opponentGender: Gender? = null,
    val celebrityFigure: String? = null,
    val placementRound: Int? = null,
    val placementState: PlacementRoundState? = null
) {
    data class Row(val item: String, val mine: String, val theirs: String)

    /**
     * 본인 기록이 일으킨 티어 평가(서버 EvaluationDto, D-291·D-294). [kind]는 FIRST_PLACEMENT·REEVALUATION, [tier]는 서버 Tier 이름,
     * [validRuns]는 평가를 일으킨 유효 러닝 순번, [runimalType]은 확정된 유형(첫 배치·미확정이면 null)이다.
     */
    data class Evaluation(val kind: String, val tier: String, val validRuns: Int, val runimalType: String?)
}
