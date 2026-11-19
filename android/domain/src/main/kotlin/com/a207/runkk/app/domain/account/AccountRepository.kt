package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunViewMode
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
     * 워치용 토큰 쌍을 따로 받는다(POST /auth/watch-session, D-372, 사용자 결정 W17). 폰 토큰 쌍은 그대로 유효하다(서로 무효로 만들지
     * 않는다). 폰 data.watch의 WatchSessionPublisher가 워치에 보낼 로그인 정보를 만들 때 부른다(R71: api.account를 직접 부르지 않는다).
     */
    suspend fun issueWatchSession(): Outcome<SessionTokens>

    /**
     * 이 회원의 워치용 갱신 토큰을 모두 폐기한다(DELETE /auth/watch-session, 본문 없음, 204, contract-v2 4절, FR-019, W14, W25).
     * 폰이 워치연동을 끌 때 WatchSessionPublisher가 부른다. 로그아웃·탈퇴는 서버가 함께 폐기하므로 부르지 않는다.
     */
    suspend fun revokeWatchSession(): Outcome<Unit>

    /**
     * 로그인한 본인의 회원 ID(기기에 있는 접근 토큰에서 읽는다, 서버 호출 없음). 토큰이 없으면 null. WatchSessionPublisher가 워치에 보낼
     * 로그인 정보의 회원 ID와 "이미 보낸 회원인가"를 이것으로 정한다(SQ-43: watch-session 응답에는 토큰 쌍뿐이다). 기본 구현을 두지 않는다
     * (빠뜨리면 발행기가 아무것도 보내지 않고 조용히 끝난다, 코드 리뷰 AN12-3).
     */
    fun selfMemberId(): Long?

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

    /** 기기에 남긴 내 요약 사본(내 계정 칸, S15P31A207-345 2차). 없으면 null. 네트워크를 쓰지 않는다. */
    suspend fun cachedMySummary(): MySummary? = null

    /**
     * 본인 배치고사 진행(GET /me/placement, FR-279~FR-282, D-488·D-493). 홈 헤더 배지·진입 시트, 마이, 배치고사 화면이 읽는다.
     * 임시 티어는 이 응답에만 있다(FR-282). 모르는 상태 값은 실패(Server(0))다. 기본 구현은 실패라 구현이 없는 가짜 저장소에서는
     * 화면이 진행을 모르는 상태(배지 없음, 진입 시트 랜덤 → 배치고사 화면)로 동작한다.
     */
    suspend fun placement(): Outcome<PlacementProgress> = Outcome.Failure(AppError.Server(0))

    /**
     * 배치 진행 기기 사본(Jira 325): 마지막으로 [placement]에서 읽은 로그인한 회원의 값. 홈이 복귀마다 서버를 부르지 않고 이것을 쓴다.
     * 없거나 다른 회원 것이면 null(그때는 [placement]로 읽는다). 구현이 없으면 null.
     */
    fun cachedPlacement(): PlacementProgress? = null

    /**
     * 이 기기에서 배치고사 완료 화면(FR-281)을 본 마지막 판의 대결 ID(D-531). 배치고사 진행 화면이 완료를 받았을 때 이 값이 마지막 판과
     * 다르면 완료 화면을 연다. 구현(data)은 설정 기기 사본(SettingsRepository)에 맡겨 로그아웃·탈퇴 때 함께 지운다. 기본 구현은 사본
     * 없음·저장 무시라, 구현이 없는 가짜 저장소에서는 완료를 받을 때마다 완료 화면을 연다(한 번 더 보여도 되는 쪽).
     */
    suspend fun placementCompleteSeen(): Long? = null

    suspend fun savePlacementCompleteSeen(battleId: Long): Outcome<Unit> = Outcome.Success(Unit)

    /**
     * 대결·크루런 진행 화면의 보기 선호([임시:D-성-264], [임시:FR-성-28]). 처음 값은 트랙이다. 구현(data)은 설정 기기 사본
     * (SettingsRepository)에 맡겨 로그아웃·탈퇴 때 함께 지운다. 기본 구현은 늘 트랙·저장 무시다.
     */
    suspend fun runViewMode(): RunViewMode = RunViewMode.TRACK

    suspend fun saveRunViewMode(mode: RunViewMode): Outcome<Unit> = Outcome.Success(Unit)

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

    /**
     * 기다리지 않고 바로 그릴 수 있는 얼굴 그림(앞서 받아 둔 것). 셀카 얼굴이 아니면 빈 목록, 받아 둔 그림이 없으면 null이다(그때는
     * [faceLayers]로 받는다). 화면이 첫 그림부터 그 얼굴을 그리고 기본 머리를 거치지 않게 한다. 구현이 없으면 null.
     */
    fun cachedFaceLayers(faceKey: String?): List<FaceLayer>? = null

    /**
     * 마지막으로 읽은 내 아바타(기기에 남긴 내 프로필의 얼굴·배경 키와 받아 둔 그림). 화면 상태의 첫 값으로 써서 앱을 새로 켜도 첫
     * 그림부터 내 얼굴을 그린다. 모르거나(처음 로그인, 다른 계정) 그림을 받아 두지 않았으면 null. 구현이 없으면 null.
     */
    fun lastMyAvatar(): MyAvatar? = null

    companion object {
        /** profile(memberId)에 넘기면 로그인한 본인. 구현이 본인 회원 ID로 바꿔 부른다. */
        const val SELF: Long = 0L
    }
}

private val UNKNOWN_GENDER: StateFlow<Gender?> = MutableStateFlow(null)
