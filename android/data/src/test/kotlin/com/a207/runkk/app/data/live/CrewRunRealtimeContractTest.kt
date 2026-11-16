package com.a207.runkk.app.data.live

import com.a207.runkk.app.domain.live.CrewRunChanged
import com.a207.runkk.app.domain.live.CrewRunDeparture
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 크루런 변경·출발 실시간 메시지 계약(설계 05-32 5.3, TC-11042 앱 부분). 표본은 백엔드 계약 표본
 * (backend/src/test/resources/contract/realtime)을 그대로 옮긴 것이다. 서버 시각은 오프셋 없는 Asia/Seoul LocalDateTime이다.
 */
class CrewRunRealtimeContractTest {
    private val decoder = ServerMessageDecoder()

    @Test
    fun `TC-11042 CrewRunChanged 표본 둘을 해독한다`() {
        assertEquals(
            CrewRunChanged(7, 101, CrewRunChanged.Resource.LOBBY, 18, "7:101:18:LOBBY", kst("2026-11-16T10:00:00Z")),
            decoder.decode(read("CrewRunChanged.json"))
        )
        assertEquals(
            CrewRunChanged(
                7,
                101,
                CrewRunChanged.Resource.SCHEDULE,
                3,
                "7:101:3:SCHEDULE",
                kst("2026-11-16T09:30:00.250Z")
            ),
            decoder.decode(read("CrewRunChanged-schedule.json"))
        )
    }

    @Test
    fun `TC-11042 CrewRunDeparture 표본은 startAt을 절대시각 그대로 읽고 후보가 비어도 읽는다`() {
        assertEquals(
            CrewRunDeparture(7, 101, 20, "101-18", kst("2026-11-16T10:00:03Z"), listOf(11)),
            decoder.decode(read("CrewRunDeparture.json"))
        )
        assertEquals(
            CrewRunDeparture(7, 102, 9, "102-7", kst("2026-11-15T22:00:03.500Z"), emptyList()),
            decoder.decode(read("CrewRunDeparture-empty.json"))
        )
    }

    @Test
    fun `TC-11042 모르는 자원 이름의 변경 신호는 버린다(당겨서 새로고침·재진입으로 맞춘다)`() {
        assertNull(
            decoder.decode(
                """{"type":"CrewRunChanged","crewId":7,"postId":101,"resource":"SETTLEMENT","revision":1,"eventId":"x","occurredAt":"2026-11-16T19:00:00"}"""
            )
        )
    }

    private fun kst(utc: String): Instant = Instant.parse(utc)

    private fun read(name: String): String =
        requireNotNull(javaClass.getResource("/contract/realtime/$name")) { "표본 없음: $name" }.readText()
}
