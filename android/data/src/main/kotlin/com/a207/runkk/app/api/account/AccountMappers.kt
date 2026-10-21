package com.a207.runkk.app.api.account

import com.a207.runkk.app.api.core.enumByName
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.account.Gender
import com.a207.runkk.app.domain.account.MemberSettings
import com.a207.runkk.app.domain.account.Profile
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.SessionTokens
import com.a207.runkk.app.domain.account.SignUpForm
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

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
    longestDistance = longestDistanceMeters?.let(::Distance),
    ageBand = ageBand,
    gender = enumByName<Gender>(gender),
    photoUrl = photoUrl
)

/**
 * 서버 설정 원본 → 앱 사본(D-53). 동의는 consents의 LOCATION·HEART_RATE(없으면 거짓).
 * 중간 알림 간격은 M500 → 500m, 그 밖 → 1km. 서버 설정에 순위 알림 항목이 없어 기본값(켬)을 쓴다(FR-190).
 */
internal fun MemberSettingsResponse.toDomain(): SettingsSnapshot {
    val defaults = GuidanceSettings.DEFAULT
    return SettingsSnapshot(
        isLocationConsented = consents[CONSENT_LOCATION] == true,
        isHeartRateConsented = consents[CONSENT_HEART_RATE] == true,
        isWatchLinked = watchLinked,
        isCourseMapShared = courseMapShared,
        birthYear = birthYear,
        guidanceSettings =
            GuidanceSettings(
                isRunVoiceOn = runSettings?.voice ?: defaults.isRunVoiceOn,
                isRunVibrationOn = runSettings?.vibration ?: defaults.isRunVibrationOn,
                isBattleVoiceOn = battleSettings?.voice ?: defaults.isBattleVoiceOn,
                isBattleVibrationOn = battleSettings?.vibration ?: defaults.isBattleVibrationOn,
                gapAlertMeters =
                    when (battleSettings?.gapAlertInterval) {
                        GAP_500M -> 500
                        null -> defaults.gapAlertMeters
                        else -> 1_000
                    },
                isRankAlertOn = defaults.isRankAlertOn,
                shouldFollowCoachPlan = runSettings?.followCoachPlan ?: defaults.shouldFollowCoachPlan
            )
    )
}

/**
 * 서버 설정 원본 → 설정 화면 값(MemberSettings). values의 키는 changeSetting(item, value)의 item과 같다:
 * 서버 SettingItem 이름(UNIT·RECORD_VISIBILITY·COURSE_MAP_SHARED·PROFILE_VISIBILITY·WATCH_LINK), 러닝·대결 설정은
 * "RUN_SETTINGS.voice"처럼 하위 항목, 알림은 "ALERT.알림종류". 서버가 보내지 않은 항목은 넣지 않는다(화면이 기본값을 쓴다).
 */
internal fun MemberSettingsResponse.toMemberSettings(): MemberSettings {
    val values = linkedMapOf<String, String>()
    unit?.let { values[ITEM_UNIT] = it }
    recordVisibility?.let { values[ITEM_RECORD_VISIBILITY] = it }
    values[ITEM_COURSE_MAP_SHARED] = courseMapShared.toString()
    profileVisibility?.let { values[ITEM_PROFILE_VISIBILITY] = it }
    values[ITEM_WATCH_LINK] = watchLinked.toString()
    runSettings?.let {
        it.target?.let { target -> values["$GROUP_RUN.target"] = target }
        values["$GROUP_RUN.voice"] = it.voice.toString()
        values["$GROUP_RUN.vibration"] = it.vibration.toString()
        values["$GROUP_RUN.followCoachPlan"] = it.followCoachPlan.toString()
    }
    battleSettings?.let {
        it.defaultDistance?.let { distance -> values["$GROUP_BATTLE.defaultDistance"] = distance }
        values["$GROUP_BATTLE.voice"] = it.voice.toString()
        values["$GROUP_BATTLE.vibration"] = it.vibration.toString()
        values["$GROUP_BATTLE.gapAlertInterval"] = it.gapAlertInterval
    }
    notificationPrefs.forEach { (type, on) -> values["$GROUP_ALERT.$type"] = on.toString() }
    return MemberSettings(values = values, consents = consents)
}

/**
 * 러닝·대결 설정의 한 하위 항목만 바꾼 설정 JSON(서버는 RUN_SETTINGS·BATTLE_SETTINGS를 통째 JSON 값으로 받는다).
 * 현재 서버 값에 field를 덮어쓰고 서버에 값이 없던 항목은 서버 기본값(6.1.2)을 쓴다. group이 둘 중 하나가 아니면 null.
 */
internal fun MemberSettingsResponse.withSettingChanged(group: String, field: String, value: String): String? {
    val fields = linkedMapOf<String, JsonElement>()
    when (group) {
        GROUP_RUN -> {
            fields["target"] = JsonPrimitive(runSettings?.target ?: "FREE")
            fields["voice"] = JsonPrimitive(runSettings?.voice ?: true)
            fields["vibration"] = JsonPrimitive(runSettings?.vibration ?: true)
            fields["followCoachPlan"] = JsonPrimitive(runSettings?.followCoachPlan ?: false)
        }

        GROUP_BATTLE -> {
            fields["defaultDistance"] = JsonPrimitive(battleSettings?.defaultDistance ?: "KM5")
            fields["voice"] = JsonPrimitive(battleSettings?.voice ?: true)
            fields["vibration"] = JsonPrimitive(battleSettings?.vibration ?: true)
            fields["gapAlertInterval"] = JsonPrimitive(battleSettings?.gapAlertInterval ?: "KM1")
        }

        else -> return null
    }
    val booleanField = fields[field] is JsonPrimitive && (fields[field] as JsonPrimitive).booleanOrNull != null
    fields[field] = if (booleanField) JsonPrimitive(value.toBoolean()) else JsonPrimitive(value)
    return JsonObject(fields).toString()
}

internal const val GROUP_RUN = "RUN_SETTINGS"
internal const val GROUP_BATTLE = "BATTLE_SETTINGS"
internal const val GROUP_ALERT = "ALERT"
private const val ITEM_UNIT = "UNIT"
private const val ITEM_RECORD_VISIBILITY = "RECORD_VISIBILITY"
private const val ITEM_COURSE_MAP_SHARED = "COURSE_MAP_SHARED"
private const val ITEM_PROFILE_VISIBILITY = "PROFILE_VISIBILITY"
private const val ITEM_WATCH_LINK = "WATCH_LINK"
private const val CONSENT_LOCATION = "LOCATION"
private const val CONSENT_HEART_RATE = "HEART_RATE"
private const val GAP_500M = "M500"
