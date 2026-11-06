package com.a207.runkk.app.api.account

import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.api.core.enumByName
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.account.AccountType
import com.a207.runkk.app.domain.account.GuestSession
import com.a207.runkk.app.domain.account.MemberSettings
import com.a207.runkk.app.domain.account.MySummary
import com.a207.runkk.app.domain.account.PlacementProgress
import com.a207.runkk.app.domain.account.PlacementRound
import com.a207.runkk.app.domain.account.PlacementStatus
import com.a207.runkk.app.domain.account.Profile
import com.a207.runkk.app.domain.account.ProfileEdit
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.RecentRun
import com.a207.runkk.app.domain.account.SessionTokens
import com.a207.runkk.app.domain.account.SignUpForm
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.ScreenTheme
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import java.time.Duration
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// DTO ↔ 도메인 매퍼(13.2 toDomain·toRequest).

internal fun ProviderToken.toRequest(): LoginRequest = LoginRequest(provider = provider.name, providerToken = token)

/** 가입 요청은 티켓·닉네임·동의만 싣는다(D-90). */
internal fun SignUpForm.toRequest(ticket: String): SignUpRequest =
    SignUpRequest(signUpTicket = ticket, nickname = nickname, consents = consents)

internal fun GuestStartResponse.toDomain(): GuestSession =
    GuestSession(SessionTokens(access = accessToken, refresh = refreshToken), guestCredential)

internal fun TokenPairResponse.toDomain(): SessionTokens = SessionTokens(access = accessToken, refresh = refreshToken)

internal fun ProfileResponse.toDomain(): Profile = Profile(
    memberId = memberId,
    displayName = displayName,
    disclosure = disclosure,
    tier = tier,
    level = level,
    intro = intro,
    runimalType = runimalType,
    faceKey = faceKey,
    crewName = crewName,
    longestDistance = longestDistanceMeters?.let(::Distance),
    ageBand = ageBand,
    gender = enumByName<Gender>(gender),
    avatarBackgroundKey = avatarBackgroundKey,
    totalDistance = totalDistanceMeters?.let(::Distance),
    runCount = runCount,
    recentRuns = recentRuns.mapNotNull { it.toDomain() },
    isAgePublic = ageBandVisibility?.let { it == VISIBILITY_PUBLIC },
    isGenderPublic = genderVisibility?.let { it == VISIBILITY_PUBLIC },
    shownGender = enumByName<Gender>(shownGender)
)

/** 본인 프로필 → 본인 요약(D-331). 유효 러닝 수는 runCount(유효 기록만 센다, v0.12 D29)다. */
internal fun Profile.toMySummary(): MySummary = MySummary(
    displayName = displayName,
    tier = tier,
    level = level,
    faceKey = faceKey,
    avatarBackgroundKey = avatarBackgroundKey,
    validRuns = runCount
)

/**
 * 배치 진행 → 도메인(05-03 5.7.3.5). 페이스 0 이하는 없음. 모르는 상태 이름은 해독 실패(SerializationException → Server(0))로 본다:
 * 진행을 모르는 채 IN_PROGRESS·EXEMPT 가운데 하나로 짐작하지 않는다(진입 시트는 실패를 배치고사 화면으로 보낸다, D-499).
 */
internal fun PlacementResponse.toDomain(): PlacementProgress = PlacementProgress(
    status = enumByName<PlacementStatus>(status) ?: throw SerializationException("알 수 없는 배치 상태: $status"),
    countedRounds = countedRounds,
    nextRound = nextRound,
    provisionalTier = provisionalTier,
    tier = tier,
    rounds = rounds.map {
        PlacementRound(
            round = it.round,
            battleId = it.battleId,
            battleDistance = Distance(it.battleDistanceMeters),
            distance = Distance(it.distanceMeters),
            elapsed = Duration.ofSeconds(it.elapsedSeconds),
            averagePace = it.averagePaceSecondsPerKm?.takeIf { pace -> pace > 0 }?.let(::Pace),
            verdict = it.verdict
        )
    }
)

/** 날짜를 읽지 못한 줄은 버린다(프로필 전체를 실패시키지 않는다). */
private fun ProfileResponse.RecentRunDto.toDomain(): RecentRun? =
    runCatching { ServerTime.parseDate(startedOn) }.getOrNull()?.let { date ->
        RecentRun(kind = enumByName<CardKind>(kind), startedOn = date, distance = Distance(distanceMeters))
    }

/** 프로필 편집 요청(D-287): 스위치 켬 = PUBLIC. */
internal fun ProfileEdit.toRequest(): ProfileEditRequest = ProfileEditRequest(
    nickname = nickname,
    intro = intro,
    ageBandVisibility = if (isAgePublic) VISIBILITY_PUBLIC else VISIBILITY_PRIVATE,
    genderVisibility = if (isGenderPublic) VISIBILITY_PUBLIC else VISIBILITY_PRIVATE,
    gender = gender?.name,
    birthYear = birthYear
)

private const val VISIBILITY_PUBLIC = "PUBLIC"
private const val VISIBILITY_PRIVATE = "PRIVATE"

/**
 * 서버 설정 원본 → 앱 사본(D-53). 동의는 consents의 LOCATION·HEART_RATE(없으면 거짓).
 * 중간 알림 간격은 M500 → 500m, 그 밖 → 1km. 서버 설정에 순위 알림 항목이 없어 기본값(켬)을 쓴다(FR-190).
 * 자유런 목표(FreeRunTarget)는 KM3·KM5·KM10 → 3·5·10km, FREE와 모르는 값 → 목표 없음(FR-048).
 * 화면 테마는 모르는 값·없음이면 SYSTEM, 근접 알림은 없으면 참(D-162). 게스트 여부는 accountType을 AccountType으로 읽어 GUEST인지다(AN5-C-11).
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
                isFollowingCoachPlan = runSettings?.followCoachPlan ?: defaults.isFollowingCoachPlan,
                freeRunTarget = runSettings?.target?.let(FREE_RUN_TARGETS::get),
                isProximityAlertOn = battleSettings?.proximityAlert ?: defaults.isProximityAlertOn
            ),
        theme = enumByName<ScreenTheme>(theme) ?: ScreenTheme.SYSTEM,
        isGuest = enumByName<AccountType>(accountType) == AccountType.GUEST
    )
}

/** 서버 FreeRunTarget → 목표 거리. FREE(목표 없음)는 넣지 않는다. */
private val FREE_RUN_TARGETS: Map<String, Distance> =
    mapOf("KM3" to Distance(3_000), "KM5" to Distance(5_000), "KM10" to Distance(10_000))

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
    theme?.let { values[ITEM_THEME] = it }
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
        values["$GROUP_BATTLE.proximityAlert"] = it.proximityAlert.toString()
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
            fields["proximityAlert"] = JsonPrimitive(battleSettings?.proximityAlert ?: true)
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
private const val ITEM_THEME = "THEME"
private const val CONSENT_LOCATION = "LOCATION"
private const val CONSENT_HEART_RATE = "HEART_RATE"
private const val GAP_500M = "M500"
