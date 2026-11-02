package com.a207.runkk.wear.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames

/**
 * 하위 호환 계약 시험(F.4.4, D-97, TC-6542·6543·6549·6550, TC-6863).
 * 폰과 워치는 따로 갱신되므로 옛 JSON 해독, 모르는 키, 키 불변을 지킨다.
 */
class WearContractCompatibilityTest {
    @Test
    fun `TC-6542 paused·pausable 키가 없는 v0·10 표시 정보는 거짓으로 읽는다`() {
        val old = sample("watch-display-v010.json")

        val decoded = WatchDisplayMessage.decode(old)

        assertFalse(decoded.isPaused)
        assertFalse(decoded.isPausable)
        assertEquals(
            WatchDisplayMessage(
                isRunning = true,
                elapsedSeconds = 600,
                distanceMeters = 1_500,
                currentPaceSecondsPerKm = 330,
                heartRateBpm = 150,
                planDistanceMeters = 5_000,
                planPaceSecondsPerKm = 330,
                isHeartRateCollectionAllowed = true,
                notice = "폰에서 러닝 화면을 여세요"
            ),
            decoded
        )
    }

    @Test
    fun `TC-6543 모르는 키가 더해진 표시 정보도 해독한다`() {
        val json = """{"running":true,"elapsedSeconds":10,"distanceMeters":30,"futureKey":1}"""

        val decoded = WatchDisplayMessage.decode(json.toByteArray())

        assertTrue(decoded.isRunning)
        assertEquals(10L, decoded.elapsedSeconds)
        assertEquals(30L, decoded.distanceMeters)
    }

    @Test
    fun `모르는 항목이 더해져도 해독한다(한쪽만 먼저 배포된 경우)`() {
        assertEquals(
            StartRequestMessage(5),
            StartRequestMessage.decode("""{"requestedAtEpochMillis":5,"futureField":"x"}""".toByteArray())
        )
        assertEquals(
            PauseRequestMessage(PauseRequestMessage.Action.PAUSE, 5),
            PauseRequestMessage.decode("""{"action":"PAUSE","requestedAtEpochMillis":5,"x":1}""".toByteArray())
        )
        assertEquals(
            ProximityMessage(true, 20, 480, true, 5),
            ProximityMessage.decode(
                """{"active":true,"gapMeters":20,"intervalMillis":480,"ahead":true,"sentAtEpochMillis":5,"x":1}"""
                    .toByteArray()
            )
        )
        assertEquals(
            LeadChangeMessage(LeadChangeMessage.Kind.OVERTAKEN, 5),
            LeadChangeMessage.decode("""{"kind":"OVERTAKEN","atEpochMillis":5,"x":1}""".toByteArray())
        )
    }

    @Test
    fun `S-20 TC-6549 수집 허용 항목이 없는 옛 표시 정보는 수집 금지로 읽는다`() {
        val old = """{"running":true,"elapsedSeconds":10,"distanceMeters":30}""".toByteArray()
        assertFalse(WatchDisplayMessage.decode(old).isHeartRateCollectionAllowed)
    }

    @Test
    fun `모르는 action·kind 값은 해독 실패로 알린다(받는 쪽이 버린다)`() {
        assertFailsWith<SerializationException> {
            PauseRequestMessage.decode("""{"action":"STOP","requestedAtEpochMillis":1}""".toByteArray())
        }
        assertFailsWith<SerializationException> {
            LeadChangeMessage.decode("""{"kind":"TIED","atEpochMillis":1}""".toByteArray())
        }
    }

    @Test
    fun `TC-6550 v0·10 JSON 키는 하나도 바뀌거나 지워지지 않고 늘어난 키는 기본값이 있다`() {
        V010_KEYS.forEach { (serializer, oldKeys) ->
            val descriptor = serializer.descriptor
            val names = descriptor.elementNames.toList()
            assertTrue(names.containsAll(oldKeys), "${descriptor.serialName}: v0.10 키 $oldKeys 중 빠짐 → $names")
            names
                .filterNot { it in oldKeys }
                .forEach { added ->
                    assertTrue(
                        descriptor.isElementOptional(descriptor.getElementIndex(added)),
                        "${descriptor.serialName}.$added 는 기본값이 있어야 옛 상대가 보낸 JSON을 읽는다"
                    )
                }
        }
    }

    @Test
    fun `v0·11에 새로 생긴 메시지의 키는 설계 F·4·2와 같다`() {
        assertEquals(listOf("action", "requestedAtEpochMillis"), keysOf(PauseRequestMessage.serializer().descriptor))
        assertEquals(
            listOf("active", "gapMeters", "intervalMillis", "ahead", "sentAtEpochMillis"),
            keysOf(ProximityMessage.serializer().descriptor)
        )
        assertEquals(listOf("kind", "atEpochMillis"), keysOf(LeadChangeMessage.serializer().descriptor))
    }

