package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.Ranking
import com.a207.runkk.app.domain.record.RecordList
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

/**
 * 받아 둔 첫 화면 먼저 보여 주기(S15P31A207-345 2차): 랭킹·기록 화면은 시작 로딩이 남긴 사본을 먼저 그리고 뒤에서 새로 받아 바꾼다.
 * 새로 받지 못해도 사본을 오류 화면으로 덮지 않는다. 사본이 없으면 지금처럼 받는 동안 로딩, 실패면 오류 화면이다.
 */
class ScreenCopyViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val records = FakeRecordRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-20T01:00:00Z"), ZoneId.of("Asia/Seoul"))

    private val copy = Ranking(listOf(Ranking.Entry(1, "사본", Distance(30_000))), null, null, null, null)
    private val fresh = Ranking(listOf(Ranking.Entry(1, "새것", Distance(31_000))), null, null, null, null)

    @Test
    fun `랭킹은 받아 둔 사본을 먼저 그리고 새로 받으면 바꾼다`() {
        records.rankingCopies[AggregationPeriod.WEEKLY] = copy
        records.rankingByPeriod[AggregationPeriod.WEEKLY] = Outcome.Success(fresh)
        val gate = CompletableDeferred<Unit>()
        records.rankingGates[AggregationPeriod.WEEKLY] = gate

        val vm = RankingViewModel(records, clock)
        assertEquals(copy, vm.state.value.ranking, "받는 동안 사본을 그린다")
        assertTrue(vm.state.value.isLoading, "뒤에서 새로 받는 중")

        gate.complete(Unit)
        assertEquals(fresh, vm.state.value.ranking)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `랭킹을 새로 받지 못해도 사본이 있으면 오류 화면으로 덮지 않는다`() {
        records.rankingCopies[AggregationPeriod.WEEKLY] = copy
        records.rankingResult = Outcome.Failure(AppError.Network)

        val vm = RankingViewModel(records, clock)
        assertEquals(copy, vm.state.value.ranking)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `랭킹 사본이 없으면 지금처럼 실패를 오류 화면으로 보인다`() {
        records.rankingResult = Outcome.Failure(AppError.Network)
        val vm = RankingViewModel(records, clock)
        assertNull(vm.state.value.ranking)
        assertTrue(vm.state.value.error != null)
    }

    @Test
    fun `기록 목록은 이번 달 사본을 먼저 그리고 새로 받으면 바꾼다`() {
        val today = LocalDate.of(2026, 10, 20)
        records.runsCopy = RecordList(listOf(summary("copy", 3, 3_000)))
        records.dashboardCopy = today to dashboard(monthRuns = 1, monthMeters = 3_000)
        val gate = CompletableDeferred<Unit>()
        records.runsGate = gate

        val vm = RecordListViewModel(records, FakePendingRunRepository(), clock, ZoneId.of("Asia/Seoul"))
        assertEquals(listOf("copy"), vm.state.value.runs.map { it.recordId }, "받는 동안 사본을 그린다")
        assertTrue(vm.state.value.summary != null)

        gate.complete(Unit)
        assertEquals(listOf("r3", "r2", "r1"), vm.state.value.runs.map { it.recordId })
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `기록 목록을 새로 받지 못해도 사본을 둔다`() {
        records.runsCopy = RecordList(listOf(summary("copy", 3, 3_000)))
        records.runsResult = Outcome.Failure(AppError.Network)

        val vm = RecordListViewModel(records, FakePendingRunRepository(), clock, ZoneId.of("Asia/Seoul"))
        assertEquals(listOf("copy"), vm.state.value.runs.map { it.recordId })
        assertNull(vm.state.value.error)
    }
}
