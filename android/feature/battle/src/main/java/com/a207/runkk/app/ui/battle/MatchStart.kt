package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.model.Distance

/** 매칭 대기 화면이 여는 대기 종류(경로 인자). */
sealed interface MatchStart {
    /** 랜덤 매칭(Routes.BattleQuickMatch). */
    data class Quick(val distance: Distance) : MatchStart

    /** 친구 초대(Routes.FriendInviteWait). */
    data class Invite(val distance: Distance) : MatchStart

    /** 받은 초대 참가 확인(Routes.FriendInviteJoin). */
    data class Join(val token: String) : MatchStart
}
