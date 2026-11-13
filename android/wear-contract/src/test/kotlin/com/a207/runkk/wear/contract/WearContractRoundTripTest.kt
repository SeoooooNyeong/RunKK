package com.a207.runkk.wear.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** 14장 계약 시험: wear.contract 메시지의 폰 ↔ 워치 직렬화 왕복(TC-6541·TC-6544~6547, TC-6862). */
class WearContractRoundTripTest {
    @Test
    fun `시작 요청 왕복`() {
        val message = StartRequestMessage(requestedAtEpochMillis = 1_760_000_000_000)
        assertEquals(message, StartRequestMessage.decode(message.encode()))
    }

    @Test
    fun `표시 정보 왕복 - 러닝 중`() {
        val message =
            WatchDisplayMessage(
                isRunning = true,
                elapsedSeconds = 1_234,
                distanceMeters = 4_321,
                currentPaceSecondsPerKm = 315,
                heartRateBpm = 152,
                planDistanceMeters = 5_000,
                planPaceSecondsPerKm = 330,
                isHeartRateCollectionAllowed = true,
                notice = null
            )
        assertEquals(message, WatchDisplayMessage.decode(message.encode()))
    }

    @Test
    fun `표시 정보 왕복 - 비어 있는 값과 안내 문구`() {
        val message =
            WatchDisplayMessage(
                isRunning = false,
                elapsedSeconds = 0,
                distanceMeters = 0,
                currentPaceSecondsPerKm = null,
                heartRateBpm = null,
                planDistanceMeters = null,
                planPaceSecondsPerKm = null,
                isHeartRateCollectionAllowed = false,
                notice = "폰에서 러닝 화면을 여세요"
            )
        assertEquals(message, WatchDisplayMessage.decode(message.encode()))
    }

    @Test
    fun `TC-6541 일시정지 항목이 든 표시 정보는 접두어 없는 JSON 키로 왕복한다`() {
        val message =
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
            )

        val keys = jsonKeys(message.encode())

