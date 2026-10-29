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
import com.a207.runkk.app.domain.record.RecordList
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

/** 기록 카드 팝업·공유 ViewModel(FR-068·FR-069). 목록·상세는 RecordListViewModelTest·RecordDetailViewModelTest가 맡는다. */
@RunWith(RobolectricTestRunner::class)
class RecordViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val records = FakeRecordRepository()
    private val pending = FakePendingRunRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-20T01:00:00Z"), ZoneId.of("Asia/Seoul"))

    private val avatars = FakeAvatarRepository()

    private fun cardViewModel(shared: Boolean, target: CountingTarget = CountingTarget()) = RecordCardViewModel(
        records,
        avatars,
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

    @Test
    fun `카드 앞면 배경 키는 아바타 배경에서 읽고, 읽지 못해도 기본 배경으로 그린다(FR-202, D-140)`() {
        avatars.backgroundResult = Outcome.Failure(AppError.Network)
        val vm = cardViewModel(shared = true)
        vm.load("r1")
        eventually { !vm.state.value.isLoading }
        assertEquals(1, avatars.backgroundCalls)
        assertNotNull(vm.state.value.front)
        assertNull(vm.state.value.error)
    }
}
