package com.a207.runkk.app.ui.record

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.ui.share.CardSharer
import com.a207.runkk.app.ui.share.CardSide
import com.a207.runkk.app.ui.share.RunCardRenderer
import com.a207.runkk.app.ui.share.ShareOutcome
import com.a207.runkk.app.ui.share.ShareTarget
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class CountingTarget(private val outcome: ShareOutcome = ShareOutcome.Shared) : ShareTarget {
    var count = 0

    override fun share(context: Context, image: Uri): ShareOutcome {
        count++
        return outcome
    }
}

@RunWith(RobolectricTestRunner::class)
class RecordViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val records = FakeRecordRepository()
    private val pending = FakePendingRunRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-20T01:00:00Z"), ZoneId.of("Asia/Seoul"))

    @Test
    fun `기록 목록은 이번 달 본인 기록을 읽고 날짜별로 더한다(FR-057·FR-058)`() {
        val vm = RecordListViewModel(records, pending, clock)
        val s = vm.state.value
        assertEquals(YearMonth.of(2026, 10), s.month)
        assertEquals(
            Triple(RecordRepository.SELF, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
            records.runsCalls.single()
        )
        assertEquals(Distance(12_200), s.dailyDistance[LocalDate.of(2026, 10, 14)])
        assertEquals(3, s.runs.size)
        assertEquals(listOf("r3", "r2", "r1"), s.runs.map { it.recordId })
        assertEquals(2, s.validCount)
        assertEquals(Distance(15_220), s.validDistance)
    }

    @Test
    fun `달을 옮기면 그 달을 읽는다`() {
        val vm = RecordListViewModel(records, pending, clock)
        vm.onPreviousMonth()
        assertEquals(YearMonth.of(2026, 9), vm.state.value.month)
        assertEquals(LocalDate.of(2026, 9, 1), records.runsCalls.last().second)
        vm.onNextMonth()
        vm.onNextMonth()
        assertEquals(YearMonth.of(2026, 11), vm.state.value.month)
    }

    @Test
    fun `미전송 기록 수를 함께 보이고 목록 오류는 다시 읽는다(FR-039)`() {
        pending.runs.value = listOf(pendingRun("p1"))
        records.runsResult = Outcome.Failure(AppError.Network)
        val vm = RecordListViewModel(records, pending, clock)
        assertEquals(1, vm.state.value.pendingCount)
        assertNotNull(vm.state.value.error)
        records.runsResult = Outcome.Success(com.a207.runkk.app.domain.record.RecordList(emptyList()))
        vm.refresh()
        assertNull(vm.state.value.error)
    }

    @Test
    fun `보기를 달력·목록으로 바꾼다`() {
        val vm = RecordListViewModel(records, pending, clock)
        vm.onViewSelected(RecordView.LIST)
        assertEquals(RecordView.LIST, vm.state.value.view)
    }

    @Test
    fun `기록 상세를 읽고 실패하면 오류를 보인다(FR-059)`() {
        val vm = RecordDetailViewModel(records)
        vm.load("r1")
        assertEquals("12°C 맑음", vm.state.value.detail?.weather)
        records.runResult = Outcome.Failure(AppError.Network)
        val failing = RecordDetailViewModel(records)
        failing.load("r9")
        assertNull(failing.state.value.detail)
        assertEquals(RunkkErrorText.of(AppError.Network), failing.state.value.error)
    }

    private fun cardViewModel(shared: Boolean, target: CountingTarget = CountingTarget()) = RecordCardViewModel(
        records,
        RunCardRenderer(ApplicationProvider.getApplicationContext()),
        CardSharer(ApplicationProvider.getApplicationContext(), FakeSettingsRepository(shared)) { _, file: File ->
            Uri.fromFile(file)
        },
        CardShareTargets(save = target, system = target, story = target)
    )

    @Test
    fun `카드는 앞·뒷면을 그리고 앞면을 기기에 저장한다`() {
        val target = CountingTarget(ShareOutcome.Saved)
        val vm = cardViewModel(shared = true, target)
        vm.load("r1")
        eventually { !vm.state.value.isLoading }
        assertNotNull(vm.state.value.front)
        assertNotNull(vm.state.value.back)
        vm.onShare(CardShareKind.SAVE)
        eventually { vm.state.value.message != null }
        assertEquals(1, target.count)
        assertEquals(UiText.Res(R.string.record_card_saved), vm.state.value.message)
    }

    @Test
    fun `코스 지도 공유가 꺼져 있으면 뒷면 공유를 막고 이유를 보인다(FR-069)`() {
        val target = CountingTarget()
        records.cardResult = Outcome.Success(runCard(shared = false))
        val vm = cardViewModel(shared = false, target)
        vm.load("r1")
        eventually { !vm.state.value.isLoading }
        vm.onSideSelected(CardSide.BACK)
        vm.onShare(CardShareKind.SYSTEM)
        eventually { vm.state.value.message != null }
        assertEquals(0, target.count)
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(vm.state.value.message!!.asString(context).startsWith("코스 지도 공유가 꺼져 있어"))
        assertFalse(vm.state.value.canShareBack)
    }
}