        assertEquals(message, WatchDisplayMessage.decode(message.encode()))
        assertEquals(
            setOf(
                "running",
                "elapsedSeconds",
                "distanceMeters",
                "currentPaceSecondsPerKm",
                "heartRateBpm",
                "planDistanceMeters",
                "planPaceSecondsPerKm",
                "heartRateCollectionAllowed",
                "paused",
                "pausable"
            ),
            keys
        )
        assertTrue(keys.none { it.startsWith("is") }, "Kotlin is… 이름이 JSON 키로 샜다: $keys")
    }

    @Test
    fun `심박 묶음 왕복 - 실패 측정 표시 포함`() {
        val message =
            HeartRateBatchMessage(
                samples =
                    listOf(
                        HeartRateBatchMessage.Sample(1_000, 140, HeartRateBatchMessage.Status.OK),
                        HeartRateBatchMessage.Sample(2_000, 0, HeartRateBatchMessage.Status.FAILED)
                    )
            )
        assertEquals(message, HeartRateBatchMessage.decode(message.encode()))
    }

    @Test
    fun `종료 요청 왕복`() {
        val message = EndRequestMessage(requestedAtEpochMillis = 1_760_000_000_000)
        assertEquals(message, EndRequestMessage.decode(message.encode()))
    }

    @Test
    fun `TC-6544 일시정지·재개 요청 왕복`() {
        PauseRequestMessage.Action.entries.forEach { action ->
            val message = PauseRequestMessage(action = action, requestedAtEpochMillis = 1_800_000_000_000)
            val json = message.encode().toString(Charsets.UTF_8)

            assertEquals(message, PauseRequestMessage.decode(message.encode()))
            assertTrue(json.contains("\"action\":\"${action.name}\""), json)
        }
        assertEquals(listOf("PAUSE", "RESUME"), PauseRequestMessage.Action.entries.map { it.name })
    }

    @Test
    fun `TC-6545 근접 알림 왕복과 키 5개`() {
        val message =
            ProximityMessage(
                isActive = true,
                gapMeters = 50,
                intervalMillis = 1_105,
                isAhead = false,
                sentAtEpochMillis = 1_800_000_000_000
            )

        assertEquals(message, ProximityMessage.decode(message.encode()))
        assertEquals(
            setOf("active", "gapMeters", "intervalMillis", "ahead", "sentAtEpochMillis"),
            jsonKeys(message.encode())
        )
    }

    @Test
    fun `TC-6546 추월·역전 왕복`() {
        LeadChangeMessage.Kind.entries.forEach { kind ->
            val message = LeadChangeMessage(kind = kind, atEpochMillis = 1_800_000_000_000)
            val json = message.encode().toString(Charsets.UTF_8)

            assertEquals(message, LeadChangeMessage.decode(message.encode()))
            assertTrue(json.contains("\"kind\":\"${kind.name}\""), json)
        }
        assertEquals(listOf("OVERTOOK", "OVERTAKEN"), LeadChangeMessage.Kind.entries.map { it.name })
    }

    @Test
    fun `TC-6547 D-371 W40 W43 경로 14개는 서로 겹치지 않고 값이 설계와 같다`() {
        val paths =
            listOf(
                WearPaths.START_REQUEST,
                WearPaths.WATCH_DISPLAY,
                WearPaths.HEART_RATE_BATCH,
                WearPaths.END_REQUEST,
                WearPaths.PAUSE_REQUEST,
                WearPaths.PROXIMITY,
                WearPaths.LEAD_CHANGE,
                WearPaths.SESSION_REQUEST,
                WearPaths.WATCH_SESSION,
                WearPaths.SESSION_REVOKED,
                WearPaths.WATCH_MEASURING,
                WearPaths.RESEND_REJECTED,
                WearPaths.REJECTED_COUNT,
                WearPaths.DISPLAY_REQUEST
            )

        assertEquals(14, paths.toSet().size)
        assertEquals(
            listOf(
                "/runkk/start-request",
                "/runkk/watch-display",
                "/runkk/heart-rate-batch",
                "/runkk/end-request",
                "/runkk/pause-request",
                "/runkk/proximity",
                "/runkk/lead-change",
                "/runkk/session-request",
                "/runkk/watch-session",
                "/runkk/session-revoked",
                "/runkk/watch-measuring",
                "/runkk/resend-rejected",
                "/runkk/rejected-count",
                "/runkk/display-request"
            ),
            paths
        )
        // 한 경로가 다른 경로의 접두어면 심박 묶음처럼 접두어로 받는 쪽이 잘못 고른다.
        paths.forEach { path -> assertTrue(paths.none { it != path && it.startsWith("$path/") }, path) }
        assertEquals("runkk_watch", WearPaths.WATCH_CAPABILITY)
    }

    @Test
    fun `W43 거부 기록 다시 보내기 요청은 요청 시각 하나로 왕복하고 DataClient 항목이다(워치가 닿지 않아도 남는다)`() {
        val message = ResendRejectedMessage(requestedAtEpochMillis = 1_800_000_000_000)

        assertEquals(message, ResendRejectedMessage.decode(message.encode()))
        assertEquals(setOf("requestedAtEpochMillis"), jsonKeys(message.encode()))
        assertTrue(WearPaths.RESEND_REJECTED in WearPaths.DATA_ITEM_PATHS, "DataClient 항목이다")
    }

    @Test
    fun `W43 워치 거부 기록 건수는 건수와 알린 시각으로 왕복하고 DataClient 항목이다`() {
        val message = RejectedCountMessage(count = 3, atEpochMillis = 1_800_000_000_000)

        assertEquals(message, RejectedCountMessage.decode(message.encode()))
        assertEquals(setOf("count", "atEpochMillis"), jsonKeys(message.encode()))
        assertTrue(WearPaths.REJECTED_COUNT in WearPaths.DATA_ITEM_PATHS, "DataClient 항목이다")
    }

    @Test
    fun `S15P31A207-278 표시 요청은 본문 없는 MessageClient 경로다(DataClient 항목이 아니다)`() {
        assertEquals("/runkk/display-request", WearPaths.DISPLAY_REQUEST)
        assertFalse(WearPaths.DISPLAY_REQUEST in WearPaths.DATA_ITEM_PATHS, "일회성 요청이라 Data Layer에 남기지 않는다")
    }

    @Test
    fun `D-371 워치 로그인 정보 왕복과 키 5개`() {
        val message =
            WatchSessionMessage(
                memberId = 42,
                accountType = "SOCIAL",
                accessToken = "access.jwt",
                refreshToken = "refresh.jwt",
                issuedAtEpochMillis = 1_800_000_000_000
            )

        assertEquals(message, WatchSessionMessage.decode(message.encode()))
        assertEquals(
            setOf("memberId", "accountType", "accessToken", "refreshToken", "issuedAtEpochMillis"),
            jsonKeys(message.encode())
        )
    }

    @Test
    fun `D-371 로그아웃 표시 왕복과 키 2개`() {
        val message = SessionRevokedMessage(memberId = 42, revokedAtEpochMillis = 1_800_000_000_000)

        assertEquals(message, SessionRevokedMessage.decode(message.encode()))
        assertEquals(setOf("memberId", "revokedAtEpochMillis"), jsonKeys(message.encode()))
    }

    @Test
    fun `D-371 워치 측정 중 알림은 Boolean을 접두어 없는 measuring 키로 왕복한다`() {
        listOf(true, false).forEach { isMeasuring ->
            val message = WatchMeasuringMessage(isMeasuring = isMeasuring, atEpochMillis = 1_800_000_000_000)
            val json = message.encode().toString(Charsets.UTF_8)

            assertEquals(message, WatchMeasuringMessage.decode(message.encode()))
            assertEquals(setOf("measuring", "atEpochMillis"), jsonKeys(message.encode()))
            assertTrue(json.contains("\"measuring\":$isMeasuring"), json)
        }
    }

    @Test
    fun `H-58 ProximityMessage·of는 켜진 알림 값을 그대로 담고 off는 끔(0·0)이다`() {
        assertEquals(
            ProximityMessage(
                isActive = true,
                gapMeters = 35,
                intervalMillis = 780,
                isAhead = true,
                sentAtEpochMillis = 1_800_000_000_000
            ),
            ProximityMessage.of(
                gapMeters = 35,
                intervalMillis = 780,
                isAhead = true,
                sentAtEpochMillis = 1_800_000_000_000
            )
        )
        assertEquals(
            ProximityMessage(
                isActive = false,
                gapMeters = 0,
                intervalMillis = 0,
                isAhead = false,
                sentAtEpochMillis = 1_800_000_000_000
            ),
            ProximityMessage.off(sentAtEpochMillis = 1_800_000_000_000)
        )
    }

    private fun jsonKeys(bytes: ByteArray): Set<String> =
        Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject.keys
}
