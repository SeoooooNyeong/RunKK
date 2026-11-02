package com.a207.runkk.watch.cache

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.OpponentTrack
import com.a207.runkk.app.domain.battle.RecordBattleKind
import com.a207.runkk.watch.testing.FakeBattleRepository
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.watch.testing.T0
import com.a207.runkk.watch.testing.celebrityListItem
import com.a207.runkk.watch.testing.celebrityTrack
import com.a207.runkk.watch.testing.selfTrack
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** 3.4 OpponentRecordCache: 셀프·셀럽 상대 기록 미리 받기(FR-260, D-377, M78). */
@OptIn(ExperimentalCoroutinesApi::class)
class OpponentRecordCacheTest {
    private val dir: File = createTempDirectory("opponent-cache").toFile()
    private val file = File(dir, "opponent-records.json")
    private val battles = FakeBattleRepository()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun TestScope.cache(): OpponentRecordCache = OpponentRecordCache(
        file,
        battles,
        SchedulerClock(testScheduler),
        backgroundScope,
        StandardTestDispatcher(testScheduler)
    )

    @Test
    fun `받은 셀프·셀럽 기록을 받은 시각과 함께 두고 거리로 셀럽을 고른다`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = listOf(selfTrack(3_000, 900))
        battles.tracks[RecordBattleKind.CELEBRITY] =
            listOf(celebrityTrack(1, 5_000, 1_500), celebrityTrack(2, 10_000, 3_000))
        val cache = cache()

        assertEquals(Outcome.Success(Unit), cache.refresh(memberId = 7))