    @Test
    fun `D-371 v0·22에 새로 생긴 메시지의 키는 설계와 같다`() {
        assertEquals(
            listOf("memberId", "accountType", "accessToken", "refreshToken", "issuedAtEpochMillis"),
            keysOf(WatchSessionMessage.serializer().descriptor)
        )
        assertEquals(listOf("memberId", "revokedAtEpochMillis"), keysOf(SessionRevokedMessage.serializer().descriptor))
        assertEquals(listOf("measuring", "atEpochMillis"), keysOf(WatchMeasuringMessage.serializer().descriptor))
    }

    @Test
    fun `D-371 NFR-050 비밀값을 가진 로그인 정보는 DataClient 경로로 가지 않는다`() {
        assertEquals(setOf(WearPaths.HEART_RATE_BATCH, WearPaths.SESSION_REVOKED), WearPaths.DATA_ITEM_PATHS)
        assertFalse(WearPaths.WATCH_SESSION in WearPaths.DATA_ITEM_PATHS, "토큰은 MessageClient로만 보낸다")
        assertFalse(
            WearPaths.DATA_ITEM_PATHS.any { WearPaths.WATCH_SESSION.startsWith("$it/") },
            "DataClient 접두어 경로 아래에도 두지 않는다"
        )
        val revokedKeys = keysOf(SessionRevokedMessage.serializer().descriptor)
        assertTrue(revokedKeys.none { it.contains("token", ignoreCase = true) }, "로그아웃 표시에는 비밀값이 없다: $revokedKeys")
    }

    @Test
    fun `NFR-050 로그인 정보의 문자열 표현은 토큰을 드러내지 않는다`() {
        val text = WatchSessionMessage(42, "GUEST", "access-secret", "refresh-secret", 5).toString()

        assertFalse("access-secret" in text || "refresh-secret" in text, text)
        assertTrue("42" in text, text)
    }

    @Test
    fun `D-371 새 메시지도 모르는 항목이 더해지면 해독한다`() {
        assertEquals(
            SessionRevokedMessage(42, 5),
            SessionRevokedMessage.decode("""{"memberId":42,"revokedAtEpochMillis":5,"x":1}""".toByteArray())
        )
        assertEquals(
            WatchMeasuringMessage(true, 5),
            WatchMeasuringMessage.decode("""{"measuring":true,"atEpochMillis":5,"x":1}""".toByteArray())
        )
        assertEquals(
            WatchSessionMessage(42, "SOCIAL", "a", "r", 5),
            WatchSessionMessage.decode(
                """{"memberId":42,"accountType":"SOCIAL","accessToken":"a","refreshToken":"r","issuedAtEpochMillis":5,"x":1}"""
                    .toByteArray()
            )
        )
    }

    @Test
    fun `W40 다시 보내기 요청은 모르는 항목이 더해져도 해독하고 키는 설계와 같다`() {
        assertEquals(listOf("requestedAtEpochMillis"), keysOf(ResendRejectedMessage.serializer().descriptor))
        assertEquals(
            ResendRejectedMessage(5),
            ResendRejectedMessage.decode("""{"requestedAtEpochMillis":5,"x":1}""".toByteArray())
        )
    }

    /** Kotlin Boolean 이름(is + 대문자, 예: isMeasuring)이 JSON 키로 새지 않는다. issuedAtEpochMillis 같은 단어는 해당하지 않는다. */
    @Test
    fun `직렬화 키는 is로 시작하지 않는다(D-97 @SerialName)`() {
        val descriptors =
            V010_KEYS.map { it.first.descriptor } +
                listOf(
                    PauseRequestMessage.serializer().descriptor,
                    ProximityMessage.serializer().descriptor,
                    LeadChangeMessage.serializer().descriptor,
                    WatchSessionMessage.serializer().descriptor,
                    SessionRevokedMessage.serializer().descriptor,
                    WatchMeasuringMessage.serializer().descriptor,
                    ResendRejectedMessage.serializer().descriptor
                )
        descriptors.forEach { descriptor ->
            assertTrue(
                keysOf(descriptor).none { BOOLEAN_PREFIX.matches(it) },
                "${descriptor.serialName}: ${keysOf(descriptor)}"
            )
        }
    }

