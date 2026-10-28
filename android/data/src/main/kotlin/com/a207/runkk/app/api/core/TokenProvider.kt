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

    /**
     * 로그인 세대. clear()(로그아웃)와 새 세션 저장(로그인·연동·게스트 시작의 SessionRepository.save)마다 바뀐다.
     * SessionRefresher가 갱신 요청을 보내기 전에 읽어 [updateInSession]에 넘긴다.
     */
    fun sessionGeneration(): Long = 0L

    /**
     * 세대가 아직 [generation]이면(그 사이 로그아웃·새 로그인이 없었으면) 토큰을 바꾸고 참, 아니면 바꾸지 않고 거짓. 응답을 기다리는 사이의
     * 로그아웃이 되살아나거나(AN5-C-8) 새 로그인의 토큰을 덮어쓰지 않게 한다(AN6-2). 실제 저장소(EncryptedTokenStore)는 확인과 저장을 한 잠금 안에서 한다. 기본 구현은 시험용 가짜를 위한 것이다.
     */
    fun updateInSession(generation: Long, access: String, refresh: String): Boolean {
        if (sessionGeneration() != generation) return false
        update(access, refresh)
        return true
    }

    /**
     * 저장된 게스트 자격 증명(없으면 null). 갱신이 거부됐을 때 SessionRefresher가 게스트 세션을 다시 연다(D-89).
     * 게스트 기능이 없는 구현(시험용 가짜 등)은 기본값 null을 쓴다.
     */
    fun guestCredential(): String? = null

    suspend fun clear(): Outcome<Unit>
}
