package com.a207.runkk.app

import java.time.Duration

/**
 * 보류 초대([PendingInvite])의 종류와 남겨 두는 시간([임시:D-성-242]). 친구 대결은 서버 초대 수명(5분, D-271)과 같고, 크루 초대는
 * 코드가 7일 동안 살아 있어 로그인·가입을 마칠 동안(30분) 남긴다.
 */
enum class InviteKind(val lifetime: Duration) {
    FRIEND_BATTLE(Duration.ofMinutes(5)),
    CREW(Duration.ofMinutes(30))
}
