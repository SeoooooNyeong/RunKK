package com.a207.runkk.app

/**
 * 들어온 초대 링크가 가리키는 것([임시:D-성-242]). 친구 대결 초대와 크루 초대는 경로(/invite/battle/, /invite/crew/)와 카카오 실행
 * 인자(invite, crewInvite)가 달라 서로 다른 화면으로 간다([임시:FR-성-38]).
 */
sealed interface InviteLink {
    /** 친구 대결 초대(D-272). [token]은 서버 초대 토큰이다. */
    data class FriendBattle(val token: String) : InviteLink

    /** 크루 초대([임시:D-성-237]). [code]는 대문자·숫자 6자 초대 코드다. */
    data class Crew(val code: String) : InviteLink
}
