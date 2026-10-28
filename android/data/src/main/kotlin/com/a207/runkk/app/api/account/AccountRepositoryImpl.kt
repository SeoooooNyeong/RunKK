package com.a207.runkk.app.api.account

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.FaceImageSource
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.api.core.TokenProvider
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.GuestSession
import com.a207.runkk.app.domain.account.MemberSettings
import com.a207.runkk.app.domain.account.Profile
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.SessionTokens
import com.a207.runkk.app.domain.account.SignUpForm
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

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
    private val tokens: TokenProvider,
    private val self: SelfMember,
    private val faces: FaceImageSource
) : AccountRepository {
    override suspend fun faceLayers(faceKey: String?): Outcome<List<FaceLayer>> = faces.faceLayers(faceKey)

    @Volatile
    private var signUpTicket: String? = null

    override suspend fun login(token: ProviderToken): Outcome<SessionTokens> {
        val reply =
            try {
                auth.login(token.toRequest())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return Outcome.Failure(ApiErrorMapper.toLoginError(e))
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

    /** 게스트 시작(POST /members/guest, FR-197). 토큰과 자격 증명 저장은 StartAsGuestUseCase가 한다(D-159 (1)). */
    override suspend fun startAsGuest(consents: Map<String, Boolean>): Outcome<GuestSession> =
        ApiErrorMapper.call { auth.startAsGuest(GuestStartRequest(consents)).toDomain() }

    /**
     * 소셜 계정 연동(POST /me/social-account, FR-198). 제공자 인증 실패(401 AUTH_FAILED)는 접근 토큰 만료가 아니므로 로그인과 같은
     * 규칙으로 Rejected(code)로 읽는다. 토큰은 바꾸지 않는다.
     */
    override suspend fun linkSocialAccount(token: ProviderToken): Outcome<Unit> = try {
        auth.linkSocialAccount(SocialLinkRequest(token.provider.name, token.token))
        Outcome.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(ApiErrorMapper.toLoginError(e))
    }

    /** 서버의 갱신 토큰 폐기. 갱신 토큰이 없으면(이미 로그아웃) 보낼 것이 없다. */
    override suspend fun logout(): Outcome<Unit> {
        val refresh = tokens.refreshToken() ?: return Outcome.Success(Unit)
        return ApiErrorMapper.call { auth.logout(LogoutRequest(refresh)) }
    }

    override suspend fun withdraw(): Outcome<Unit> = try {
        me.withdraw()
        Outcome.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(ApiErrorMapper.toWithdrawalError(e))
    }

    /** memberId가 SELF면 접근 토큰의 회원 ID로 바꿔 부른다. 본인 ID를 모르면(토큰 없음) Unauthorized. */
    override suspend fun profile(memberId: Long): Outcome<Profile> {
        val id = self.resolve(memberId, AccountRepository.SELF) ?: return Outcome.Failure(AppError.Unauthorized)
        return ApiErrorMapper.call { me.profile(id).toDomain() }
    }

    /** 태그는 v0.11에서 지웠다(D-91). */
    override suspend fun editProfile(nickname: String, intro: String?): Outcome<Unit> =
        ApiErrorMapper.call { me.editProfile(ProfileEditRequest(nickname, intro)) }

    /**
     * 화면의 설정 키를 서버 요청으로 바꾼다(MemberSettings.values와 같은 키). "ALERT.알림종류"는 항목 ALERT에 알림 종류를 싣고,
     * "RUN_SETTINGS.필드"·"BATTLE_SETTINGS.필드"는 서버 값을 읽어 그 필드만 덮은 JSON을 항목 값으로 보낸다. 그 밖은 그대로 보낸다.
     */
    override suspend fun changeSetting(item: String, value: String): Outcome<Unit> = ApiErrorMapper.call {
        val dot = item.indexOf('.')
        val group = if (dot > 0) item.substring(0, dot) else null
        val field = if (dot > 0) item.substring(dot + 1) else ""
        when (group) {
            GROUP_ALERT -> me.changeSetting(SettingChangeBody(GROUP_ALERT, value, notificationType = field))

            GROUP_RUN, GROUP_BATTLE -> {
                val merged = me.settings().withSettingChanged(group, field, value)
                me.changeSetting(SettingChangeBody(group, requireNotNull(merged)))
            }

            else -> me.changeSetting(SettingChangeBody(item, value))
        }
    }

    override suspend fun giveConsent(item: String): Outcome<Unit> = ApiErrorMapper.call { me.giveConsent(item) }

    override suspend fun withdrawConsent(item: String): Outcome<Unit> = ApiErrorMapper.call { me.withdrawConsent(item) }

    override suspend fun legalDocument(type: String): Outcome<String> = ApiErrorMapper.call {
        val document = me.legalDocument(type)
        requireNotNull(document.url ?: document.body) { "고지문서 주소가 비었다" }
    }

    override suspend fun settings(): Outcome<MemberSettings> = ApiErrorMapper.call { me.settings().toMemberSettings() }

    private companion object {
        /** 서버 ReasonCode와 같은 이름. */
        const val AUTH_FAILED = "AUTH_FAILED"
        const val SIGN_UP_TICKET_INVALID = "SIGN_UP_TICKET_INVALID"
    }
}
