package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.account.MyAvatar
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.record.RecordDetail

data class BattleResultUiState(
    val isLoading: Boolean = true,
    val battleId: Long? = null,
    val result: BattleResult? = null,
    val error: UiText? = null,
    /** 보류 결과를 다시 읽는 중인가. 이 동안은 결과를 확인하는 화면을, 다시 읽기를 멈춘 뒤에는 늦어짐 안내를 보인다. */
    val isAwaitingVerdict: Boolean = false,
    /** 이 대결 본인 기록 상세(2쪽, D-283). 기록이 아직 없거나 읽지 못하면 null이고 2쪽은 결과 값으로만 그린다. */
    val detail: RecordDetail? = null,
    /** 결과 쪽 내 아바타의 얼굴·배경(홈과 같은 출처). 읽기 전·실패면 null이고 기본 아바타를 그린다. */
    val myAvatar: MyAvatar? = null
) {
    val verdict: Verdict? get() = Verdict.of(result?.verdict)

    /** 결과 종류(서버 type): 고스트는 랜덤, 모르면(옛 서버) 랜덤으로 본다. */
    val kind: OpponentKind
        get() = when (result?.type) {
            BattleHistory.Type.FRIEND -> OpponentKind.FRIEND
            BattleHistory.Type.SELF -> OpponentKind.SELF
            BattleHistory.Type.CELEBRITY -> OpponentKind.CELEBRITY
            else -> OpponentKind.RANDOM
        }
}
