package com.a207.runkk.wear.contract

import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `TC-6547 경로 7개는 서로 겹치지 않고 값이 설계와 같다`() {
        val paths =
            listOf(
                WearPaths.START_REQUEST,
                WearPaths.WATCH_DISPLAY,
                WearPaths.HEART_RATE_BATCH,
                WearPaths.END_REQUEST,
                WearPaths.PAUSE_REQUEST,
                WearPaths.PROXIMITY,
                WearPaths.LEAD_CHANGE
            )

        assertEquals(7, paths.toSet().size)
        assertEquals(
            listOf(
                "/runkk/start-request",
                "/runkk/watch-display",
                "/runkk/heart-rate-batch",
                "/runkk/end-request",
                "/runkk/pause-request",
                "/runkk/proximity",
                "/runkk/lead-change"
            ),
            paths
        )
        // 한 경로가 다른 경로의 접두어면 심박 묶음처럼 접두어로 받는 쪽이 잘못 고른다.
        paths.forEach { path -> assertTrue(paths.none { it != path && it.startsWith("$path/") }, path) }
        assertEquals("runkk_watch", WearPaths.WATCH_CAPABILITY)
    }

    private fun jsonKeys(bytes: ByteArray): Set<String> =
        Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject.keys
}
