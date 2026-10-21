package com.a207.runkk.app.api.account

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.api.core.TokenProvider
import com.a207.runkk.app.api.core.jwtOf
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.Gender
import com.a207.runkk.app.domain.account.MemberSettings
import com.a207.runkk.app.domain.account.Profile
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.ProviderType
import com.a207.runkk.app.domain.account.SessionTokens
import com.a207.runkk.app.domain.account.SignUpForm
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.create

/** api.account 매퍼·REST(14장 단위(앱 데이터) "api.<기능> 매퍼"): MockWebServer 응답 → 도메인 모델, 오류 → AppError. */
class AccountRepositoryImplTest {
    private val api = ApiTestServer()
    private val tokens = FixedTokens()
    private val repository =
        AccountRepositoryImpl(api.retrofit.create(), api.retrofit.create(), tokens, SelfMember(tokens))

    @AfterTest
    fun tearDown() = api.close()

    @Test
    fun `회원이면 토큰 쌍을 돌려준다`() = runTest {
        api.reply("""{"accessToken":"a","refreshToken":"r"}""")

        val result = repository.login(ProviderToken(ProviderType.KAKAO, "kakao-token"))

        assertEquals(Outcome.Success(SessionTokens("a", "r")), result)
        val sent = Json.parseToJsonElement(api.take().body!!.utf8()).jsonObject
        assertEquals("KAKAO", sent["provider"]!!.jsonPrimitive.content)
        assertEquals("kakao-token", sent["providerToken"]!!.jsonPrimitive.content)
    }

    @Test
    fun `가입 전이면 SIGN_UP_REQUIRED이고 이어지는 가입이 티켓을 싣는다`() = runTest {
        api.reply("""{"signUpTicket":"ticket-15m"}""")
        api.reply("""{"accessToken":"a","refreshToken":"r"}""")

        val login = repository.login(ProviderToken(ProviderType.GOOGLE, "g"))
        val signUp =
            repository.signUp(
                SignUpForm(
                    nickname = "달리는곰",
                    birthYear = 1995,
                    gender = Gender.FEMALE,
                    intro = null,
                    consents = mapOf("TERMS" to true, "LOCATION" to true),
                    recordVisibility = "PUBLIC",
                    tags = setOf("MORNING")
                )
            )

        assertEquals(Outcome.Failure(AppError.Rejected(AppError.Rejected.SIGN_UP_REQUIRED)), login)
        assertEquals(Outcome.Success(SessionTokens("a", "r")), signUp)
        api.take()
        val request = api.take()
        assertEquals("/api/v1/members", request.url.encodedPath)
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("ticket-15m", body["signUpTicket"]!!.jsonPrimitive.content)
        assertEquals("FEMALE", body["gender"]!!.jsonPrimitive.content)
    }

    @Test
    fun `티켓 없이 가입하면 보내지 않고 거부한다`() = runTest {
        val result = repository.signUp(
            SignUpForm(null, null, Gender.UNDISCLOSED, null, emptyMap(), "PUBLIC", emptySet())
        )

        assertEquals(Outcome.Failure(AppError.Rejected("SIGN_UP_TICKET_INVALID")), result)
        assertEquals(0, api.server.requestCount)
    }

    @Test
    fun `409는 서버 사유 코드로 거부된다`() = runTest {
        api.reply("""{"code":"NICKNAME_INVALID","message":"닉네임 오류"}""", code = 409)

        val result = repository.editProfile("x", null, emptySet())

        assertEquals(Outcome.Failure(AppError.Rejected("NICKNAME_INVALID")), result)
    }

    @Test
    fun `401은 Unauthorized, 5xx는 Server, 연결 실패는 Network`() = runTest {
        api.reply("{}", code = 401)
        api.reply("{}", code = 503)

        assertEquals(Outcome.Failure(AppError.Unauthorized), repository.withdraw())
        assertEquals(Outcome.Failure(AppError.Server(503)), repository.withdraw())
        api.close()
        assertEquals(Outcome.Failure(AppError.Network), repository.withdraw())
    }

    @Test
    fun `로그아웃은 갱신 토큰을 보낸다`() = runTest {
        api.replyEmpty(200)

        assertEquals(Outcome.Success(Unit), repository.logout())

        assertEquals("""{"refreshToken":"refresh-1"}""", api.take().body!!.utf8())
    }

