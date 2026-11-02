package com.a207.runkk.app.data.store

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.core.model.SignalState
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LateHeartRates
import com.a207.runkk.app.domain.run.PauseInterval
import com.a207.runkk.app.domain.run.RoutePoint
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.UnstableSegment
import com.a207.runkk.app.domain.run.UploadScheduler
import com.a207.runkk.app.domain.run.UploadState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** RoomPendingRunRepository(14장 단위(앱 데이터)): Room 메모리 DB로 엔티티 ↔ CompletedRun 왕복과 보관 규칙을 확인한다. */
@RunWith(RobolectricTestRunner::class)
class RoomPendingRunRepositoryTest {
    private val now = Instant.parse("2026-10-20T01:00:00Z")
    private val scheduler = CountingScheduler()
    private lateinit var db: PendingRunDatabase
    private lateinit var repository: RoomPendingRunRepository

    @BeforeTest
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PendingRunDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomPendingRunRepository(db, scheduler, Clock.fixed(now, ZoneOffset.UTC))
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `저장한 기록을 그대로 읽는다(엔티티 ↔ CompletedRun 왕복)`() = runTest {
        val run = completedRun("r-1")

        assertEquals(Outcome.Success(Unit), repository.save(run))

        assertEquals(Outcome.Success(listOf(run)), repository.pending())
        assertEquals(listOf(run), repository.observePending().first())
    }

    @Test
    fun `TC-6522 정지 구간 두 개를 순서와 밀리초까지 왕복한다`() = runTest {
        val pauses =
            listOf(
                PauseInterval(Instant.parse("2026-10-22T10:05:00.250Z"), Instant.parse("2026-10-22T10:07:00Z")),
                PauseInterval(Instant.parse("2026-10-22T10:15:00Z"), Instant.parse("2026-10-22T10:20:00Z"))
            )
        val run = completedRun("r-p").copy(pauses = pauses)

        repository.save(run)

        assertEquals(pauses, (repository.pending() as Outcome.Success).value.single().pauses)
    }

    @Test
    fun `워치에서 시작한 기록의 시작 기기를 왕복한다(D-378)`() = runTest {
        val run = completedRun("r-w").copy(startDevice = RunStartSpec.StartDevice.WATCH)

        repository.save(run)

        assertEquals(Outcome.Success(listOf(run)), repository.pending())
    }

    @Test
    fun `대결 기록의 ID와 보낸 상태도 왕복한다`() = runTest {
        val run =
            completedRun("r-2").copy(
                mode = RunMode.BATTLE,
                cardKind = CardKind.CELEB_RUN,
                battleId = 42,
                uploadState = UploadState.SENDING
            )

        repository.save(run)

        assertEquals(Outcome.Success(listOf(run)), repository.pending())
    }

    @Test
    fun `포인트는 압축 BLOB으로 담긴다`() = runTest {
        val manyPoints =
            (0 until 3_600).map { i ->
                RoutePoint(now.plusSeconds(i.toLong()), GeoPoint(37.5 + i * 1e-5, 127.0), SignalState.NORMAL)
            }
        repository.save(completedRun("r-3").copy(route = manyPoints))

        val entity = db.pendingRunDao().find("r-3")!!

        // 원시 크기(포인트당 시각 8 + 위도 8 + 경도 8 + 상태 1 바이트)보다 작다.
        assertTrue(entity.route.size < manyPoints.size * 25, "압축 크기 ${entity.route.size}")
        assertEquals(manyPoints, (repository.pending() as Outcome.Success).value.single().route)
    }

    @Test
    fun `remove하면 미전송 목록에서 빠진다`() = runTest {
        repository.save(completedRun("r-1"))
        repository.save(completedRun("r-2"))

        repository.remove("r-1")

        assertEquals(listOf("r-2"), (repository.pending() as Outcome.Success).value.map { it.recordId })
        assertEquals(listOf("r-2"), repository.observePending().first().map { it.recordId })
    }

    @Test
    fun `파기 지시는 모든 보관 기록의 경로나 심박만 지운다`() = runTest {
        val run = completedRun("r-1")
        repository.save(run)

        repository.purgeRoutes()
        assertEquals(listOf(run.purgeRoute()), (repository.pending() as Outcome.Success).value)

        repository.purgeHeartRates()
        assertEquals(listOf(run.purgeRoute().purgeHeartRates()), (repository.pending() as Outcome.Success).value)
    }

    @Test
    fun `20건이 쌓이거나 7일이 지난 기록이 있으면 재전송을 안내한다`() = runTest {
        repeat(19) { repository.save(completedRun("r-$it").copy(savedAt = now)) }
        assertEquals(Outcome.Success(false), repository.needsReminder(now))

        repository.save(completedRun("r-19").copy(savedAt = now))
        assertEquals(Outcome.Success(true), repository.needsReminder(now))

        repository.clear()
        repository.save(completedRun("old").copy(savedAt = now.minus(Duration.ofDays(7))))
        assertEquals(Outcome.Success(true), repository.needsReminder(now))
        assertEquals(Outcome.Success(false), repository.needsReminder(now.minus(Duration.ofSeconds(1))))
    }

