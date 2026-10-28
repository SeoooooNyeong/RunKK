package com.a207.runkk.app.domain.account

/**
 * 게스트 시작 결과(POST /members/guest, FR-197): 서비스 토큰과 한 번만 내주는 기기 자격 증명(D-89).
 * 자격 증명은 갱신 토큰이 만료·거부됐을 때 게스트 세션을 다시 여는 데만 쓴다.
 */
data class GuestSession(val tokens: SessionTokens, val guestCredential: String) {
    override fun toString(): String = "GuestSession(***)"
}