    @Test
    fun `설정 변경과 동의는 갈래마다 다른 엔드포인트로 간다`() = runTest {
        repeat(3) { api.replyEmpty() }

        repository.changeSetting("WATCH_LINK", "true")
        repository.giveConsent("HEART_RATE")
        repository.withdrawConsent("HEART_RATE")

        val change = api.take()
        assertEquals("PATCH /api/v1/me/settings", "${change.method} ${change.url.encodedPath}")
        assertEquals("""{"item":"WATCH_LINK","value":"true"}""", change.body!!.utf8())
        val give = api.take()
        assertEquals("PUT /api/v1/me/consents/HEART_RATE", "${give.method} ${give.url.encodedPath}")
        val withdraw = api.take()
        assertEquals("DELETE /api/v1/me/consents/HEART_RATE", "${withdraw.method} ${withdraw.url.encodedPath}")
    }

    @Test
    fun `프로필 DTO를 도메인으로 바꾼다`() = runTest {
        api.reply(
            """{"memberId":7,"displayName":"달리는곰","disclosure":"PUBLIC","tier":"GOLD","level":12,
               |"tags":["MORNING"],"intro":"안녕","runimalType":"ENFP","faceKey":"face/7","crewName":"새벽런",
               |"longestDistanceMeters":21097,"ageBand":"30대","gender":"FEMALE","photoUrl":"https://img/7.png"}
            """.trimMargin()
        )

        val profile = repository.profile(7).value()

        assertEquals(
            Profile(
                7,
                "달리는곰",
                "PUBLIC",
                "GOLD",
                12,
                setOf("MORNING"),
                "안녕",
                "ENFP",
                "face/7",
                "새벽런",
                Distance(21_097),
                ageBand = "30대",
                gender = Gender.FEMALE,
                photoUrl = "https://img/7.png"
            ),
            profile
        )
        assertEquals("/api/v1/members/7/profile", api.take().url.encodedPath)
    }

    @Test
    fun `SELF 프로필은 토큰의 본인 회원 ID로 부른다`() = runTest {
        api.reply("""{"memberId":7,"displayName":"나","disclosure":"PUBLIC"}""")

        repository.profile(AccountRepository.SELF).value()

        assertEquals("/api/v1/members/7/profile", api.take().url.encodedPath)
    }

    @Test
    fun `본인 ID를 모르면 SELF 프로필은 보내지 않고 Unauthorized`() = runTest {
        val noToken =
            object : TokenProvider by tokens {
                override fun accessToken(): String? = null
            }
        val loggedOut =
            AccountRepositoryImpl(api.retrofit.create(), api.retrofit.create(), noToken, SelfMember(noToken))

        assertEquals(Outcome.Failure(AppError.Unauthorized), loggedOut.profile(AccountRepository.SELF))
        assertEquals(0, api.server.requestCount)
    }

    @Test
    fun `비공개 프로필은 빠진 항목을 null로 둔다`() = runTest {
        api.reply("""{"memberId":8,"displayName":"익명","disclosure":"PRIVATE"}""")

        val profile = repository.profile(8).value()

        assertEquals(null, profile.tier)
        assertEquals(emptySet(), profile.tags)
        assertEquals(null, profile.longestDistance)
    }

    @Test
    fun `고지문서는 서버가 주는 공개 주소를 돌려준다`() = runTest {
        api.reply("""{"type":"PRIVACY_POLICY","url":"https://runkk.example/legal/privacy"}""")

        assertEquals(Outcome.Success("https://runkk.example/legal/privacy"), repository.legalDocument("PRIVACY_POLICY"))
        assertEquals("/api/v1/legal/PRIVACY_POLICY", api.take().url.encodedPath)
    }

    @Test
    fun `고지문서에 주소도 본문도 없으면 실패로 돌려준다`() = runTest {
        api.reply("""{"type":"HELP"}""")

        assertEquals(Outcome.Failure(AppError.Server(0)), repository.legalDocument("HELP"))
    }

    @Test
    fun `로그인 실패는 서버가 본문 reason에 담은 사유 코드로 돌려준다`() = runTest {
        api.reply("""{"status":"FAILED","reason":"AUTH_FAILED","message":"인증 실패"}""", code = 401)
        api.reply("""{"status":"FAILED","reason":"PROVIDER_UNAVAILABLE","message":"로그인 제공자 연결 실패"}""", code = 503)

        assertEquals(
            Outcome.Failure(AppError.Rejected("AUTH_FAILED")),
            repository.login(ProviderToken(ProviderType.KAKAO, "bad"))
        )
        assertEquals(
            Outcome.Failure(AppError.Rejected("PROVIDER_UNAVAILABLE")),
            repository.login(ProviderToken(ProviderType.KAKAO, "down"))
        )
    }

