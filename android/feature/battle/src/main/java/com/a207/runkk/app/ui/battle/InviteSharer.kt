package com.a207.runkk.app.ui.battle

import android.app.Activity
import com.a207.runkk.app.domain.battle.FriendInvite

/**
 * 친구 대결 초대를 보낸다(D-270). Activity가 필요한 공유는 화면이 부르고 초대 수명은 MatchViewModel이 쥔다.
 * 카카오톡 공유 화면이 열리는 동안 앱이 뒤로 가도 대기를 떠난 것이 아니다(D-271).
 */
interface InviteSharer {
    /**
     * 초대 메시지를 보낼 화면을 연다. 카카오톡이 없으면 웹 공유로 열고, 둘 다 열지 못하면 [ShareResult.FAILED]다.
     * [inviterName]이 null이면 이름 없는 제목을 쓴다.
     */
    suspend fun share(activity: Activity, invite: FriendInvite, inviterName: String?): ShareResult
}