    @Test
    fun `R12 D2-b 거부 표시는 미전송으로 남기고 사유를 왕복하며 그 뒤 덧붙은 늦은 심박을 덮지 않는다`() = runTest {
        val run = completedRun("r-1")
        repository.save(run)
        val handedOut = (repository.pending() as Outcome.Success).value.single()
        repository.appendHeartRates("r-1", listOf(HeartRateSample(now.plusSeconds(700), 150)))

        assertEquals(Outcome.Success(Unit), repository.markRejected(handedOut, "BAD_REQUEST"))

        val kept = (repository.pending() as Outcome.Success).value.single()
        assertEquals(UploadState.REJECTED, kept.uploadState)
        assertEquals("BAD_REQUEST", kept.rejectedReason)
        assertEquals(listOf(130, 140, 150), kept.heartRates.map { it.bpm })
        assertEquals(
            run.copy(uploadState = UploadState.REJECTED, rejectedReason = "BAD_REQUEST"),
            kept.copy(heartRates = run.heartRates)
        )
    }

    @Test
    fun `R12 D3 보낸 기록은 미전송 목록에서 빠지고 경로·심박·정지 구간을 지운 머리 정보만 남는다(D-77)`() = runTest {
        repository.save(completedRun("r-1"))

        repository.remove("r-1")

        assertEquals(Outcome.Success(emptyList()), repository.pending())
        val header = assertNotNull(db.pendingRunDao().find("r-1"))
        assertNotNull(header.sentAtMillis)
        assertEquals(0, header.route.size)
        assertEquals(0, header.heartRates.size)
        assertEquals(0, header.pauses.size)
    }

    @Test
    fun `미전송 기록에 늦은 심박을 시각 순서로 덧붙인다`() = runTest {
        repository.save(completedRun("r-1"))
        val late = listOf(HeartRateSample(now.plusSeconds(700), 150), HeartRateSample(now.plusSeconds(650), 148))

        assertEquals(Outcome.Success(Unit), repository.appendHeartRates("r-1", late))

        val heartRates = (repository.pending() as Outcome.Success).value.single().heartRates
        assertEquals(listOf(130, 140, 148, 150), heartRates.map { it.bpm })
        assertEquals(0, scheduler.requests, "아직 보내지 않은 기록은 다음 전송 때 함께 간다")
    }