    @Test
    fun `탈퇴가 가드에 막히면 409 본문의 거부 문구를 앱 사유 코드로 바꾼다`() = runTest {
        api.reply("""{"withdrawn":false,"rejectionReason":"크루장 양도 필요"}""", code = 409)
        api.reply("""{"withdrawn":false,"rejectionReason":"진행 중인 대결 종료 필요"}""", code = 409)
        api.reply("""{"withdrawn":false,"rejectionReason":"그 밖의 사유"}""", code = 409)

        assertEquals(Outcome.Failure(AppError.Rejected("LEADER_MUST_TRANSFER")), repository.withdraw())
        assertEquals(Outcome.Failure(AppError.Rejected("BATTLE_IN_PROGRESS")), repository.withdraw())
        assertEquals(Outcome.Failure(AppError.Rejected("WITHDRAWAL_REJECTED")), repository.withdraw())
    }

    @Test
    fun `가입 요청 본문은 서버 SignUpRequest 항목 이름과 서버 enum 이름을 쓴다`() = runTest {
        api.reply("""{"status":"SIGN_UP_REQUIRED","signUpTicket":"t"}""")
        api.reply("""{"memberId":9,"nickname":"달리는곰","accessToken":"a","refreshToken":"r"}""")
        repository.login(ProviderToken(ProviderType.KAKAO, "k"))

        repository.signUp(
            SignUpForm(
                nickname = null,
                birthYear = 1995,
                gender = Gender.UNDISCLOSED,
                intro = null,
                consents = mapOf("AGE_14_PLUS" to true, "TERMS" to true, "PRIVACY" to true, "LOCATION" to true),
                recordVisibility = "PUBLIC",
                tags = setOf("SOLO_RUN", "SHORT_DISTANCE")
            )
        )

        api.take()
        val body = Json.parseToJsonElement(api.take().body!!.utf8()).jsonObject
        assertEquals("UNDISCLOSED", body["gender"]!!.jsonPrimitive.content)
        assertEquals("PUBLIC", body["recordVisibility"]!!.jsonPrimitive.content)
        assertTrue("SOLO_RUN" in body["tags"].toString(), body["tags"].toString())
    }

    @Test
    fun `서버 설정 원본(MemberSettingsView)을 앱 사본으로 바꾼다`() = runTest {
        api.reply(
            """{"unit":"KM","recordVisibility":"PUBLIC","courseMapShared":true,"profileVisibility":"PRIVATE",
               |"notificationPrefs":{"PLAN_MISSED":true},"watchLinked":true,
               |"runSettings":{"target":"FREE","voice":false,"vibration":true,"followCoachPlan":true},
               |"battleSettings":{"defaultDistance":"KM5","voice":true,"vibration":false,"gapAlertInterval":"M500"},
               |"consents":{"AGE_14_PLUS":true,"TERMS":true,"PRIVACY":true,"LOCATION":true,"HEART_RATE":false},
               |"birthYear":1990}
            """.trimMargin()
        )

        val snapshot = SettingsRemoteImpl(api.retrofit.create()).fetch().value()

        assertEquals(
            SettingsSnapshot(
                locationConsented = true,
                heartRateConsented = false,
                watchLinked = true,
                courseMapShared = true,
                birthYear = 1990,
                guidanceSettings =
                    GuidanceSettings(
                        runVoice = false,
                        runVibration = true,
                        battleVoice = true,
                        battleVibration = false,
                        gapAlertMeters = 500,
                        rankAlert = true,
                        followCoachPlan = true
                    )
            ),
            snapshot
        )
        assertEquals("/api/v1/me/settings", api.take().url.encodedPath)
    }

