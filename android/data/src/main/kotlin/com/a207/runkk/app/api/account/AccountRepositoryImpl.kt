package com.a207.runkk.app.api.account

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.TokenProvider
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.Profile
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.SessionTokens
import com.a207.runkk.app.domain.account.SignUpForm
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 계정·회원 포트를 REST로 구현한다(6.1.14 api.account). 가입 전 로그인이면 서버가 준 가입 티켓을 이 객체가 보관하고
 * Failure(Rejected(SIGN_UP_REQUIRED))를 돌려준다. 이어지는 signUp이 그 티켓을 실어 보낸다(D-19, 15분).
 */
@Singleton
internal class AccountRepositoryImpl
@Inject
constructor(
    private val auth: AuthApi,
    private val me: MeApi,
    private val tokens: TokenProvider
) : AccountRepository {
    @Volatile
    private var signUpTicket: String? = null

    override suspend fun login(token: ProviderToken): Outcome<SessionTokens> {
        val reply =
            when (val result = ApiErrorMapper.call { auth.login(token.toRequest()) }) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val access = reply.accessToken
        val refresh = reply.refreshToken
        if (access != null && refresh != null) {
            signUpTicket = null
            return Outcome.Success(SessionTokens(access, refresh))
        }
        val ticket = reply.signUpTicket ?: return Outcome.Failure(AppError.Rejected(AUTH_FAILED))
        signUpTicket = ticket
        return Outcome.Failure(AppError.Rejected(AppError.Rejected.SIGN_UP_REQUIRED))
    }

    override suspend fun signUp(form: SignUpForm): Outcome<SessionTokens> {
        val ticket = signUpTicket ?: return Outcome.Failure(AppError.Rejected(SIGN_UP_TICKET_INVALID))
        val issued = ApiErrorMapper.call { auth.signUp(form.toRequest(ticket)).toDomain() }
        if (issued is Outcome.Success) signUpTicket = null
        return issued
    }

    /** 서버의 갱신 토큰 폐기. 갱신 토큰이 없으면(이미 로그아웃) 보낼 것이 없다. */
    override suspend fun logout(): Outcome<Unit> {
        val refresh = tokens.refreshToken() ?: return Outcome.Success(Unit)
        return ApiErrorMapper.call { auth.logout(LogoutRequest(refresh)) }
    }

    override suspend fun withdraw(): Outcome<Unit> = ApiErrorMapper.call { me.withdraw() }

    override suspend fun profile(memberId: Long): Outcome<Profile> =
        ApiErrorMapper.call { me.profile(memberId).toDomain() }

    override suspend fun editProfile(nickname: String, intro: String?, tags: Set<String>): Outcome<Unit> =
        ApiErrorMapper.call { me.editProfile(ProfileEditRequest(nickname, intro, tags)) }

    override suspend fun changeSetting(item: String, value: String): Outcome<Unit> =
        ApiErrorMapper.call { me.changeSetting(SettingChangeBody(item, value)) }

    override suspend fun giveConsent(item: String): Outcome<Unit> = ApiErrorMapper.call { me.giveConsent(item) }

    override suspend fun withdrawConsent(item: String): Outcome<Unit> = ApiErrorMapper.call { me.withdrawConsent(item) }

    override suspend fun legalDocument(type: String): Outcome<String> =
        ApiErrorMapper.call { me.legalDocument(type).body }

    private companion object {
        /** 서버 ReasonCode와 같은 이름. */
        const val AUTH_FAILED = "AUTH_FAILED"
        const val SIGN_UP_TICKET_INVALID = "SIGN_UP_TICKET_INVALID"
    }
}