    @Test
    fun `이미 보내 지운 기록에 늦은 심박이 오면 머리 정보와 늦은 심박으로 다시 보관하고 전송을 요청한다`() = runTest {
        val run = completedRun("r-1")
        repository.save(run)
        repository.remove("r-1")
        val late = listOf(HeartRateSample(now.plusSeconds(900), 151))

        assertEquals(Outcome.Success(Unit), repository.appendHeartRates("r-1", late))

        val revived = (repository.pending() as Outcome.Success).value.single()
        assertEquals(run.copy(route = emptyList(), unstable = emptyList(), heartRates = late), revived)
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun `M-1 머리 정보 없는 기록의 늦은 심박은 거부하지 않고 심박만 담은 항목으로 남기고 전송을 요청한다`() = runTest {
        val late = listOf(HeartRateSample(now.plusSeconds(30), 120), HeartRateSample(now.plusSeconds(10), 118))

        assertEquals(Outcome.Success(Unit), repository.appendHeartRates("unknown", late))

        assertEquals(
            Outcome.Success(listOf(LateHeartRates("unknown", late.sortedBy { it.at }))),
            repository.pendingLateHeartRates()
        )
        assertEquals(Outcome.Success(emptyList()), repository.pending(), "미전송 기록을 새로 만들지 않는다")
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun `같은 기록의 늦은 심박 항목은 시각 순서로 합치고 같은 시각은 한 번만 둔다`() = runTest {
        repository.appendHeartRates("unknown", listOf(HeartRateSample(now.plusSeconds(20), 121)))

        repository.appendHeartRates(
            "unknown",
            listOf(HeartRateSample(now.plusSeconds(20), 121), HeartRateSample(now.plusSeconds(5), 110))
        )

        val entry = (repository.pendingLateHeartRates() as Outcome.Success).value.single()
        assertEquals(listOf(110, 121), entry.samples.map { it.bpm })
    }

    @Test
    fun `removeLateHeartRates는 그 기록의 항목만 지운다`() = runTest {
        repository.appendHeartRates("x", listOf(HeartRateSample(now, 120)))
        repository.appendHeartRates("y", listOf(HeartRateSample(now, 121)))

        val sentX = (repository.pendingLateHeartRates() as Outcome.Success).value.first { it.recordId == "x" }

        assertEquals(Outcome.Success(Unit), repository.removeLateHeartRates(sentX))

        assertEquals(listOf("y"), (repository.pendingLateHeartRates() as Outcome.Success).value.map { it.recordId })
    }

    @Test
    fun `X-1 전송이 읽은 뒤 합쳐진 늦은 심박은 항목을 지워도 남는다`() = runTest {
        repository.appendHeartRates("x", listOf(HeartRateSample(now, 120)))
        val sent = (repository.pendingLateHeartRates() as Outcome.Success).value.single()
        val arrived = HeartRateSample(now.plusSeconds(5), 125)
        repository.appendHeartRates("x", listOf(arrived))

        assertEquals(Outcome.Success(Unit), repository.removeLateHeartRates(sent))

        assertEquals(
            Outcome.Success(listOf(LateHeartRates("x", listOf(arrived)))),
            repository.pendingLateHeartRates()
        )
    }

    @Test
    fun `X-1 이미 지워진 항목을 다시 지워도 성공하고 아무것도 만들지 않는다`() = runTest {
        val gone = LateHeartRates("x", listOf(HeartRateSample(now, 120)))

        assertEquals(Outcome.Success(Unit), repository.removeLateHeartRates(gone))

        assertEquals(Outcome.Success(emptyList()), repository.pendingLateHeartRates())
    }

    @Test
    fun `심박 파기와 탈퇴는 늦은 심박 항목도 지운다(FR-029, TF-VA-31)`() = runTest {
        repository.appendHeartRates("x", listOf(HeartRateSample(now, 120)))
        repository.purgeHeartRates()
        assertEquals(Outcome.Success(emptyList()), repository.pendingLateHeartRates())

        repository.appendHeartRates("y", listOf(HeartRateSample(now, 121)))
        repository.clear()
        assertEquals(Outcome.Success(emptyList()), repository.pendingLateHeartRates())
    }

    @Test
    fun `보낸 기록의 머리 정보는 기간이 아니라 최근 보낸 30건으로 남는다(D-77)`() = runTest {
        val clock = MutableClock(now)
        val counted = RoomPendingRunRepository(db, scheduler, clock)
        repeat(30) { i ->
            counted.save(completedRun("r-$i"))
            counted.remove("r-$i")
            clock.now = clock.now.plusSeconds(60)
        }
        clock.now = clock.now.plus(Duration.ofDays(3))

        // 사흘 뒤에 온 늦은 심박도 머리 정보에 덧붙는다(그 뒤 다시 보내면 r-0이 가장 최근 보낸 기록이 된다).
        val late = listOf(HeartRateSample(now.plusSeconds(1), 1))
        assertEquals(Outcome.Success(Unit), counted.appendHeartRates("r-0", late))
        assertEquals(listOf("r-0"), (counted.pending() as Outcome.Success).value.map { it.recordId })
        counted.remove("r-0")
        assertEquals(30, (0 until 30).count { db.pendingRunDao().find("r-$it") != null })

        // 31번째 보낸 기록이 생기면 가장 오래된 머리 정보(r-1)가 지워진다.
        clock.now = clock.now.plusSeconds(60)
        counted.save(completedRun("r-30"))
        counted.remove("r-30")

        assertNull(db.pendingRunDao().find("r-1"))
        assertNotNull(db.pendingRunDao().find("r-0"))
        assertNotNull(db.pendingRunDao().find("r-30"))
    }

    @Test
    fun `clear는 모두 지운다`() = runTest {
        repository.save(completedRun("r-1"))
        repository.remove("r-1")
        repository.save(completedRun("r-2"))

        repository.clear()

        assertEquals(Outcome.Success(emptyList()), repository.pending())
        assertNull(db.pendingRunDao().find("r-1"))
    }

    private fun completedRun(id: String) = CompletedRun(
        recordId = id,
        mode = RunMode.FREE,
        cardKind = CardKind.FREE_RUN,
        battleId = null,
        crewRunPostId = null,
        startedAt = now,
        elapsed = Duration.ofSeconds(605),
        distance = Distance(2_012),
        route =
            listOf(
                RoutePoint(now.plusSeconds(1), GeoPoint(37.501234, 127.034567), SignalState.NORMAL),
                RoutePoint(now.plusSeconds(2), GeoPoint(37.501301, 127.034601), SignalState.UNSTABLE)
            ),
        unstable = listOf(UnstableSegment(now.plusSeconds(100), now.plusSeconds(115), Distance(42))),
        heartRates = listOf(HeartRateSample(now.plusSeconds(10), 130), HeartRateSample(now.plusSeconds(20), 140)),
        savedAt = now.plusSeconds(606)
    )

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    private class CountingScheduler : UploadScheduler {
        var requests = 0

        override fun requestUpload() {
            requests++
        }
    }

    @Test
    fun `F-4 전송이 읽은 뒤 덧붙은 늦은 심박은 remove가 지우지 않고 다시 보낼 기록으로 남긴다`() = runTest {
        val run = completedRun("r-1")
        repository.save(run)
        repository.pending()
        val late = listOf(HeartRateSample(now.plusSeconds(700), 150))
        repository.appendHeartRates("r-1", late)

        repository.remove("r-1")

        val left = (repository.pending() as Outcome.Success).value.single()
        assertEquals(run.copy(route = emptyList(), unstable = emptyList(), heartRates = late), left)
        assertEquals(1, scheduler.requests, "남은 늦은 심박을 보내도록 전송을 요청한다")
    }
}
