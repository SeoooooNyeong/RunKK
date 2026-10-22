package com.a207.runkk.wear.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** 14장 계약 시험: wear.contract 메시지의 폰 ↔ 워치 직렬화 왕복. */
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
                running = true,
                elapsedSeconds = 1_234,
                distanceMeters = 4_321,
                currentPaceSecondsPerKm = 315,
                heartRateBpm = 152,
                planDistanceMeters = 5_000,
                planPaceSecondsPerKm = 330,
                heartRateCollectionAllowed = true,
                notice = null
            )
        assertEquals(message, WatchDisplayMessage.decode(message.encode()))
    }

    @Test
    fun `표시 정보 왕복 - 비어 있는 값과 안내 문구`() {
        val message =
            WatchDisplayMessage(
                running = false,
                elapsedSeconds = 0,
                distanceMeters = 0,
                currentPaceSecondsPerKm = null,
                heartRateBpm = null,
                planDistanceMeters = null,
                planPaceSecondsPerKm = null,
                heartRateCollectionAllowed = false,
                notice = "폰에서 러닝 화면을 여세요"
            )
        assertEquals(message, WatchDisplayMessage.decode(message.encode()))
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
    fun `모르는 항목이 더해져도 해독한다(한쪽만 먼저 배포된 경우)`() {
        val json = """{"requestedAtEpochMillis":5,"futureField":"x"}"""
        assertEquals(StartRequestMessage(5), StartRequestMessage.decode(json.toByteArray()))
    }

    @Test
    fun `경로 이름은 서로 겹치지 않는다`() {
        val paths = listOf(WearPaths.START_REQUEST, WearPaths.WATCH_DISPLAY, WearPaths.HEART_RATE_BATCH)
        assertEquals(paths.size, paths.toSet().size)
        paths.forEach { assertNotEquals(-1, it.indexOf("/runkk/")) }
    }

    @Test
    fun `종료 요청 왕복`() {
        val message = EndRequestMessage(requestedAtEpochMillis = 1_760_000_000_000)
        assertEquals(message, EndRequestMessage.decode(message.encode()))
        assertEquals("/runkk/end-request", WearPaths.END_REQUEST)
    }

    @Test
    fun `S-20 수집 허용 항목이 없는 옛 표시 정보는 수집 금지로 읽는다`() {
        val old = """{"running":true,"elapsedSeconds":10,"distanceMeters":30}""".toByteArray()
        assertEquals(false, WatchDisplayMessage.decode(old).heartRateCollectionAllowed)
    }
}
