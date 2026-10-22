package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.FaceLayer
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

    /** 회원 설정 화면 값(GET /me/settings, FR-008~FR-019·FR-048). 마이 > 설정·동의 관리·자유런 설정 화면이 읽는다. */
    suspend fun settings(): Outcome<MemberSettings>

    /**
     * 프로필 얼굴 그림: 프로필의 faceKey(셀카 아바타)로 부위 그림을 받는다(AvatarRepository.faceLayers와 같은 그림).
     * 셀카 얼굴이 아니면 빈 목록, 받지 못하면 실패다. 구현이 없으면 빈 목록이다.
     */
    suspend fun faceLayers(faceKey: String?): Outcome<List<FaceLayer>> = Outcome.Success(emptyList())

    companion object {
        /** profile(memberId)에 넘기면 로그인한 본인. 구현이 본인 회원 ID로 바꿔 부른다. */
        const val SELF: Long = 0L
    }
}
