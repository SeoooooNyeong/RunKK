package com.a207.runkk.app.api.account

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.api.core.TokenProvider
import com.a207.runkk.app.api.core.jwtOf
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.MemberSettings
import com.a207.runkk.app.domain.account.MySummary
import com.a207.runkk.app.domain.account.Profile
import com.a207.runkk.app.domain.account.ProfileEdit
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.ProviderType
import com.a207.runkk.app.domain.account.RecentRun
import com.a207.runkk.app.domain.account.SessionTokens
import com.a207.runkk.app.domain.account.SignUpForm
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.ScreenTheme
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import java.time.LocalDate
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
        AccountRepositoryImpl(api.retrofit.create(), api.retrofit.create(), tokens, SelfMember(tokens), api.faces)

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
                    consents = mapOf("TERMS" to true, "LOCATION" to true)
                )
            )

        assertEquals(Outcome.Failure(AppError.Rejected(AppError.Rejected.SIGN_UP_REQUIRED)), login)
        assertEquals(Outcome.Success(SessionTokens("a", "r")), signUp)
        api.take()
        val request = api.take()
        assertEquals("/api/v1/members", request.url.encodedPath)
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("ticket-15m", body["signUpTicket"]!!.jsonPrimitive.content)
        assertEquals("달리는곰", body["nickname"]!!.jsonPrimitive.content)
    }

    @Test
    fun `티켓 없이 가입하면 보내지 않고 거부한다`() = runTest {
        val result = repository.signUp(
            SignUpForm(null, emptyMap())
        )

        assertEquals(Outcome.Failure(AppError.Rejected("SIGN_UP_TICKET_INVALID")), result)
        assertEquals(0, api.server.requestCount)
    }

    @Test
    fun `409는 서버 사유 코드로 거부된다`() = runTest {
        api.reply("""{"code":"NICKNAME_INVALID","message":"닉네임 오류"}""", code = 409)

        val result = repository.editProfile(ProfileEdit("x", null, isAgePublic = false, isGenderPublic = false))

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
    fun `워치 세션은 본문 없이 POST auth·watch-session을 보내고 새 토큰 쌍을 돌려준다(D-372)`() = runTest {
        api.reply("""{"accessToken":"watch-a","refreshToken":"watch-r"}""")

        val result = repository.issueWatchSession()

        assertEquals(Outcome.Success(SessionTokens("watch-a", "watch-r")), result)
        val sent = api.take()
        assertEquals("POST /api/v1/auth/watch-session", "${sent.method} ${sent.url.encodedPath}")
        assertEquals(0L, sent.bodySize)
    }

    @Test
    fun `워치 세션 발급이 401이면 Unauthorized, 연결 실패는 Network다`() = runTest {
        api.reply("""{"code":"UNAUTHORIZED"}""", code = 401)
        assertEquals(Outcome.Failure(AppError.Unauthorized), repository.issueWatchSession())

        api.close()
        assertEquals(Outcome.Failure(AppError.Network), repository.issueWatchSession())
    }

    @Test
    fun `워치 세션 폐기는 본문 없이 DELETE auth·watch-session을 보내고 204면 성공이다(contract-v2 4절)`() = runTest {
        api.replyEmpty(204)

        val result = repository.revokeWatchSession()

        assertEquals(Outcome.Success(Unit), result)
        val sent = api.take()
        assertEquals("DELETE /api/v1/auth/watch-session", "${sent.method} ${sent.url.encodedPath}")
        assertEquals(0L, sent.bodySize)
    }

    @Test
    fun `워치 세션 폐기가 연결 실패면 Network다`() = runTest {
        api.close()

        assertEquals(Outcome.Failure(AppError.Network), repository.revokeWatchSession())
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
               |"intro":"안녕","runimalType":"ENFP","faceKey":"face/7","crewName":"새벽런",
               |"longestDistanceMeters":21097,"ageBand":"30대","gender":"FEMALE","photoUrl":"https://img/7.png",
               |"avatarBackgroundKey":"SEOUL_TOWER"}
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
                "안녕",
                "ENFP",
                "face/7",
                "새벽런",
                Distance(21_097),
                ageBand = "30대",
                gender = Gender.FEMALE,
                avatarBackgroundKey = "SEOUL_TOWER"
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
            AccountRepositoryImpl(api.retrofit.create(), api.retrofit.create(), noToken, SelfMember(noToken), api.faces)

        assertEquals(Outcome.Failure(AppError.Unauthorized), loggedOut.profile(AccountRepository.SELF))
        assertEquals(Outcome.Failure(AppError.Unauthorized), loggedOut.mySummary())
        assertEquals(null, loggedOut.selfMemberId())
        assertEquals(0, api.server.requestCount)
    }

    @Test
    fun `본인 회원 ID는 서버 호출 없이 접근 토큰에서 읽는다(워치 로그인 정보, SQ-43)`() {
        assertEquals(7L, repository.selfMemberId())
        assertEquals(0, api.server.requestCount)
    }

    @Test
    fun `본인 요약은 본인 프로필 응답 하나에서 쓰는 값만 옮기고 유효 러닝 수는 runCount다(D-331)`() = runTest {
        api.reply(
            """{"memberId":7,"displayName":"달리는곰","disclosure":"PUBLIC","tier":"UNRANKED","level":3,
               |"faceKey":"default:BEAR","avatarBackgroundKey":"BUSAN","runCount":2,"gender":"FEMALE",
               |"totalDistanceMeters":9000}
            """.trimMargin()
        )

        val summary = repository.mySummary().value()

        assertEquals(MySummary("달리는곰", "UNRANKED", 3, "default:BEAR", "BUSAN", 2), summary)
        assertEquals(Gender.FEMALE, repository.myGender.value)
        assertEquals("/api/v1/members/7/profile", api.take().url.encodedPath)
        assertEquals(1, api.server.requestCount)
    }

    @Test
    fun `로그아웃하면 내 성별 사본을 비운다(리뷰 AN10-5)`() = runTest {
        api.reply("""{"memberId":7,"displayName":"나","disclosure":"PUBLIC","tier":"UNRANKED","gender":"FEMALE"}""")
        repository.mySummary().value()
        assertEquals(Gender.FEMALE, repository.myGender.value)

        repository.forgetMyGender()

        assertEquals(null, repository.myGender.value)
    }

    @Test
    fun `옛 서버가 runCount를 주지 않으면 본인 요약의 유효 러닝 수는 null이다(D-331)`() = runTest {
        api.reply("""{"memberId":7,"displayName":"나","disclosure":"PUBLIC","tier":"UNRANKED"}""")

        assertEquals(null, repository.mySummary().value().validRuns)
    }

    @Test
    fun `비공개 프로필은 빠진 항목을 null로 둔다`() = runTest {
        api.reply("""{"memberId":8,"displayName":"익명","disclosure":"PRIVATE"}""")

        val profile = repository.profile(8).value()

        assertEquals(null, profile.tier)
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
    fun `로그인 실패는 서버 공통 오류 응답의 code를 사유 코드로 돌려준다`() = runTest {
        api.reply("""{"code":"AUTH_FAILED","message":"인증 실패"}""", code = 401)
        api.reply("""{"code":"PROVIDER_UNAVAILABLE","message":"로그인 제공자 연결 실패"}""", code = 503)

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
    fun `탈퇴가 가드에 막히면 409 공통 오류 응답의 code를 사유 코드로 돌려준다`() = runTest {
        api.reply("""{"code":"LEADER_MUST_TRANSFER","message":"크루장 양도 필요"}""", code = 409)
        api.reply("""{"code":"BATTLE_IN_PROGRESS","message":"진행 중인 대결 종료 필요"}""", code = 409)
        api.reply("""{"message":"그 밖의 사유"}""", code = 409)

        assertEquals(Outcome.Failure(AppError.Rejected("LEADER_MUST_TRANSFER")), repository.withdraw())
        assertEquals(Outcome.Failure(AppError.Rejected("BATTLE_IN_PROGRESS")), repository.withdraw())
        assertEquals(Outcome.Failure(AppError.Rejected("WITHDRAWAL_REJECTED")), repository.withdraw())
    }

    @Test
    fun `TC-6443 가입 요청 본문은 signUpTicket·nickname·consents뿐이다`() = runTest {
        api.reply("""{"status":"SIGN_UP_REQUIRED","signUpTicket":"t"}""")
        api.reply("""{"memberId":9,"nickname":"달리는곰","accessToken":"a","refreshToken":"r"}""")
        repository.login(ProviderToken(ProviderType.KAKAO, "k"))

        repository.signUp(
            SignUpForm(
                nickname = null,
                consents = mapOf("AGE_14_PLUS" to true, "TERMS" to true, "PRIVACY" to true, "LOCATION" to true)
            )
        )

        api.take()
        val body = Json.parseToJsonElement(api.take().body!!.utf8()).jsonObject
        assertEquals(setOf("signUpTicket", "consents"), body.keys - "nickname", "출생연도·성별·사진·소개·공개 범위·태그 0개(D-90)")
        assertEquals(4, body["consents"]!!.jsonObject.size)
    }

    @Test
    fun `로그인·가입 속도 제한(429 TOO_MANY_ATTEMPTS)은 사유 코드 거부로 돌려준다`() = runTest {
        api.reply("""{"code":"TOO_MANY_ATTEMPTS","message":"잠시 뒤 다시 시도해 주세요"}""", code = 429)
        api.reply("""{"status":"SIGN_UP_REQUIRED","signUpTicket":"t"}""")
        api.reply("""{"code":"TOO_MANY_ATTEMPTS","message":"잠시 뒤 다시 시도해 주세요"}""", code = 429)

        assertEquals(
            Outcome.Failure(AppError.Rejected("TOO_MANY_ATTEMPTS")),
            repository.login(ProviderToken(ProviderType.KAKAO, "k"))
        )
        repository.login(ProviderToken(ProviderType.KAKAO, "k"))
        val signedUp = repository.signUp(
            SignUpForm(
                nickname = null,
                consents = mapOf("AGE_14_PLUS" to true, "TERMS" to true, "PRIVACY" to true, "LOCATION" to true)
            )
        )
        assertEquals(Outcome.Failure(AppError.Rejected("TOO_MANY_ATTEMPTS")), signedUp)
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
                isLocationConsented = true,
                isHeartRateConsented = false,
                isWatchLinked = true,
                isCourseMapShared = true,
                birthYear = 1990,
                guidanceSettings =
                    GuidanceSettings(
                        isRunVoiceOn = false,
                        isRunVibrationOn = true,
                        isBattleVoiceOn = true,
                        isBattleVibrationOn = false,
                        gapAlertMeters = 500,
                        isRankAlertOn = true,
                        isFollowingCoachPlan = true
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
                        "BATTLE_SETTINGS.proximityAlert" to "true",
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
                """{"defaultDistance":"KM5","voice":true,"vibration":true,"gapAlertInterval":"M500","proximityAlert":true}"""
            ),
            Json.parseToJsonElement(body["value"]!!.jsonPrimitive.content)
        )
    }

    @Test
    fun `자유런 목표(FreeRunTarget)를 사본의 목표 거리로 옮긴다(FR-048)`() = runTest {
        val expected =
            linkedMapOf(
                "FREE" to null,
                "KM3" to Distance(3_000),
                "KM5" to Distance(5_000),
                "KM10" to Distance(10_000),
                "UNKNOWN" to null
            )
        expected.forEach { (target, distance) ->
            api.reply("""{"runSettings":{"target":"$target","voice":true,"vibration":true,"followCoachPlan":false}}""")

            val snapshot = SettingsRemoteImpl(api.retrofit.create()).fetch().value()

            assertEquals(distance, snapshot.guidanceSettings.freeRunTarget, target)
        }
    }

    @Test
    fun `동의 항목이 빠진 설정은 동의 거짓으로 읽는다`() = runTest {
        api.reply("""{"watchLinked":false,"courseMapShared":false}""")

        val snapshot = SettingsRemoteImpl(api.retrofit.create()).fetch().value()

        assertTrue(!snapshot.isLocationConsented && !snapshot.isHeartRateConsented)
        assertEquals(GuidanceSettings.DEFAULT, snapshot.guidanceSettings)
    }

    @Test
    fun `게스트 시작은 동의만 보내고 토큰과 자격 증명을 돌려준다`() = runTest {
        api.reply(
            """{"memberId":42,"nickname":"러너1234","accessToken":"A1","refreshToken":"R1","guestCredential":"g1.42.s"}""",
            code = 201
        )

        val started = repository.startAsGuest(
            mapOf("AGE_14_PLUS" to true, "TERMS" to true, "MARKETING" to false)
        ).value()

        assertEquals(SessionTokens("A1", "R1"), started.tokens)
        assertEquals("g1.42.s", started.guestCredential)
        val request = api.take()
        assertEquals("POST /api/v1/members/guest", "${request.method} ${request.url.encodedPath}")
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals(setOf("consents"), body.keys)
        assertEquals(3, body["consents"]!!.jsonObject.size)
    }

    @Test
    fun `게스트 시작 거부는 사유 코드로 돌려준다`() = runTest {
        api.reply("""{"code":"UNDER_14","message":"만 14세 이상"}""", code = 409)

        assertEquals(Outcome.Failure(AppError.Rejected("UNDER_14")), repository.startAsGuest(emptyMap()))
    }

    @Test
    fun `소셜 계정 연동은 제공자와 토큰을 보내고 거부 사유를 그대로 돌려준다`() = runTest {
        api.reply("""{"accountType":"LINKED","provider":"KAKAO"}""")
        api.reply("""{"code":"ALREADY_MEMBER","message":"이미 가입된 계정"}""", code = 409)
        api.reply("""{"code":"AUTH_FAILED","message":"인증 실패"}""", code = 401)
        api.reply("""{"code":"PROVIDER_UNAVAILABLE","message":"잠시 후"}""", code = 503)

        val kakao = ProviderToken(ProviderType.KAKAO, "kakao-token")
        assertEquals(Outcome.Success(Unit), repository.linkSocialAccount(kakao))
        assertEquals(Outcome.Failure(AppError.Rejected("ALREADY_MEMBER")), repository.linkSocialAccount(kakao))
        assertEquals(Outcome.Failure(AppError.Rejected("AUTH_FAILED")), repository.linkSocialAccount(kakao))
        assertEquals(Outcome.Failure(AppError.Rejected("PROVIDER_UNAVAILABLE")), repository.linkSocialAccount(kakao))
        val request = api.take()
        assertEquals("POST /api/v1/me/social-account", "${request.method} ${request.url.encodedPath}")
        assertEquals(
            Json.parseToJsonElement("""{"provider":"KAKAO","providerToken":"kakao-token"}"""),
            Json.parseToJsonElement(request.body!!.utf8())
        )
    }

    @Test
    fun `TC-9740 프로필 수정은 닉네임·소개·나이대·성별 공개만 보내고 사진·태그는 보내지 않는다`() = runTest {
        api.replyEmpty(200)

        repository.editProfile(ProfileEdit("달리는곰", "안녕", isAgePublic = true, isGenderPublic = false)).value()

        val body = Json.parseToJsonElement(api.take().body!!.utf8())
        assertEquals(
            Json.parseToJsonElement(
                """{"nickname":"달리는곰","intro":"안녕","ageBandVisibility":"PUBLIC","genderVisibility":"PRIVATE"}"""
            ),
            body
        )
    }

    @Test
    fun `프로필의 누적 거리·러닝 횟수·최근 러닝·공개 여부를 읽는다(D-287)`() = runTest {
        api.reply(
            """{"memberId":7,"displayName":"나","disclosure":"PUBLIC","totalDistanceMeters":128400,"runCount":42,
               |"recentRuns":[{"kind":"FREE_RUN","startedOn":"2026-10-20","distanceMeters":5020},
               |{"kind":"NEW_KIND","startedOn":"2026-10-18","distanceMeters":5000},
               |{"kind":"BATTLE_RUN","startedOn":"bad","distanceMeters":1}],
               |"ageBandVisibility":"PUBLIC","genderVisibility":"PRIVATE","photoUrl":null}
            """.trimMargin()
        )

        val profile = repository.profile(7).value()

        assertEquals(Distance(128_400), profile.totalDistance)
        assertEquals(42, profile.runCount)
        assertEquals(
            listOf(
                RecentRun(CardKind.FREE_RUN, LocalDate.of(2026, 10, 20), Distance(5_020)),
                RecentRun(null, LocalDate.of(2026, 10, 18), Distance(5_000))
            ),
            profile.recentRuns
        )
        assertEquals(true, profile.isAgePublic)
        assertEquals(false, profile.isGenderPublic)
    }

    @Test
    fun `다른 러너 프로필의 표시 성별을 읽는다(D-327)`() = runTest {
        api.reply("""{"memberId":8,"displayName":"바람","disclosure":"PUBLIC","shownGender":"FEMALE"}""")

        assertEquals(Gender.FEMALE, repository.profile(8).value().shownGender)
    }

    @Test
    fun `TC-9743 옛 서버 프로필은 새 항목 없이 해독된다`() = runTest {
        api.reply("""{"memberId":7,"displayName":"나","disclosure":"PUBLIC"}""")

        val profile = repository.profile(7).value()

        assertEquals(null, profile.totalDistance)
        assertEquals(null, profile.runCount)
        assertEquals(emptyList(), profile.recentRuns)
        assertEquals(null, profile.isAgePublic)
        assertEquals(null, profile.shownGender)
    }

    @Test
    fun `첫 사용 가이드 서버 값을 읽고 끝냄을 알린다(D-277)`() = runTest {
        api.reply("""{"seen":true}""")
        api.replyEmpty(204)
        api.reply("""{}""")

        val remote = GuideRemoteImpl(api.retrofit.create())

        assertEquals(Outcome.Success(true), remote.seen())
        assertEquals("GET /api/v1/me/guide", api.take().let { "${it.method} ${it.url.encodedPath}" })
        assertEquals(Outcome.Success(Unit), remote.markSeen())
        assertEquals("PUT /api/v1/me/guide", api.take().let { "${it.method} ${it.url.encodedPath}" })
        assertEquals(Outcome.Success(false), remote.seen())
    }

    @Test
    fun `설정 사본에 테마·근접 알림·게스트 여부를 옮긴다(D-92, D-162)`() = runTest {
        api.reply("""{"theme":"DARK","accountType":"GUEST","battleSettings":{"proximityAlert":false}}""")
        api.reply("""{"theme":"SEPIA","accountType":"LINKED","linkedProvider":"KAKAO"}""")
        api.reply("""{}""")

        val remote = SettingsRemoteImpl(api.retrofit.create())
        val guest = remote.fetch().value()
        val linked = remote.fetch().value()
        val empty = remote.fetch().value()

        assertEquals(ScreenTheme.DARK, guest.theme)
        assertTrue(guest.isGuest)
        assertEquals(false, guest.guidanceSettings.isProximityAlertOn)
        assertEquals(ScreenTheme.SYSTEM, linked.theme, "모르는 테마 이름은 SYSTEM")
        assertEquals(false, linked.isGuest)
        assertEquals(true, linked.guidanceSettings.isProximityAlertOn)
        assertEquals(ScreenTheme.SYSTEM, empty.theme)
        assertEquals(false, empty.isGuest)
    }

    @Test
    fun `설정 화면 값에 테마와 근접 알림이 들어가고 근접 알림 변경은 대결 설정 JSON에 실린다`() = runTest {
        api.reply("""{"theme":"LIGHT","battleSettings":{"defaultDistance":"KM3","proximityAlert":true}}""")
        api.reply("""{"battleSettings":{"defaultDistance":"KM3","proximityAlert":true}}""")
        api.replyEmpty()

        val values = repository.settings().value().values
        repository.changeSetting("BATTLE_SETTINGS.proximityAlert", "false").value()

        assertEquals("LIGHT", values["THEME"])
        assertEquals("true", values["BATTLE_SETTINGS.proximityAlert"])
        api.take()
        api.take()
        val body = Json.parseToJsonElement(api.take().body!!.utf8()).jsonObject
        val value = Json.parseToJsonElement(body["value"]!!.jsonPrimitive.content).jsonObject
        assertEquals("false", value["proximityAlert"]!!.jsonPrimitive.content)
        assertEquals("KM3", value["defaultDistance"]!!.jsonPrimitive.content)
    }

    private class FixedTokens : TokenProvider {
        override fun accessToken(): String? = jwtOf("7")

        override fun refreshToken(): String? = "refresh-1"

        override fun update(access: String, refresh: String) = Unit

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }
}
