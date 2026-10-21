package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Distance

/** 소셜 로그인 제공자(C-007). */
enum class ProviderType {
    KAKAO,
    GOOGLE
}

/** 제공자 SDK로 받은 토큰. ui.me의 SocialLoginClient가 만들고 LoginUseCase에 넘긴다(D-19). */
data class ProviderToken(val provider: ProviderType, val token: String) {
    override fun toString(): String = "ProviderToken(provider=$provider, token=***)"
}

/** 서비스 JWT(접근 1시간, 갱신 14일, D-19). */
data class SessionTokens(val access: String, val refresh: String) {
    override fun toString(): String = "SessionTokens(***)"
}

/**
 * 설정 변경 요청(FR-008~FR-017). 갈래마다 서버 연산이 다르다(D-36): 일반 설정은 PATCH /me/settings,
 * 동의는 PUT·DELETE /me/consents/{item}. item 값은 서버 SettingItem·ConsentItem 상수 이름이다.
 */
sealed interface SettingChangeRequest {
    data class Change(val item: String, val value: String) : SettingChangeRequest

    data class GiveConsent(val item: String) : SettingChangeRequest

    data class WithdrawConsent(val item: String) : SettingChangeRequest
}

/** 성별(서버 member.Gender). */
enum class Gender {
    FEMALE,
    MALE,
    UNDISCLOSED
}

/**
 * 가입 입력(서버 member.SignUpForm과 같은 항목, POST /members). 검증 규칙(6.3 1.2 의사결정표)은 서버가 판정하고
 * 거부 사유는 AppError.Rejected(사유 코드)로 온다. consents의 키는 서버 ConsentItem 상수 이름이다.
 */
data class SignUpForm(
    val nickname: String?,
    val birthYear: Int?,
    val gender: Gender,
    val intro: String?,
    val consents: Map<String, Boolean>,
    val recordVisibility: String,
    val tags: Set<String>
)

/**
 * 회원 설정 화면 값(GET /me/settings: 서버 MemberSettingsView와 동의 최근 값). 필드 이름·형식은 10/21 API 계약으로 확정하고
 * api.account 매퍼가 맞춘다. values의 키·값은 changeSetting(item, value)로 보내는 것과 같은 문자열이고(서버 SettingItem 이름,
 * 러닝·대결 설정은 "RUN_SETTINGS.voice"처럼 하위 항목, 알림은 "ALERT.알림종류"), consents의 키는 서버 ConsentItem 이름이다.
 */
data class MemberSettings(val values: Map<String, String>, val consents: Map<String, Boolean>)

/**
 * 회원 프로필(GET /members/{id}/profile, 서버 profile.ProfileView). 공개 수준에 따라 서버가 걸러 보낸 항목만 채운다(D-52).
 * 필드 이름·형식은 10/21 API 계약으로 확정하고 api.account 매퍼가 맞춘다.
 */
data class Profile(
    val memberId: Long,
    val displayName: String,
    val disclosure: String,
    val tier: String?,
    val level: Int?,
    val tags: Set<String>,
    val intro: String?,
    val runimalType: String?,
    val faceKey: String?,
    val crewName: String?,
    val longestDistance: Distance?,
    val ageBand: String? = null,
    val gender: Gender? = null,
    val photoUrl: String? = null
)
