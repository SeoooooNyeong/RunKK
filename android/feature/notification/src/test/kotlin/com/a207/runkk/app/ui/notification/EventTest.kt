package com.a207.runkk.app.ui.notification

import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkStatusChipTone
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.notification.Inbox
import com.a207.runkk.app.domain.notification.NotificationRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 이벤트 목록·알림함 이벤트 항목·홈 점 ViewModel, 카드 문구, 웹뷰 브리지 origin 검사(이벤트 계약 2절, 5절, 7절). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EventTest {
    private val events = FakeEventRepository()

    /** 2026-11-08 09:00(서울). 앱 시계는 UTC다. */
    private val clock = Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `목록을 읽으면 진행 중 이벤트를 본 것으로 남기고 탭별로 나눈다`() {
        events.seen = setOf(99L)
        val vm = EventListViewModel(events, clock)

        val state = vm.state.value
        assertEquals(setOf(1L, 2L), events.seen, "진행 중만 남기고 목록에 없는 옛 ID는 버린다")
        assertEquals(LocalDate.of(2026, 11, 8), state.today)
        assertEquals(listOf(1L, 2L, 3L), state.visible.map { it.id }, "진행 중 탭은 진행 중과 곧 열림")
        assertEquals(listOf(1L, 2L), state.banners.map { it.id })

        vm.onTab(EventTab.PAST)
        assertEquals(listOf(4L), vm.state.value.visible.map { it.id })
        assertEquals(emptyList(), vm.state.value.banners)
    }

    @Test
    fun `목록을 못 읽으면 오류를 보이고 본 사본을 바꾸지 않는다`() {
        events.result = Outcome.Failure(AppError.Network)
        val vm = EventListViewModel(events, clock)
        assertEquals(RunkkErrorText.of(AppError.Network), vm.state.value.error)
        assertEquals(emptySet(), events.seen)
    }

    @Test
    fun `카드를 누르면 상세 경로, 상세 주소가 없으면 열지 않는다`() {
        val coach = sampleEvents[0]
        assertEquals(
            Routes.EventDetail("coach-challenge", "코치님을 이겨라", "$ORIGIN/events/coach-challenge/"),
            eventDetailKey(coach)
        )
        assertNull(eventDetailKey(coach.copy(detailUrl = null)))
    }

    @Test
    fun `알림함 이벤트 항목은 진행 중 수와 안 본 이벤트 점을 보이고 목록을 본 뒤에는 점을 끈다`() {
        val inbox = InboxViewModel(EmptyNotifications, clock, events)
        assertEquals(InboxEventSummary(ongoingCount = 2, hasUnseen = true), inbox.state.value.eventSummary)

        EventListViewModel(events, clock)
        inbox.onResumed()
        inbox.onResumed()
        assertEquals(InboxEventSummary(ongoingCount = 2, hasUnseen = false), inbox.state.value.eventSummary)
    }

    @Test
    fun `이벤트가 없거나 못 읽으면 알림함 이벤트 항목을 숨긴다`() {
        events.result = Outcome.Success(emptyList())
        assertNull(InboxViewModel(EmptyNotifications, clock, events).state.value.eventSummary)
        events.result = Outcome.Failure(AppError.Network)
        assertNull(InboxViewModel(EmptyNotifications, clock, events).state.value.eventSummary)
    }

    @Test
    fun `홈 점은 안 본 진행 중 이벤트가 있을 때만 켠다`() {
        val dot = EventDotViewModel(events)
        dot.refresh()
        assertTrue(dot.hasUnseen.value)
        events.seen = setOf(1L, 2L)
        dot.refresh()
        assertFalse(dot.hasUnseen.value, "곧 열림, 지난 이벤트는 점을 켜지 않는다")
    }

    @Test
    fun `홈 점은 30초 안에 다시 보여도 이벤트를 서버에 다시 묻지 않고 본 목록만 다시 센다`() {
        var now = Instant.parse("2026-11-08T00:00:00Z")
        val ticking = object : Clock() {
            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId?): Clock = this

            override fun instant(): Instant = now
        }
        val dot = EventDotViewModel(events, com.a207.runkk.app.domain.settings.RefreshGate(), ticking)
        dot.refresh()
        events.seen = setOf(1L, 2L)
        now = now.plusSeconds(29)
        dot.refresh()
        assertEquals(1, events.eventsCalls, "30초 안은 요청 0건")
        assertFalse(dot.hasUnseen.value, "본 목록은 기기 값이라 바로 반영한다")

        now = now.plusSeconds(1)
        dot.refresh()
        assertEquals(2, events.eventsCalls)
    }

    @Test
    fun `상세 화면은 페이지를 열기 전에 내 참여를 한 번 읽는다`() {
        val vm = EventDetailViewModel(events)
        assertFalse(vm.isReady.value)
        vm.start("beta-1km")
        vm.start("beta-1km")
        assertTrue(vm.isReady.value)
        assertEquals(listOf("beta-1km"), events.meCalls)
        assertEquals(ORIGIN, vm.webOrigin)
    }

    @Test
    fun `카드 상태 칩, 기간, D-day`() {
        val res = RuntimeEnvironment.getApplication().resources
        val today = LocalDate.of(2026, 11, 8)
        val (coach, beta, upcoming, ended) = sampleEvents
        assertEquals(
            listOf(EventChip.NEW, EventChip.ENTERED, EventChip.UPCOMING, EventChip.ENDED),
            sampleEvents.map(EventChip::of)
        )
        // 피그마 상태 칩: 새 이벤트 보라, 참여 중 라임, 곧 열려요와 종료 회색(디자인 시스템 RunkkStatusChip)
        assertEquals(
            listOf(
                RunkkStatusChipTone.Violet,
                RunkkStatusChipTone.Lime,
                RunkkStatusChipTone.Muted,
                RunkkStatusChipTone.Muted
            ),
            EventChip.entries.map { it.tone }
        )
        assertEquals("11월 16일까지", eventPeriod(res, coach))
        assertEquals("11월 13일부터", eventPeriod(res, upcoming))
        assertEquals("11월 5일 종료", eventPeriod(res, ended))
        assertEquals(8L, eventDaysLeft(beta, today))
        assertEquals(5L, eventDaysLeft(upcoming, today))
        assertNull(eventDaysLeft(ended, today))
        assertEquals("D-8", eventDdayLabel(res, 8))
        assertEquals("D-DAY", eventDdayLabel(res, eventDaysLeft(coach, LocalDate.of(2026, 11, 16))!!))
    }

    @Test
    fun `origin 비교와 테마 붙이기`() {
        assertEquals(ORIGIN, webOriginOf("https://K15A207.p.ssafy.io:8443/events/a/?x=1"))
        assertEquals("https://k15a207.p.ssafy.io", webOriginOf("https://k15a207.p.ssafy.io:443/events/a/"))
        assertEquals("http://10.0.2.2:8080", webOriginOf("http://10.0.2.2:8080/events/a/"))
        assertNull(webOriginOf("file:///android_asset/index.html"))
        assertNull(webOriginOf("javascript:alert(1)"))
        assertNull(webOriginOf("https://evil.example\\@k15a207.p.ssafy.io:8443/"))
        assertFalse(isSameOrigin("https://k15a207.p.ssafy.io.evil.example:8443/", ORIGIN))
        assertFalse(isSameOrigin("http://k15a207.p.ssafy.io:8443/", ORIGIN), "scheme이 다르면 다른 origin")
        assertFalse(isSameOrigin("https://k15a207.p.ssafy.io/", ORIGIN), "포트가 다르면 다른 origin")
        assertEquals("$ORIGIN/events/a/?theme=dark", withTheme("$ORIGIN/events/a/", isDark = true))
        assertEquals("$ORIGIN/events/a/?v=2&theme=light#me", withTheme("$ORIGIN/events/a/?v=2#me", isDark = false))
        assertTrue(isExternalWebLink("https://naver.com"))
        assertFalse(isExternalWebLink("intent://scan/#Intent;scheme=zxing;end"))
    }

    @Test
    fun `브리지는 주 문서가 API origin일 때만 토큰을 주고 화면을 연다`() {
        var url: String? = "$ORIGIN/events/coach-challenge/?theme=dark"
        val opened = mutableListOf<EventPageTarget>()
        val shared = mutableListOf<String>()
        var closed = 0
        val bridge = RunkkAppBridge(
            allowedOrigin = ORIGIN,
            currentUrl = { url },
            accessToken = { "access-1" },
            runOnMain = { it() },
            onOpen = { opened += it },
            onClose = { closed++ },
            onShare = { shared += it }
        )

        assertEquals("access-1", bridge.getAccessToken())
        bridge.open("celebrity")
        bridge.open("run")
        bridge.open("events")
        bridge.open("settings")
        bridge.share("  코치님을 이겨라  ")
        bridge.share("   ")
        bridge.close()
        assertEquals(listOf(EventPageTarget.CELEBRITY, EventPageTarget.RUN, EventPageTarget.EVENTS), opened)
        assertEquals(listOf("코치님을 이겨라"), shared)
        assertEquals(1, closed)

        url = "https://evil.example/events/coach-challenge/"
        assertNull(bridge.getAccessToken())
        bridge.open("celebrity")
        bridge.share("x")
        bridge.close()
        assertEquals(3, opened.size)
        assertEquals(1, shared.size)
        assertEquals(1, closed)

        url = null
        assertNull(bridge.getAccessToken(), "주 문서를 모르면 믿지 않는다")
    }

    @Test
    fun `허용 origin을 읽을 수 없으면 어떤 주소도 같은 origin이 아니다`() {
        assertFalse(isSameOrigin(null, ""), "둘 다 null이라 같다고 보면 안 된다")
        assertFalse(isSameOrigin("file:///android_asset/x.html", "not a url"))
        val bridge = RunkkAppBridge(
            allowedOrigin = "",
            currentUrl = { null },
            accessToken = { "access-1" },
            runOnMain = { it() },
            onOpen = {},
            onClose = {},
            onShare = {}
        )
        assertNull(bridge.getAccessToken())
    }

    @Test
    fun `목록으로 돌아오면 응모 수를 다시 읽는다`() {
        val vm = EventListViewModel(events, clock)
        assertEquals(1, events.eventsCalls)
        vm.onResumed()
        assertEquals(1, events.eventsCalls, "처음 표시는 init이 읽는다")
        events.result = Outcome.Success(sampleEvents.map { it.copy(entryCount = 1) })
        vm.onResumed()
        assertEquals(2, events.eventsCalls)
        assertTrue(vm.state.value.events.all { it.entryCount == 1 })
    }

    private object EmptyNotifications : NotificationRepository {
        override suspend fun inbox(): Outcome<Inbox> = Outcome.Success(Inbox(emptyList()))

        override suspend fun open(notificationId: Long): Outcome<Unit> = Outcome.Success(Unit)
    }
}