    @Test
    fun `표본 JSON은 같은 메시지로 해독되고 다시 같은 JSON으로 직렬화된다(폰 단계가 같은 표본을 쓴다)`() {
        val samples =
            listOf(
                "watch-display.json" to { b: ByteArray -> WatchDisplayMessage.decode(b).encode() },
                "pause-request.json" to { b: ByteArray -> PauseRequestMessage.decode(b).encode() },
                "resume-request.json" to { b: ByteArray -> PauseRequestMessage.decode(b).encode() },
                "proximity.json" to { b: ByteArray -> ProximityMessage.decode(b).encode() },
                "proximity-off.json" to { b: ByteArray -> ProximityMessage.decode(b).encode() },
                "lead-change.json" to { b: ByteArray -> LeadChangeMessage.decode(b).encode() },
                "watch-session.json" to { b: ByteArray -> WatchSessionMessage.decode(b).encode() },
                "session-revoked.json" to { b: ByteArray -> SessionRevokedMessage.decode(b).encode() },
                "watch-measuring.json" to { b: ByteArray -> WatchMeasuringMessage.decode(b).encode() },
                "resend-rejected.json" to { b: ByteArray -> ResendRejectedMessage.decode(b).encode() }
            )
        samples.forEach { (name, reencode) ->
            val bytes = sample(name)
            assertEquals(bytes.toString(Charsets.UTF_8), reencode(bytes).toString(Charsets.UTF_8), name)
        }
        assertEquals(
            WatchDisplayMessage(
                isRunning = true,
                elapsedSeconds = 600,
                distanceMeters = 1_500,
                currentPaceSecondsPerKm = 330,
                heartRateBpm = 150,
                planDistanceMeters = 5_000,
                planPaceSecondsPerKm = 330,
                isHeartRateCollectionAllowed = true,
                notice = null,
                isPaused = true,
                isPausable = true
            ),
            WatchDisplayMessage.decode(sample("watch-display.json"))
        )
        assertEquals(
            ProximityMessage(
                isActive = true,
                gapMeters = 50,
                intervalMillis = 1_110,
                isAhead = false,
                sentAtEpochMillis = T
            ),
            ProximityMessage.decode(sample("proximity.json"))
        )
        assertFalse(ProximityMessage.decode(sample("proximity-off.json")).isActive)
        assertEquals(
            LeadChangeMessage(LeadChangeMessage.Kind.OVERTOOK, T),
            LeadChangeMessage.decode(sample("lead-change.json"))
        )
        assertEquals(
            PauseRequestMessage(PauseRequestMessage.Action.PAUSE, T),
            PauseRequestMessage.decode(sample("pause-request.json"))
        )
        assertEquals(
            WatchSessionMessage(42, "LINKED", "sample-access", "sample-refresh", T),
            WatchSessionMessage.decode(sample("watch-session.json"))
        )
        assertEquals(SessionRevokedMessage(42, T), SessionRevokedMessage.decode(sample("session-revoked.json")))
        assertEquals(WatchMeasuringMessage(true, T), WatchMeasuringMessage.decode(sample("watch-measuring.json")))
        assertEquals(ResendRejectedMessage(T), ResendRejectedMessage.decode(sample("resend-rejected.json")))
    }

    private fun keysOf(descriptor: SerialDescriptor): List<String> = descriptor.elementNames.toList()

    private fun sample(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/$SAMPLE_DIR/$name")) { "표본 없음: $name" }.use { it.readBytes() }

    private companion object {
        const val SAMPLE_DIR = "wear-contract-samples"
        const val T = 1_800_000_000_000L

        /** Kotlin Boolean 속성 이름 꼴(D-111 is·has·can 중 is). */
        val BOOLEAN_PREFIX = Regex("is[A-Z].*")

        /** v0.10(설계 v0.10)까지 배포된 메시지의 JSON 키 스냅숏. 지우거나 이름을 바꾸면 옛 상대가 깨진다. */
        val V010_KEYS: List<Pair<KSerializer<*>, List<String>>> =
            listOf(
                StartRequestMessage.serializer() to listOf("requestedAtEpochMillis"),
                EndRequestMessage.serializer() to listOf("requestedAtEpochMillis"),
                HeartRateBatchMessage.serializer() to listOf("samples"),
                HeartRateBatchMessage.Sample.serializer() to listOf("measuredAtEpochMillis", "bpm", "status"),
                WatchDisplayMessage.serializer() to
                    listOf(
                        "running",
                        "elapsedSeconds",
                        "distanceMeters",
                        "currentPaceSecondsPerKm",
                        "heartRateBpm",
                        "planDistanceMeters",
                        "planPaceSecondsPerKm",
                        "heartRateCollectionAllowed",
                        "notice"
                    )
            )
    }
}
