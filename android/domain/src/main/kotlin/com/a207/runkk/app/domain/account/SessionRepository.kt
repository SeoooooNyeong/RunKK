package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome
import kotlinx.coroutines.flow.StateFlow

/**
 * 서비스 토큰과 로그인 상태 포트. 구현 data.session.EncryptedTokenStore(TokenProvider·LoginState도 구현).
 * 어느 쪽에서 지우든 loggedIn이 거짓이 된다.
 */
interface SessionRepository {
    val loggedIn: StateFlow<Boolean>

    suspend fun save(tokens: SessionTokens): Outcome<Unit>

    suspend fun clear(): Outcome<Unit>
}
