package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

// 계정·회원 REST 전송 형식. api.account 밖으로 나가지 않는다(13.2). 거리는 미터, 시각은 api.core.ServerTime 형식이다.

/** POST /auth/login 요청(서버 AuthService.login(provider, providerToken)). */
@Serializable
internal data class LoginRequest(val provider: String, val providerToken: String)

/**
 * POST /auth/login 응답(서버 LoginResult). 회원이면 토큰 쌍, 가입 전이면 signUpTicket(15분, D-19)만 온다.
 * 실패(제공자 무응답·인증 실패)는 409 + 사유 코드로 온다.
 */
@Serializable
internal data class LoginResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val signUpTicket: String? = null
)

@Serializable
internal data class TokenPairResponse(val accessToken: String, val refreshToken: String)

@Serializable
internal data class LogoutRequest(val refreshToken: String)

/** POST /members 요청(서버 SignUpForm + 가입 티켓). consents 키는 서버 ConsentItem 이름이다. */
@Serializable
internal data class SignUpRequest(
    val signUpTicket: String,
    val nickname: String?,
    val birthYear: Int?,
    val gender: String,
    val intro: String?,
    val consents: Map<String, Boolean>,
    val recordVisibility: String,
    val tags: Set<String>
)

/** GET /members/{id}/profile 응답(서버 profile.ProfileView). 공개 수준에 따라 서버가 걸러 보낸 항목만 있다(D-52). */
@Serializable
internal data class ProfileResponse(
    val memberId: Long,
    val displayName: String,
    val disclosure: String,
    val tier: String? = null,
    val level: Int? = null,
    val tags: Set<String> = emptySet(),
    val intro: String? = null,
    val runimalType: String? = null,
    val faceKey: String? = null,
    val crewName: String? = null,
    val longestDistanceMeters: Long? = null,
    val ageBand: String? = null,
    val gender: String? = null,
    val photoUrl: String? = null
)

@Serializable
internal data class ProfileEditRequest(val nickname: String, val intro: String?, val tags: Set<String>)

/** PATCH /me/settings 요청(서버 SettingChangeRequest: 설정항목 이름·값, ALERT일 때만 알림 종류). */
@Serializable
internal data class SettingChangeBody(val item: String, val value: String, val notificationType: String? = null)

/** GET /legal/{type} 응답(서버 member.LegalDocument): 문서는 앱 밖 공개 주소(url)에 있다. body는 주소 대신 본문을 줄 때를 위한 여분이다. */
@Serializable
internal data class LegalDocumentResponse(val type: String? = null, val url: String? = null, val body: String? = null)

/**
 * GET /me/settings 응답(서버 member.MemberSettingsView, D-53). 앱 사본은 이 가운데 동의·워치 연동·코스지도 공유·
 * 출생연도·안내 설정만 쓴다. consents 키는 서버 ConsentItem 이름이다.
 */
@Serializable
internal data class MemberSettingsResponse(
    val unit: String? = null,
    val recordVisibility: String? = null,
    val courseMapShared: Boolean = false,
    val profileVisibility: String? = null,
    val notificationPrefs: Map<String, Boolean> = emptyMap(),
    val watchLinked: Boolean = false,
    val runSettings: RunSettingsDto? = null,
    val battleSettings: BattleSettingsDto? = null,
    val consents: Map<String, Boolean> = emptyMap(),
    val birthYear: Int? = null
) {
    /** 서버 member.RunSettings. */
    @Serializable
    data class RunSettingsDto(
        val target: String? = null,
        val voice: Boolean = true,
        val vibration: Boolean = true,
        val followCoachPlan: Boolean = false
    )

    /** 서버 member.BattleSettings. gapAlertInterval은 M500 또는 KM1. */
    @Serializable
    data class BattleSettingsDto(
        val defaultDistance: String? = null,
        val voice: Boolean = true,
        val vibration: Boolean = true,
        val gapAlertInterval: String = "KM1"
    )
}
