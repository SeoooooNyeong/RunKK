package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.live.CrewRunChanged
import com.a207.runkk.app.domain.live.CrewRunRelay
import com.a207.runkk.app.domain.testing.FakeLiveChannelProvider
import com.a207.runkk.app.domain.testing.T0
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** 변경 신호 임대(CrewRunSignals)와 다시 읽기 합치기(CrewReadSession), 설계 05-32 [임시:D-성-877]·[임시:D-성-880], TC-11039·11050. */
@OptIn(ExperimentalCoroutinesApi::class)
class CrewRealtimeReadTest {
    private fun changed(revision: Long, resource: CrewRunChanged.Resource = CrewRunChanged.Resource.LOBBY) =
        CrewRunChanged(7, 101, resource, revision, "7:101:$revision:$resource", T0)

    @Test
    fun `수집하는 동안만 임대를 빌려 변경 신호만 내보내고 멈추면 반납한다`() = runTest {
        val live = FakeLiveChannelProvider()
        val got = mutableListOf<CrewChange>()
        val job = backgroundScope.launch { CrewRunSignals(live).changes().collect { got += it } }
        runCurrent()
        assertEquals(1, live.opens)
        assertEquals(1, live.channel.connects)
        live.channel.messages.emit(changed(18))
        live.channel.messages.emit(CrewRunRelay(101, Distance(0), emptyList()))
        runCurrent()
        assertEquals(listOf(CrewChange(7, 101, CrewChange.Resource.LOBBY, 18)), got, "중계 등 다른 메시지는 내보내지 않는다")
        job.cancel()
        runCurrent()
        assertTrue(live.channel.closed, "숨으면 임대를 반납한다")
    }

    @Test
    fun `TC-11050 읽는 중에 온 요청은 끝난 뒤 한 번만 더 읽고 시간 주기 읽기는 없다`() = runTest {
        var reads = 0
        val gate = CompletableDeferred<Unit>()
        val session = CrewReadSession(backgroundScope) {
            reads++
            if (reads == 1) gate.await()
        }
        session.refresh()
        runCurrent()
        repeat(5) { session.refresh() }
        runCurrent()
        assertEquals(1, reads, "동시 요청은 하나")
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, reads, "후속은 한 번으로 합친다")
        testScheduler.advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, reads, "60초 동안 스스로 다시 읽지 않는다")
    }

    @Test
    fun `숨기면 읽기를 취소하고 남은 후속 요청을 버린다`() = runTest {
        var reads = 0
        val gate = CompletableDeferred<Unit>()
        val session = CrewReadSession(backgroundScope) {
            reads++
            gate.await()
        }
        session.refresh()
        runCurrent()
        session.refresh()
        session.cancel()
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, reads)
        assertFalse(session.isReading)
    }
}
