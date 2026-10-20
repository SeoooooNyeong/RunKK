package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.Outcome

/**
 * 서비스 토큰을 읽고 바꾸는 포트(6.1.14, design-review.md M40). 구현은 data.session.EncryptedTokenStore다.
 * 문자열만 주고받아 api.core가 domain.account를 import하지 않는다.
 *
 * clear()를 부르면 구현이 로그인 상태(SessionRepository.loggedIn·LoginState.loggedIn)를 거짓으로 바꾼다.
 * clear()는 domain.account.SessionRepository.clear()와 시그니처가 같다: EncryptedTokenStore 한 객체가 두 포트를 함께
 * 구현하려면(SessionBindings) 같은 이름의 연산이 suspend 여부만 달라서는 안 되기 때문이다.
 */
interface TokenProvider {
    fun accessToken(): String?

    fun refreshToken(): String?

    fun update(access: String, refresh: String)

    suspend fun clear(): Outcome<Unit>
}
