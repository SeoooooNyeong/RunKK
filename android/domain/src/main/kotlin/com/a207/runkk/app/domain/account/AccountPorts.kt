package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Outcome
import kotlinx.coroutines.flow.StateFlow

/**
 * 계정·회원 REST 포트(로그인·가입·로그아웃·탈퇴·프로필·설정·동의·약관). 구현 api.account.AccountRepositoryImpl.
 * 가입 전 로그인은 Failure(Rejected(SIGN_UP_REQUIRED))이고, 가입 티켓은 구현이 보관한다(D-19).
 */
interface AccountRepository {
    suspend fun login(token: ProviderToken): Outcome<SessionTokens>

    suspend fun signUp(form: SignUpForm): Outcome<SessionTokens>

    suspend fun logout(): Outcome<Unit>

    suspend fun withdraw(): Outcome<Unit>

    suspend fun profile(memberId: Long): Outcome<Profile>

    suspend fun editProfile(nickname: String, intro: String?, tags: Set<String>): Outcome<Unit>

    suspend fun changeSetting(item: String, value: String): Outcome<Unit>

    suspend fun giveConsent(item: String): Outcome<Unit>

    suspend fun withdrawConsent(item: String): Outcome<Unit>

    /** 약관·개인정보 처리방침 본문(GET /legal/{type}). */
    suspend fun legalDocument(type: String): Outcome<String>
}

/**
 * 서비스 토큰과 로그인 상태 포트. 구현 data.session.EncryptedTokenStore(TokenProvider·LoginState도 구현).
 * 어느 쪽에서 지우든 loggedIn이 거짓이 된다.
 */
interface SessionRepository {
    val loggedIn: StateFlow<Boolean>

    suspend fun save(tokens: SessionTokens): Outcome<Unit>

    suspend fun clear(): Outcome<Unit>
}
