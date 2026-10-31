package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 계정·회원 REST 포트(로그인·가입·로그아웃·탈퇴·프로필·설정·동의·약관). 구현 api.account.AccountRepositoryImpl.
 * 가입 전 로그인은 Failure(Rejected(SIGN_UP_REQUIRED))이고, 가입 티켓은 구현이 보관한다(D-19).
 */
interface AccountRepository {
    /** 내 성별(기본 캐릭터 남녀를 고른다). 내 프로필을 읽거나 고치면 갱신한다. 아직 모르면 null. */
    val myGender: StateFlow<Gender?> get() = UNKNOWN_GENDER

    /** 로그아웃하면 내 성별 사본을 비운다(다음 회원이 이전 회원의 기본 캐릭터를 잠깐 보지 않게, 리뷰 AN10-5). */
    fun forgetMyGender() {}

    suspend fun login(token: ProviderToken): Outcome<SessionTokens>

    suspend fun signUp(form: SignUpForm): Outcome<SessionTokens>

    suspend fun logout(): Outcome<Unit>

    /**
     * 게스트로 시작(POST /members/guest, FR-197). [consents]의 키는 서버 ConsentItem 이름이고 가입과 같은 동의 화면이 채운다
     * (HEART_RATE·FACE_TRANSFER는 받지 않는다). 거부는 Rejected(UNDER_14·CONSENT_MISSING 등), 접속지 한도 초과는 Server(429).
     */
    suspend fun startAsGuest(consents: Map<String, Boolean>): Outcome<GuestSession>

    /**
     * 게스트 계정에 소셜 계정을 연동(POST /me/social-account, FR-198). 토큰과 회원 ID는 그대로다. 다른 회원과 연동된 소셜 계정은
     * Rejected(ALREADY_MEMBER), 이미 연동했으면 Rejected(ALREADY_LINKED).
     */
    suspend fun linkSocialAccount(token: ProviderToken): Outcome<Unit>

    suspend fun withdraw(): Outcome<Unit>

    suspend fun profile(memberId: Long): Outcome<Profile>

    /**
     * 본인 요약(D-331, R35): 본인 프로필 응답에서 홈·배틀런이 쓰는 값만 옮긴다. 요청은 profile(SELF)와 같은 하나이고,
     * 읽으면 [myGender]도 갱신한다. 본인 ID를 모르면(토큰 없음) Unauthorized.
     */
    suspend fun mySummary(): Outcome<MySummary>

    /** 프로필 편집(PATCH /me/profile, FR-007, D-287). 성향 태그(D-91)와 사진은 편집 항목이 아니다. */
    suspend fun editProfile(edit: ProfileEdit): Outcome<Unit>

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

private val UNKNOWN_GENDER: StateFlow<Gender?> = MutableStateFlow(null)
