package com.a207.runkk.app.api.battle

import com.a207.runkk.app.api.core.RunnerLookJson
import kotlinx.serialization.Serializable

/**
 * 대결 결과(서버 BattleDtos.BattleResultResponse). recordId는 본인 러닝 기록 ID(앱이 만든 UUID), 아직 없으면 null.
 * v0.14 필드(type, totalRewardPoints, evaluation)는 옛 서버를 견디게 선택 항목이다(D-289). weekly는 폐기 예정이라 읽지 않는다(D-283).
 * opponentGender는 상대 성별(MALE·FEMALE, 셀럽·과거의 나·옛 서버면 null)이다. celebrityFigure는 셀럽 그림 키(lee·hwang·son, 셀럽 대결이
 * 아니거나 옛 서버면 null)다. placementRound·placementState는 배치고사 대결의 만든 때 판 번호와 셈 상태(PlacementRoundState 이름)이고
 * 배치고사가 아니면 null이다(05-03 5.7.3.3 P4).
 */
@Serializable
internal data class BattleResultResponse(
    val battleId: Long,
    val type: String? = null,
    val pending: Boolean = false,
    val verdict: String? = null,
    val rows: List<Row> = emptyList(),
    val rewardPoints: Int? = null,
    val newRecord: Boolean = false,
    val opponentName: String? = null,
    val recordId: String? = null,
    val myReachSeconds: Long? = null,
    val opponentReachSeconds: Long? = null,
    val totalRewardPoints: Int? = null,
    val evaluation: EvaluationDto? = null,
    val opponentGender: String? = null,
    val celebrityFigure: String? = null,
    val placementRound: Int? = null,
    val placementState: String? = null,
    /** 상대 모습([임시:D-성-252]). 배치고사·고스트·셀럽·옛 서버면 null이다. */
    val opponentLook: RunnerLookJson? = null
) {
    @Serializable
    data class Row(val item: String, val mine: String, val theirs: String)

    /** 본인 기록이 일으킨 티어 평가(서버 BattleDtos.EvaluationDto, D-291·D-294). tier가 없으면 평가 없음으로 본다. */
    @Serializable
    data class EvaluationDto(
        val kind: String? = null,
        val tier: String? = null,
        val validRuns: Int = 0,
        val runimalType: String? = null
    )
}
