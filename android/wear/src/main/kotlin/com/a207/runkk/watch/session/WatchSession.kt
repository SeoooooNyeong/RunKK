package com.a207.runkk.watch.session

import com.a207.runkk.app.domain.account.AccountType

/** 워치 로그인 상태(3.3, 상태도 2.4.26). 바꾸는 연산은 WatchSessionStore의 accept·revoke·deferRevoke·completeRevoke다. */
sealed interface WatchSession {
    /** 로그인 정보 없음(미로그인 안내, FR-242). */
    data object None : WatchSession

    /** 폰이 보낸 워치용 로그인 정보로 서버를 쓴다(FR-240). */
    data class Active(val memberId: Long, val accountType: AccountType) : WatchSession

    /** 측정 중 로그아웃 표시를 받았다. 결과를 닫으면(completeRevoke) 지운다(FR-241, D-389). */
    data class RevokePending(val memberId: Long) : WatchSession
}