    @Test
    fun `설정 화면 값은 changeSetting의 항목 키와 같은 키로 바꾼다`() = runTest {
        api.reply(
            """{"unit":"MI","recordVisibility":"PUBLIC","courseMapShared":true,"profileVisibility":"PRIVATE",
               |"notificationPrefs":{"PLAN_MISSED":false},"watchLinked":true,
               |"runSettings":{"target":"KM5","voice":false,"vibration":true,"followCoachPlan":true},
               |"battleSettings":{"defaultDistance":"KM3","voice":true,"vibration":false,"gapAlertInterval":"M500"},
               |"consents":{"LOCATION":true,"HEART_RATE":false}}
            """.trimMargin()
        )

        val settings = repository.settings().value()

        assertEquals(
            MemberSettings(
                values =
                    mapOf(
                        "UNIT" to "MI",
                        "RECORD_VISIBILITY" to "PUBLIC",
                        "COURSE_MAP_SHARED" to "true",
                        "PROFILE_VISIBILITY" to "PRIVATE",
                        "WATCH_LINK" to "true",
                        "RUN_SETTINGS.target" to "KM5",
                        "RUN_SETTINGS.voice" to "false",
                        "RUN_SETTINGS.vibration" to "true",
                        "RUN_SETTINGS.followCoachPlan" to "true",
                        "BATTLE_SETTINGS.defaultDistance" to "KM3",
                        "BATTLE_SETTINGS.voice" to "true",
                        "BATTLE_SETTINGS.vibration" to "false",
                        "BATTLE_SETTINGS.gapAlertInterval" to "M500",
                        "ALERT.PLAN_MISSED" to "false"
                    ),
                consents = mapOf("LOCATION" to true, "HEART_RATE" to false)
            ),
            settings
        )
        assertEquals("GET /api/v1/me/settings", api.take().let { "${it.method} ${it.url.encodedPath}" })
    }

    @Test
    fun `서버가 보내지 않은 설정 항목은 값에 넣지 않는다`() = runTest {
        api.reply("""{}""")

        val settings = repository.settings().value()

        assertEquals(mapOf("COURSE_MAP_SHARED" to "false", "WATCH_LINK" to "false"), settings.values)
        assertEquals(emptyMap(), settings.consents)
    }

    @Test
    fun `알림 설정은 항목 ALERT에 알림 종류를 실어 보낸다`() = runTest {
        api.replyEmpty()

        repository.changeSetting("ALERT.PLAN_MISSED", "false").value()

        val body = Json.parseToJsonElement(api.take().body!!.utf8()).jsonObject
        assertEquals("ALERT", body["item"]!!.jsonPrimitive.content)
        assertEquals("false", body["value"]!!.jsonPrimitive.content)
        assertEquals("PLAN_MISSED", body["notificationType"]!!.jsonPrimitive.content)
    }

    @Test
    fun `러닝 설정 한 항목만 바꾸면 서버 값을 읽어 합친 JSON을 보낸다`() = runTest {
        api.reply("""{"runSettings":{"target":"KM5","voice":true,"vibration":true,"followCoachPlan":false}}""")
        api.replyEmpty()

        repository.changeSetting("RUN_SETTINGS.voice", "false").value()

        assertEquals("/api/v1/me/settings", api.take().url.encodedPath)
        val patch = api.take()
        assertEquals("PATCH", patch.method)
        val body = Json.parseToJsonElement(patch.body!!.utf8()).jsonObject
        assertEquals("RUN_SETTINGS", body["item"]!!.jsonPrimitive.content)
        assertEquals(
            Json.parseToJsonElement("""{"target":"KM5","voice":false,"vibration":true,"followCoachPlan":false}"""),
            Json.parseToJsonElement(body["value"]!!.jsonPrimitive.content)
        )
    }

    @Test
    fun `대결 설정은 서버에 값이 없으면 기본값 위에 바꾼 항목을 얹는다`() = runTest {
        api.reply("""{}""")
        api.replyEmpty()

        repository.changeSetting("BATTLE_SETTINGS.gapAlertInterval", "M500").value()

        api.take()
        val body = Json.parseToJsonElement(api.take().body!!.utf8()).jsonObject
        assertEquals("BATTLE_SETTINGS", body["item"]!!.jsonPrimitive.content)
        assertEquals(
            Json.parseToJsonElement(
                """{"defaultDistance":"KM5","voice":true,"vibration":true,"gapAlertInterval":"M500"}"""
            ),
            Json.parseToJsonElement(body["value"]!!.jsonPrimitive.content)
        )
    }

    @Test
    fun `동의 항목이 빠진 설정은 동의 거짓으로 읽는다`() = runTest {
        api.reply("""{"watchLinked":false,"courseMapShared":false}""")

        val snapshot = SettingsRemoteImpl(api.retrofit.create()).fetch().value()

        assertTrue(!snapshot.locationConsented && !snapshot.heartRateConsented)
        assertEquals(GuidanceSettings.DEFAULT, snapshot.guidanceSettings)
    }

    private class FixedTokens : TokenProvider {
        override fun accessToken(): String? = jwtOf("7")

        override fun refreshToken(): String? = "refresh-1"

        override fun update(access: String, refresh: String) = Unit

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }
}
