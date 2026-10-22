package com.a207.runkk.app.domain.notification

import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/**
 * 로그인 상태 포트. 구현 data.session.EncryptedTokenStore.
 * domain.account의 SessionRepository 대신 자기 포트로 읽어 domain.notification → domain.account가 없다(design-review.md E19).
 */
interface LoginState {
    val loggedIn: StateFlow<Boolean>
}
