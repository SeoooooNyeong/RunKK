package com.a207.runkk.app

import java.time.Duration
import java.time.Instant

/**
 * 초대 링크로 받았지만 아직 열지 않은 초대(D-272). 로그인·가입 중이거나 측정 중이면 홈이 맨 위가 될 때까지 기다리는데,
 * 받은 지 [kind]의 수명([InviteKind.lifetime])이 지나면 버린다(AN8-8): 서버 초대 수명(5분, D-271)이 지난 대결 초대가 측정을 마친 뒤
 * 갑자기 열리지 않게 한다. 크루 초대([InviteKind.CREW], [임시:D-성-242])는 [token]이 초대 코드이고 수명을 따로 둔다.
 */
data class PendingInvite(val token: String, val receivedAt: Instant, val kind: InviteKind = InviteKind.FRIEND_BATTLE) {
    /** [now]에 아직 열어도 되는가. 기기 시계가 뒤로 가 받은 시각보다 앞이면 연다(서버가 만료를 판정한다). */
    fun isFresh(now: Instant): Boolean = Duration.between(receivedAt, now) < kind.lifetime

    companion object {
        /** 친구 대결 보류 초대를 남겨 두는 시간: 서버 초대 수명과 같다. */
        val LIFETIME: Duration = InviteKind.FRIEND_BATTLE.lifetime

        /** 받은 링크를 보류 초대로 바꾼다. */
        fun of(link: InviteLink, receivedAt: Instant): PendingInvite = when (link) {
            is InviteLink.FriendBattle -> PendingInvite(link.token, receivedAt, InviteKind.FRIEND_BATTLE)
            is InviteLink.Crew -> PendingInvite(link.code, receivedAt, InviteKind.CREW)
        }
    }
}
