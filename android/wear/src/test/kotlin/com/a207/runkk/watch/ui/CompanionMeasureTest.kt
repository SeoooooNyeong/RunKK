package com.a207.runkk.watch.ui

import com.a207.runkk.app.core.model.Distance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 폰 러닝 동행 표시 상태 → 워치 단독 측정 화면 상태(W35). */
class CompanionMeasureTest {
    private val running = WatchUiState(
        isPhoneConnected = true,
        isRunning = true,
        elapsedSeconds = 1_421,
        distanceMeters = 4_121,
        paceSecondsPerKm = 342,
        heartRateBpm = 152
    )

    @Test
    fun `자유런은 진행값을 옮기고 계획 거리를 목표로 둔다`() {
        val flow = assertIs<WatchFlow.Running>(
            running.copy(isPausable = true, planDistanceMeters = 5_000).toMeasureFlow()
        )

        assertEquals(WatchMode.FREE, flow.mode)
        assertTrue(flow.isPausable)
        assertEquals(Distance(5_000), flow.goal)
        assertEquals(1_421, flow.progress.elapsed.seconds)
        assertEquals(4_121, flow.progress.distance.meters)
        assertEquals(342, flow.progress.currentPace?.secondsPerKm)
        assertEquals(152, flow.progress.heartRate?.bpm)
        assertNull(flow.gapMeters)
        assertNull(flow.notice)
    }

    @Test
    fun `계획 거리가 없으면 목표 없이(무제한 고리) 보이고 빈 페이스와 심박은 비운다`() {
        val flow = assertIs<WatchFlow.Running>(
            running.copy(isPausable = true, paceSecondsPerKm = null, heartRateBpm = null).toMeasureFlow()
        )

        assertNull(flow.goal)
        assertNull(flow.progress.currentPace)
        assertNull(flow.progress.heartRate)
    }

    @Test
    fun `대결에서 앞서면 거리 차가 양수, 뒤처지면 음수다`() {
        val ahead = assertIs<WatchFlow.Running>(
            running.copy(proximity = WatchProximity(gapMeters = 18, isAhead = true, intervalMillis = 600))
                .toMeasureFlow()
        )
        val behind = assertIs<WatchFlow.Running>(
            running.copy(proximity = WatchProximity(gapMeters = 72, isAhead = false, intervalMillis = 1_510))
                .toMeasureFlow()
        )

        assertEquals(WatchMode.BATTLE, ahead.mode)
        assertFalse(ahead.isPausable)
        assertEquals(18, ahead.gapMeters)
        assertEquals(-72, behind.gapMeters)
    }

    @Test
    fun `대결인데 근접 값이 없으면(100m 밖, 끔) 거리 차 칸을 비운다`() {
        val flow = assertIs<WatchFlow.Running>(running.toMeasureFlow())

        assertEquals(WatchMode.BATTLE, flow.mode)
        assertNull(flow.gapMeters)
    }

    @Test
    fun `추월 역전 순간이면 역전 띠를 띄운다`() {
        val flow = assertIs<WatchFlow.Running>(
            running.copy(
                proximity = WatchProximity(gapMeters = 3, isAhead = true, intervalMillis = 300),
                leadKind = LeadKind.OVERTOOK
            ).toMeasureFlow()
        )

        assertTrue(flow.isLeadChanged)
        assertEquals(LeadKind.OVERTOOK, flow.leadKind)
        assertEquals(3, flow.gapMeters)
    }

    @Test
    fun `역전 띠 색은 근접 값이 없어도 방향이 정한다(WR15-5)`() {
        val overtook = assertIs<WatchFlow.Running>(running.copy(leadKind = LeadKind.OVERTOOK).toMeasureFlow())
        val overtaken = assertIs<WatchFlow.Running>(running.copy(leadKind = LeadKind.OVERTAKEN).toMeasureFlow())
        val ahead = WatchProximity(gapMeters = 2, isAhead = true, intervalMillis = 300)
        val overtakenWhileAhead = assertIs<WatchFlow.Running>(
            running.copy(proximity = ahead, leadKind = LeadKind.OVERTAKEN).toMeasureFlow()
        )

        assertEquals(WatchColors.Lime, leadBannerColor(overtook))
        assertEquals(WatchColors.Orange, leadBannerColor(overtaken))
        assertEquals(WatchColors.Orange, leadBannerColor(overtakenWhileAhead))
    }

    @Test
    fun `일시정지할 수 있는 자유런이 멈추면 일시정지 화면이다`() {
        val flow = assertIs<WatchFlow.Paused>(
            running.copy(isPaused = true, isPausable = true, planDistanceMeters = 3_000).toMeasureFlow()
        )

        assertEquals(4_121, flow.progress.distance.meters)
        assertTrue(flow.progress.isPaused)
        assertEquals(Distance(3_000), flow.goal)
    }

    @Test
    fun `일시정지 조작이 없는 러닝이 멈추면 측정 화면에 멈춘 값을 그대로 둔다`() {
        val flow = assertIs<WatchFlow.Running>(running.copy(isPaused = true).toMeasureFlow())

        assertFalse(flow.isPausable)
        assertTrue(flow.progress.isPaused)
    }

    @Test
    fun `폰 연결이 끊기면 연결 끊김 안내를 띄운다`() {
        val flow = assertIs<WatchFlow.Running>(running.copy(isLinkLost = true).toMeasureFlow())
        val paused = assertIs<WatchFlow.Paused>(
            running.copy(isLinkLost = true, isPaused = true, isPausable = true).toMeasureFlow()
        )

        assertEquals(WatchFlow.Notice.LINK_LOST, flow.notice)
        assertEquals(WatchFlow.Notice.LINK_LOST, paused.notice)
    }

    @Test
    fun `워치 안내는 안내로, 폰 안내 문구는 띠 문구로 옮긴다`() {
        val hold = assertIs<WatchFlow.Running>(running.copy(notice = WatchNotice.HoldToEnd).toMeasureFlow())
        val offline = assertIs<WatchFlow.Running>(
            running.copy(notice = WatchNotice.PhoneNotConnected, isLinkLost = true).toMeasureFlow()
        )
        val phone = assertIs<WatchFlow.Paused>(
            running.copy(isPaused = true, isPausable = true, notice = WatchNotice.FromPhone("곧 자동 종료돼요"))
                .toMeasureFlow()
        )

        assertEquals(WatchFlow.Notice.HOLD_TO_END, hold.notice)
        assertEquals(WatchFlow.Notice.PHONE_NOT_CONNECTED, offline.notice)
        assertNull(phone.notice)
        assertEquals("곧 자동 종료돼요", phone.banner)
    }
}