        assertEquals(listOf(RecordBattleKind.SELF, RecordBattleKind.CELEBRITY), battles.calls)
        assertEquals(listOf(Distance(3_000)), cache.selfCandidates().map { it.record.distance })
        assertEquals(Instant.ofEpochMilli(T0), cache.selfCandidates().single().fetchedAt)
        assertEquals(listOf(2L), cache.celebrityCandidates(Distance(10_000)).map { it.record.celebrityId })
        assertTrue(cache.celebrityCandidates(Distance(1_000)).isEmpty())
    }

    @Test
    fun `파일에 둔 기록을 새 프로세스가 같은 진행표로 읽는다`() = runTest {
        val original = celebrityTrack(1, 5_000, 1_500)
        battles.tracks[RecordBattleKind.SELF] = listOf(selfTrack(3_000, 900))
        battles.tracks[RecordBattleKind.CELEBRITY] = listOf(original)
        cache().refresh(memberId = 7)
        battles.tracks.clear()

        // 연결 없이 다시 연 프로세스: 받기는 실패하지만 파일의 기록을 쓴다(FR-260 "마지막으로 받은 것").
        val reopened = cache()
        assertIs<Outcome.Failure>(reopened.refresh(memberId = 7))

        val restored = reopened.celebrityCandidates(Distance(5_000)).single().record
        assertEquals(original.displayName, restored.displayName)
        assertEquals(original.finishTime, restored.finishTime)
        assertEquals(original.sourceLabel, restored.sourceLabel)
        assertEquals(original.celebrityFigure, restored.celebrityFigure)
        listOf(0L, 1L, 299L, 750L, 1_499L, 1_500L, 2_000L).forEach { second ->
            val at = Duration.ofSeconds(second)
            assertEquals(original.track.distanceAt(at), restored.track.distanceAt(at), "경과 ${second}초")
        }
        assertEquals(original.track.arrivalTime(Distance(5_000)), restored.track.arrivalTime(Distance(5_000)))
    }

    @Test
    fun `받기가 실패하면 그 종류는 이전 것을 그대로 둔다`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = listOf(selfTrack(3_000, 900))
        battles.tracks[RecordBattleKind.CELEBRITY] = listOf(celebrityTrack(1, 5_000, 1_500))
        val cache = cache()
        cache.refresh(memberId = 7)
        battles.tracks.remove(RecordBattleKind.CELEBRITY)
        battles.tracks[RecordBattleKind.SELF] = listOf(selfTrack(3_000, 800), selfTrack(5_000, 1_600))

        assertIs<Outcome.Failure>(cache.refresh(memberId = 7))

        assertEquals(2, cache.selfCandidates().size)
        assertEquals(1, cache.celebrityCandidates(Distance(5_000)).size)
    }

    @Test
    fun `다른 회원으로 받으면 이전 회원의 기록을 남기지 않는다`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = listOf(selfTrack(3_000, 900))
        battles.tracks[RecordBattleKind.CELEBRITY] = listOf(celebrityTrack(1, 5_000, 1_500))
        cache().refresh(memberId = 7)
        battles.tracks.clear()

        val other = cache()
        assertIs<Outcome.Failure>(other.refresh(memberId = 8))

        assertTrue(other.selfCandidates().isEmpty())
        assertTrue(other.celebrityCandidates(Distance(5_000)).isEmpty())
    }

    @Test
    fun `받는 동안 지우면(로그아웃) 받은 기록을 되살리지 않는다`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = listOf(selfTrack(3_000, 900))
        battles.tracks[RecordBattleKind.CELEBRITY] = emptyList()
        val gate = CompletableDeferred<Unit>()
        battles.gate = gate
        val cache = cache()
        val refreshing = async { cache.refresh(memberId = 7) }
        runCurrent()

        cache.clear()
        gate.complete(Unit)
        refreshing.await()
        advanceUntilIdle()

        assertTrue(cache.selfCandidates().isEmpty())
        assertFalse(file.exists())
    }

    @Test
    fun `clear는 메모리와 파일을 모두 지운다`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = listOf(selfTrack(3_000, 900))
        battles.tracks[RecordBattleKind.CELEBRITY] = emptyList()
        val cache = cache()
        cache.refresh(memberId = 7)
        assertTrue(file.exists())

        cache.clear()

        assertTrue(cache.selfCandidates().isEmpty())
        assertFalse(file.exists())
        assertTrue(cache().selfCandidates().isEmpty())
    }

    @Test
    fun `셀프런 새 최고 기록은 더 빠를 때만 그 거리의 상대 기록을 바꾸고 파일에도 남긴다`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = listOf(selfTrack(3_000, 900))
        battles.tracks[RecordBattleKind.CELEBRITY] = emptyList()
        val cache = cache()
        cache.refresh(memberId = 7)

        cache.onNewSelfBest(Distance(3_000), OpponentTrack.uniform(Distance(3_000), Duration.ofSeconds(950)))
        assertEquals(Duration.ofSeconds(900), cache.selfCandidates().single().record.finishTime)

        cache.onNewSelfBest(Distance(3_000), OpponentTrack.uniform(Distance(3_000), Duration.ofSeconds(850)))
        advanceUntilIdle()

        assertEquals(Duration.ofSeconds(850), cache.selfCandidates().single().record.finishTime)
        battles.tracks.clear()
        val reopened = cache()
        reopened.refresh(memberId = 7)
        assertEquals(Duration.ofSeconds(850), reopened.selfCandidates().single().record.finishTime)
    }

    @Test
    fun `셀럽 목록은 구간 기록 없이 두고 고를 때 받은 구간 기록을 파일에 남겨 다음 프로세스도 쓴다(W31)`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = listOf(selfTrack(3_000, 900))
        battles.tracks[RecordBattleKind.CELEBRITY] = listOf(celebrityListItem(1, 5_000, 1_500))
        battles.celebrityTracks[1] = Outcome.Success(celebrityTrack(1, 5_000, 1_500))
        val cache = cache()
        cache.refresh(memberId = 7)

        val listed = cache.celebrityCandidates(Distance(5_000)).single()
        assertFalse(listed.hasTrack)
        assertTrue(cache.selfCandidates().single().hasTrack)
        assertNull(cache.readyCelebrity(1))
        assertTrue(battles.celebrityTrackCalls.isEmpty(), "목록을 받을 때는 셀럽 하나를 받지 않는다")

        val fetched = cache.fetchCelebrityTrack(1)

        assertEquals(listOf(1L), battles.celebrityTrackCalls)
        assertTrue(assertIs<Outcome.Success<CachedTrack>>(fetched).value.hasTrack)
        assertEquals(Duration.ofSeconds(1_500), cache.readyCelebrity(1)?.record?.track?.arrivalTime(Distance(5_000)))
        advanceUntilIdle()

        battles.tracks.clear()
        val reopened = cache()
        reopened.refresh(memberId = 7)
        val restored = assertNotNull(reopened.readyCelebrity(1)).record
        assertEquals("셀럽1", restored.displayName)
        assertEquals(Duration.ofSeconds(1_500), restored.track.arrivalTime(Distance(5_000)))
        assertEquals(Distance(2_500), restored.track.distanceAt(Duration.ofSeconds(750)))
    }

    @Test
    fun `목록을 다시 받아도 받아 둔 셀럽 구간 기록은 남고 그 셀럽 기록이 바뀌면 버린다(W31)`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = emptyList()
        battles.tracks[RecordBattleKind.CELEBRITY] =
            listOf(celebrityListItem(1, 5_000, 1_500), celebrityListItem(2, 5_000, 1_600))
        battles.celebrityTracks[1] = Outcome.Success(celebrityTrack(1, 5_000, 1_500))
        val cache = cache()
        cache.refresh(memberId = 7)
        cache.fetchCelebrityTrack(1)

        cache.refresh(memberId = 7)
        assertNotNull(cache.readyCelebrity(1))
        assertNull(cache.readyCelebrity(2))

        battles.tracks[RecordBattleKind.CELEBRITY] = listOf(celebrityListItem(1, 5_000, 1_400))
        cache.refresh(memberId = 7)
        assertNull(cache.readyCelebrity(1), "완주 시간이 바뀐 기록은 다시 받는다")
        assertEquals(Duration.ofSeconds(1_400), cache.celebrityCandidates(Distance(5_000)).single().record.finishTime)
    }

    @Test
    fun `셀럽 구간 기록 받기가 실패하면 그 실패이고 빈 구간 기록은 고를 수 없는 기록이다(W31)`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = emptyList()
        battles.tracks[RecordBattleKind.CELEBRITY] =
            listOf(celebrityListItem(1, 5_000, 1_500), celebrityListItem(2, 5_000, 1_600))
        battles.celebrityTracks[2] = Outcome.Success(celebrityListItem(2, 5_000, 1_600))
        val cache = cache()
        cache.refresh(memberId = 7)

        assertEquals(Outcome.Failure(AppError.Network), cache.fetchCelebrityTrack(1))
        battles.celebrityTracks[1] = Outcome.Failure(AppError.Rejected("CELEBRITY_REMOVED"))
        assertEquals(Outcome.Failure(AppError.Rejected("CELEBRITY_REMOVED")), cache.fetchCelebrityTrack(1))
        assertIs<AppError.Rejected>(assertIs<Outcome.Failure>(cache.fetchCelebrityTrack(2)).error)

        assertNull(cache.readyCelebrity(1))
        assertNull(cache.readyCelebrity(2))
    }

    @Test
    fun `셀럽 구간 기록을 받는 동안 지우면(로그아웃) 받은 기록을 남기지 않는다(W31)`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = emptyList()
        battles.tracks[RecordBattleKind.CELEBRITY] = listOf(celebrityListItem(1, 5_000, 1_500))
        battles.celebrityTracks[1] = Outcome.Success(celebrityTrack(1, 5_000, 1_500))
        val cache = cache()
        cache.refresh(memberId = 7)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        battles.celebrityGate = gate
        val fetching = async { cache.fetchCelebrityTrack(1) }
        runCurrent()

        cache.clear()
        gate.complete(Unit)
        fetching.await()
        advanceUntilIdle()

        assertNull(cache.readyCelebrity(1))
        assertFalse(file.exists())
    }

    @Test
    fun `다른 회원으로 받으면 받아 둔 셀럽 구간 기록도 남기지 않는다(W31)`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = emptyList()
        battles.tracks[RecordBattleKind.CELEBRITY] = listOf(celebrityListItem(1, 5_000, 1_500))
        battles.celebrityTracks[1] = Outcome.Success(celebrityTrack(1, 5_000, 1_500))
        val cache = cache()
        cache.refresh(memberId = 7)
        cache.fetchCelebrityTrack(1)
        advanceUntilIdle()

        val other = cache()
        other.refresh(memberId = 8)

        assertNull(other.readyCelebrity(1))
        assertFalse(other.celebrityCandidates(Distance(5_000)).single().hasTrack)
    }

    @Test
    fun `목록을 받는 동안 받은 셀럽 구간 기록은 목록이 나중에 적용돼도 남는다(W31)`() = runTest {
        battles.tracks[RecordBattleKind.SELF] = emptyList()
        battles.tracks[RecordBattleKind.CELEBRITY] = listOf(celebrityListItem(1, 5_000, 1_500))
        battles.celebrityTracks[1] = Outcome.Success(celebrityTrack(1, 5_000, 1_500))
        val cache = cache()
        cache.refresh(memberId = 7)
        val gate = CompletableDeferred<Unit>()
        battles.gate = gate
        val refreshing = async { cache.refresh(memberId = 7) }
        runCurrent()

        battles.gate = null
        cache.fetchCelebrityTrack(1)
        gate.complete(Unit)
        refreshing.await()
        advanceUntilIdle()

        assertNotNull(cache.readyCelebrity(1))
        battles.tracks.clear()
        assertNotNull(cache().readyCelebrity(1), "파일에도 남는다")
    }
}
