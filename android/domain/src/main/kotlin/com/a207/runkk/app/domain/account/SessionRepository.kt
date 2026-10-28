package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome
import kotlinx.coroutines.flow.StateFlow

/**
 * 서비스 토큰·게스트 자격 증명과 로그인 상태 포트. 구현 data.session.EncryptedTokenStore(TokenProvider·LoginState도 구현).
 * 어느 쪽에서 지우든 loggedIn이 거짓이 된다. clear()는 게스트 자격 증명도 지운다(FR-002, D-89).
 */
interface SessionRepository {
    val loggedIn: StateFlow<Boolean>

    suspend fun save(tokens: SessionTokens): Outcome<Unit>

    suspend fun clear(): Outcome<Unit>

    /** 게스트 시작 때 한 번 받는 기기 자격 증명을 보관한다(FR-197, D-89). */
    suspend fun saveGuestCredential(credential: String): Outcome<Unit>

    /** 게스트 자격 증명만 지운다(소셜 계정 연동 성공 뒤, D-159 (2)). 토큰은 그대로다. */
    suspend fun clearGuestCredential(): Outcome<Unit>

    /** 저장된 게스트 자격 증명이 있는가(연동하지 않은 게스트인가, FR-199). 첫 호출이 암호화 저장소를 열 수 있어 구현이 IO에서 읽는다(AN5-C-16). */
    suspend fun hasGuestCredential(): Boolean
}
