package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.FriendInvite

sealed interface MatchEvent {
    /** 측정 시작: 대결 진행 화면으로(이 화면을 대신한다). */
    data class Started(val key: Routes.BattleRunning) : MatchEvent

    /** 대기 취소·안내 확인: 이전 화면으로(배틀런 화면, 받은 초대면 홈). */
    data object Exit : MatchEvent

    /** 초대를 카카오톡으로 보낸다(D-270). 화면이 InviteSharer를 부른다. [inviterName]은 내 닉네임이고 못 읽었으면 null이다. */
    data class ShareInvite(val invite: FriendInvite, val inviterName: String?) : MatchEvent
}
