package com.a207.runkk.app.api.account

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.account.Profile
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.SessionTokens
import com.a207.runkk.app.domain.account.SignUpForm
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsSnapshot

// DTO ↔ 도메인 매퍼(13.2 toDomain·toRequest).

internal fun ProviderToken.toRequest(): LoginRequest = LoginRequest(provider = provider.name, providerToken = token)

internal fun SignUpForm.toRequest(ticket: String): SignUpRequest = SignUpRequest(
    signUpTicket = ticket,
    nickname = nickname,
    birthYear = birthYear,
    gender = gender.name,
    intro = intro,
    consents = consents,
    recordVisibility = recordVisibility,
    tags = tags
)

internal fun TokenPairResponse.toDomain(): SessionTokens = SessionTokens(access = accessToken, refresh = refreshToken)

internal fun ProfileResponse.toDomain(): Profile = Profile(
    memberId = memberId,
    displayName = displayName,
    disclosure = disclosure,
    tier = tier,
    level = level,
    tags = tags,
    intro = intro,
    runimalType = runimalType,
    faceKey = faceKey,
    crewName = crewName,
    longestDistance = longestDistanceMeters?.let(::Distance)
)

/**
 * 서버 설정 원본 → 앱 사본(D-53). 동의는 consents의 LOCATION·HEART_RATE(없으면 거짓).
 * 중간 알림 간격은 M500 → 500m, 그 밖 → 1km. 서버 설정에 순위 알림 항목이 없어 기본값(켬)을 쓴다(FR-190).
 */
internal fun MemberSettingsResponse.toDomain(): SettingsSnapshot {
    val defaults = GuidanceSettings.DEFAULT
    return SettingsSnapshot(
        locationConsented = consents[CONSENT_LOCATION] == true,
        heartRateConsented = consents[CONSENT_HEART_RATE] == true,
        watchLinked = watchLinked,
        courseMapShared = courseMapShared,
        birthYear = birthYear,
        guidanceSettings =
        GuidanceSettings(
            runVoice = runSettings?.voice ?: defaults.runVoice,
            runVibration = runSettings?.vibration ?: defaults.runVibration,
            battleVoice = battleSettings?.voice ?: defaults.battleVoice,
            battleVibration = battleSettings?.vibration ?: defaults.battleVibration,
            gapAlertMeters =
            when (battleSettings?.gapAlertInterval) {
                GAP_500M -> 500
                null -> defaults.gapAlertMeters
                else -> 1_000
            },
            rankAlert = defaults.rankAlert,
            followCoachPlan = runSettings?.followCoachPlan ?: defaults.followCoachPlan
        )
    )
}

private const val CONSENT_LOCATION = "LOCATION"
private const val CONSENT_HEART_RATE = "HEART_RATE"
private const val GAP_500M = "M500"
