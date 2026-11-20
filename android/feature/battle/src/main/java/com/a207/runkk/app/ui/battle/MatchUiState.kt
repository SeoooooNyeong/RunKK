package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GpsReadiness
import com.a207.runkk.app.domain.battle.FriendInviteView
import com.a207.runkk.app.domain.live.MatchFound

data class MatchUiState(
    val phase: MatchPhase = MatchPhase.REQUESTING,
    val opponentKind: OpponentKind = OpponentKind.RANDOM,
    val distance: Distance = DEFAULT_DISTANCE,
    /** 대기 시작 뒤 경과 초(시안 "0:07"). 랜덤 대기와 초대 대기가 센다. */
    val waitingSeconds: Long = 0,
    val match: MatchFound? = null,
    /**
     * 성사 화면(C-3) "나" 아래 티어 칩의 서버 티어 코드(R2-10). 성사 메시지(MatchFound)에는 내 티어가 없어 랜덤·초대는 내 프로필
     * (GET /members/me/profile)의 tier, 참가하는 쪽은 참가 확인 정보의 myTier를 쓴다. 못 읽으면 null이고 칩을 그리지 않는다.
     */
    val myTier: String? = null,
    /** 자동 출발까지 남은 초. 시작 예약(BattleStart)을 받기 전이면 null. */
    val startsInSeconds: Long? = null,
    /**
     * 성사(MATCHED)했지만 출발 예약을 아직 받지 못했다. 서버는 MatchFound 바로 뒤에 BattleStart를 보내므로 화면은 성사 판을 잠깐(최대
     * MatchViewModel.START_GRACE_MILLIS) 미뤄 숫자 없는 성사 판을 그리지 않는다(S15P31A207-335 9회차). 그동안은 앞 화면을 그린다.
     */
    val isAwaitingStart: Boolean = false,
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
    val canForfeit: Boolean = false,
    /** 배치고사 판 번호(MatchFound.placementRound). 있으면 상대를 점선 원 「?」로 그리고 상대 티어를 숨긴다(FR-276, J24-3). */
    val placementRound: Int? = null,
    /** 성사 화면의 GPS 예열 상태(W55). 예열하지 않으면(위치 권한 없음 등) null이고 그리지 않는다. */
    val gps: GpsReadiness? = null,
    /**
     * 대기 화면에서 뒤로 가기를 눌러 띄운 나가기 확인 창([임시:D-성-270], [임시:FR-성-32]). 요청 중·대기·초대 대기에서만 켜지고 상대가
     * 정해지거나 대기가 끝나면 꺼진다.
     */
    val isLeaveConfirmVisible: Boolean = false
) {
    /** 상대 티어·그림을 숨기는가(배치고사 상대). */
    val isOpponentHidden: Boolean get() = placementRound != null

    /** 친구 초대 대기인가(나가기 확인과 상시 안내가 「초대가 취소돼요」로 바뀐다, [임시:D-성-271]). */
    val isInviteWaiting: Boolean get() = opponentKind == OpponentKind.FRIEND

    /** 초대가 끝난 까닭. 만료는 "다시 초대하기", 거절은 "확인"으로 친구 대결 거리 고르기에 돌아간다(D-271). */
    enum class InviteEnd { EXPIRED, DECLINED }

    companion object {
        /** 랜덤 대결 거리(FR-080). 친구 대결도 같다. */
        val DISTANCES: List<Distance> = listOf(Distance(1_000), Distance(3_000), Distance(5_000), Distance(10_000))

        /** 기본 대결 거리(FR-109 기본값). 서버의 기본 대결 거리(GET /battles/match)를 읽기 전·읽지 못했을 때 쓴다. */
        val DEFAULT_DISTANCE: Distance = Distance(5_000)
    }
}
