package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.battle.FriendInviteView
import com.a207.runkk.app.domain.live.MatchFound

data class MatchUiState(
    val phase: MatchPhase = MatchPhase.REQUESTING,
    val opponentKind: OpponentKind = OpponentKind.RANDOM,
    val distance: Distance = DEFAULT_DISTANCE,
    /** 대기 시작 뒤 경과 초(시안 "0:07"). 랜덤 대기와 초대 대기가 센다. */
    val waitingSeconds: Long = 0,
    val match: MatchFound? = null,
    /** 자동 출발까지 남은 초. 시작 예약(BattleStart)을 받기 전이면 null. */
    val startsInSeconds: Long? = null,
    /** 대기 화면 아래 짧은 안내(취소가 거부됨 등). */
    val error: UiText? = null,
    /** 확인을 누르면 이전 화면으로 돌아가는 안내(거부·연결 끊김·끝난 초대 등). */
    val notice: UiText? = null,
    /** 받은 초대의 참가 확인 정보(CONFIRMING). 읽는 중이면 null. */
    val invite: FriendInviteView? = null,
    /** 초대가 끝난 까닭(INVITE_ENDED). */
    val inviteEnd: InviteEnd? = null,
    /** 위치 동의 시트(D-267). */
    val isConsentSheetVisible: Boolean = false,
    /** 성사 뒤 측정이 시작되지 않고 늦어져 그만두기를 보인다(배틀 QA 2번). */
    val canForfeit: Boolean = false
) {
    /** 초대가 끝난 까닭. 만료는 "다시 초대하기", 거절은 "확인"으로 친구 대결 거리 고르기에 돌아간다(D-271). */
    enum class InviteEnd { EXPIRED, DECLINED }

    companion object {
        /** 랜덤 대결 거리(FR-080). 친구 대결도 같다. */
        val DISTANCES: List<Distance> = listOf(Distance(1_000), Distance(3_000), Distance(5_000), Distance(10_000))

        /** 기본 대결 거리(FR-109 기본값). 서버의 기본 대결 거리(GET /battles/match)를 읽기 전·읽지 못했을 때 쓴다. */
        val DEFAULT_DISTANCE: Distance = Distance(5_000)
    }
}
