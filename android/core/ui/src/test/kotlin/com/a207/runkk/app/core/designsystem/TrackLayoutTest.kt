package com.a207.runkk.app.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 트랙 보기 위치 계산([임시:D-성-257], [임시:D-성-258], [임시:D-성-306]): 폭 30 m, 내 위치 40%, 결승선은 화면 안일 때만. */
class TrackLayoutTest {
    @Test
    fun `차이가 창 안이면 트랙 위치에 그린다`() {
        val ahead = TrackLayout.of(aheadMeters = 12.0, remainingMeters = 2_000.0)
        val behind = TrackLayout.of(aheadMeters = -9.0, remainingMeters = 2_000.0)
        val level = TrackLayout.of(aheadMeters = 0.0, remainingMeters = 2_000.0)

        assertEquals(0.4f, ahead.myX)
        assertEquals(TrackLayout.Other.OnTrack(0.8f), ahead.other)
        assertEquals(TrackLayout.Other.OnTrack(0.1f), behind.other)
        assertEquals(TrackLayout.Other.OnTrack(0.4f), level.other)
    }

    @Test
    fun `가장자리 칩이어도 자르지 않은 가로 위치(rawX)가 있어 트랙 밖에서 들어온다(S15P31A207-335)`() {
        assertEquals(0.9333f, TrackLayout.of(16.0, 2_000.0).otherRawX)
        assertEquals(0.9f, TrackLayout.of(15.0, 2_000.0).otherRawX, "창 안은 OnTrack x와 같다")
        assertEquals(0.0667f, TrackLayout.of(-10.0, 2_000.0).otherRawX)
        assertEquals(-3.5333f, TrackLayout.of(-118.0, 2_000.0).otherRawX)
        assertNull(TrackLayout.of(null, 2_000.0).otherRawX, "격차를 모르면 없다")
    }

    @Test
    fun `앞으로 창을 넘으면 오른쪽 가장자리 칩이다`() {
        assertEquals(TrackLayout.Other.Edge(isAhead = true, meters = 16), TrackLayout.of(16.0, 2_000.0).other)
        assertEquals(TrackLayout.Other.Edge(isAhead = true, meters = 240), TrackLayout.of(240.4, 2_000.0).other)
        assertEquals(TrackLayout.Other.OnTrack(0.9f), TrackLayout.of(15.0, 2_000.0).other, "15 m 앞은 아직 창 안이다")
    }

    @Test
    fun `뒤로 창을 넘으면 왼쪽 가장자리 칩이다`() {
        assertEquals(TrackLayout.Other.Edge(isAhead = false, meters = 10), TrackLayout.of(-10.0, 2_000.0).other)
        assertEquals(TrackLayout.Other.Edge(isAhead = false, meters = 118), TrackLayout.of(-118.0, 2_000.0).other)
        assertEquals(TrackLayout.Other.OnTrack(0.1f), TrackLayout.of(-9.0, 2_000.0).other, "9 m 뒤는 아직 창 안이다")
    }

    @Test
    fun `남은 거리 18 m 이내에서 결승선이 보인다`() {
        assertEquals(TrackLayout.Finish(1f), TrackLayout.of(null, 18.0).finish)
        assertEquals(TrackLayout.Finish(0.7f), TrackLayout.of(null, 9.0).finish)
        assertEquals(TrackLayout.Finish(0.4f), TrackLayout.of(null, 0.0).finish, "다 달리면 결승선이 내 자리다")
        assertEquals(TrackLayout.Finish(0.4f), TrackLayout.of(null, -3.0).finish, "넘어 달려도 내 자리다")
    }

    @Test
    fun `남은 거리가 30 m보다 크면 결승선이 없다`() {
        assertNull(TrackLayout.of(3.0, 30.5).finish)
        assertNull(TrackLayout.of(3.0, 2_660.0).finish)
        assertNull(TrackLayout.of(3.0, null).finish, "목표 거리가 없으면(제한 없는 크루런) 결승선이 없다")
    }

    @Test
    fun `결승선이 화면 밖이면 그리지 않는다`() {
        assertNull(TrackLayout.of(3.0, 30.0).finish, "남은 30 m는 화면 오른쪽 밖(x = 1.4)이다")
        assertNull(TrackLayout.of(3.0, 18.5).finish)
    }

    @Test
    fun `일시정지는 위치를 바꾸지 않는다`() {
        val running = TrackLayout.of(aheadMeters = 5.0, remainingMeters = 12.0)
        val paused = TrackLayout.of(aheadMeters = 5.0, remainingMeters = 12.0)

        assertEquals(running, paused, "같은 거리값이면 같은 자리다(동작은 칸이 맡는다)")
    }

    @Test
    fun `격차를 모르면 상대를 그리지 않는다`() {
        val layout = TrackLayout.of(aheadMeters = null, remainingMeters = 2_000.0)

        assertNull(layout.other)
        assertEquals(0.4f, layout.myX)
    }
}
