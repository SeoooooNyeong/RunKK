package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.domain.run.OwnRunUploadPort
import com.a207.runkk.app.domain.run.OwnRunUploads
import com.a207.runkk.app.domain.testing.CallLog
import com.a207.runkk.app.domain.testing.FakePendingRunRepository
import com.a207.runkk.app.domain.testing.FakeUploadScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** 결과 「확인」의 내 기록 관찰(설계 05-32 CrewRunCompletion, TC-11016·11044). */
class CrewRunCompletionTest {
    private val log = CallLog()
    private val uploads = OwnRunUploads()
    private val completion =
        CrewRunCompletion(OwnRunUploadPort(uploads, FakePendingRunRepository(log), FakeUploadScheduler(log)))

    @Test
    fun `이 실행에서 본 것이 없으면 결과 응답이 내 기록을 이었을 때만 저장으로 본다`() = runTest {
        val linked = MutableStateFlow<String?>(null)
        assertEquals(MyRecordUpload.Waiting, completion.observe("mine", linked).first())
        linked.value = "other"
        assertEquals(MyRecordUpload.Waiting, completion.observe("mine", linked).first(), "다른 기록 ID는 내 저장이 아니다")
        linked.value = "mine"
        assertEquals(MyRecordUpload.Stored(true, null), completion.observe("mine", linked).first())
    }

    @Test
    fun `서버 응답·전송 실패·거부를 확인 단계로 옮기고 다시 보내기는 전송 예약이다`() = runTest {
        val linked = MutableStateFlow<String?>(null)
        uploads.onStored("a", isValid = false)
        assertEquals(MyRecordUpload.Stored(false, null), completion.observe("a", linked).first())
        uploads.onFailed("b", AppError.Network)
        assertEquals(MyRecordUpload.Retryable(AppError.Network), completion.observe("b", linked).first())
        uploads.onFailed("c", AppError.Rejected("RUN_INVALID"))
        assertEquals(MyRecordUpload.Blocked, completion.observe("c", linked).first())
        completion.retry("b")
        assertEquals(MyRecordUpload.Waiting, completion.observe("b", linked).first())
        assertEquals("upload.requestUpload", log.calls.last())
    }
}
