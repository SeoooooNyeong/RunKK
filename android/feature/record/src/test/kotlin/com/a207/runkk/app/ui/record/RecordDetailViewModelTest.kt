package com.a207.runkk.app.ui.record

import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** 기록 상세와 삭제(D-280, FR-225, TC-9736). */
class RecordDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val records = FakeRecordRepository().apply {
        runResult = Outcome.Success(detail().copy(isDeletable = true))
    }

    private fun loaded() = RecordDetailViewModel(records).apply { load("r1") }

    @Test
    fun `기록 상세를 읽고 실패하면 오류를 보인다(FR-059)`() {
        val vm = loaded()
        assertEquals("12°C 맑음", vm.state.value.detail?.weather)
        records.runResult = Outcome.Failure(AppError.Network)
        val failing = RecordDetailViewModel(records)
        failing.load("r9")
        assertNull(failing.state.value.detail)
        assertEquals(RunkkErrorText.of(AppError.Network), failing.state.value.error)
    }

    @Test
    fun `TC-9736 기록 삭제를 누르면 확인을 묻고 취소하면 요청하지 않는다`() {
        val vm = loaded()
        vm.onDeleteTapped()
        assertTrue(vm.state.value.isConfirmingDelete)
        vm.onDeleteDismissed()
        assertFalse(vm.state.value.isConfirmingDelete)
        assertEquals(emptyList(), records.deleteCalls)
    }

    @Test
    fun `TC-9736 삭제가 끝나면(204) 목록으로 돌아가라고 알린다`() = runTest {
        val vm = loaded()
        vm.events.test {
            vm.onDeleteTapped()
            vm.onDeleteConfirmed()
            assertEquals(RecordDetailEvent.DELETED, awaitItem())
        }
        assertEquals(listOf("r1"), records.deleteCalls)
        assertFalse(vm.state.value.isConfirmingDelete)
    }

    @Test
    fun `TC-9736 삭제 확인을 연달아 눌러도 요청은 한 번이다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        records.deleteGate = gate
        val vm = loaded()
        vm.onDeleteConfirmed()
        vm.onDeleteConfirmed()
        assertTrue(vm.state.value.isDeleting)
        gate.complete(Unit)
        assertEquals(listOf("r1"), records.deleteCalls)
    }

    @Test
    fun `TC-9736 이미 없는 기록(RECORD_NOT_FOUND)이면 삭제된 것으로 보고 목록으로 돌아간다`() = runTest {
        records.deleteResult = Outcome.Failure(AppError.Rejected("RECORD_NOT_FOUND"))
        val vm = loaded()
        vm.events.test {
            vm.onDeleteConfirmed()
            assertEquals(RecordDetailEvent.DELETED, awaitItem())
        }
    }

    @Test
    fun `TC-9736 유효 기록(RECORD_VALID_NOT_DELETABLE)이면 문구를 보이고 버튼을 숨긴다`() {
        records.deleteResult = Outcome.Failure(AppError.Rejected("RECORD_VALID_NOT_DELETABLE"))
        val vm = loaded()
        vm.onDeleteConfirmed()
        assertEquals(UiText.Res(R.string.record_delete_not_allowed), vm.state.value.message)
        assertFalse(vm.state.value.detail!!.isDeletable)
        assertFalse(vm.state.value.isDeleting)
        vm.onMessageShown()
        assertNull(vm.state.value.message)
    }

    @Test
    fun `TC-9736 남의 기록(403 PERMISSION_DENIED)은 일반 오류 문구이고 버튼은 그대로다`() {
        records.deleteResult = Outcome.Failure(AppError.Rejected("PERMISSION_DENIED"))
        val vm = loaded()
        vm.onDeleteConfirmed()
        assertEquals(RunkkErrorText.of(AppError.Rejected("PERMISSION_DENIED")), vm.state.value.message)
        assertTrue(vm.state.value.detail!!.isDeletable)
        assertFalse(vm.state.value.isDeleting)
    }

    @Test
    fun `TC-9735 삭제할 수 없는 기록은 확인을 묻지 않는다`() {
        records.runResult = Outcome.Success(detail().copy(isDeletable = false))
        val vm = loaded()
        vm.onDeleteTapped()
        vm.onDeleteConfirmed()
        assertFalse(vm.state.value.isConfirmingDelete)
        assertEquals(emptyList(), records.deleteCalls)
    }
}
