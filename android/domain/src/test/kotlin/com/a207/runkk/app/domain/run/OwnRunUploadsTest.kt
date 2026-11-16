package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.sync.RunUploadRemote
import com.a207.runkk.app.domain.sync.UploadPendingRunsUseCase
import com.a207.runkk.app.domain.sync.UploadReceipt
import com.a207.runkk.app.domain.testing.CallLog
import com.a207.runkk.app.domain.testing.FakePendingRunRepository
import com.a207.runkk.app.domain.testing.FakeUploadScheduler
import com.a207.runkk.app.domain.testing.T0
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** 내 기록 저장·업로드 상태 포트(설계 05-32 OwnRunUploadPort, [임시:D-성-890], TC-11016·11044). */
@OptIn(ExperimentalCoroutinesApi::class)
class OwnRunUploadsTest {
    private val log = CallLog()
    private val pending = FakePendingRunRepository(log)
    private val scheduler = FakeUploadScheduler(log)
    private val uploads = OwnRunUploads()
    private val port = OwnRunUploadPort(uploads, pending, scheduler)

    private fun run(id: String = "mine") = CompletedRun(
        recordId = id,
        mode = RunMode.CREW_RUN,
        cardKind = CardKind.CREW_RUN,
        battleId = null,
        crewRunPostId = 11,
        startedAt = T0,
        elapsed = Duration.ofMinutes(5),
        distance = Distance(800),
        route = emptyList(),
        unstable = emptyList(),
        heartRates = emptyList(),
        savedAt = T0
    )

    /** 받은 기록마다 [receipts]의 응답을 돌려주는 서버. 없으면 네트워크 실패다. */
    private class Remote(val receipts: Map<String, Outcome<UploadReceipt>>) : RunUploadRemote {
        override suspend fun upload(run: CompletedRun): Outcome<UploadReceipt> =
            receipts[run.recordId] ?: Outcome.Failure(AppError.Network)

        override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>) = Outcome.Success(Unit)
    }

    @Test
    fun `저장 중, 보관함 대기, 서버 저장(측정 거리 포함) 순서로 보인다`() = runTest {
        val archive = RunArchive(pending, scheduler, backgroundScope, uploads)
        archive.saveInBackground(run()) {}
        assertEquals(OwnUploadState.Saving, uploads.states.value["mine"])
        runCurrent()
        assertEquals(OwnUploadState.Pending, port.observe("mine").first())
        val remote = Remote(mapOf("mine" to Outcome.Success(UploadReceipt("mine", false, isValid = true))))
        UploadPendingRunsUseCase(pending, remote, uploads)()
        assertEquals(OwnUploadState.Stored(isValid = true, distance = Distance(800)), port.observe("mine").first())
    }

    @Test
    fun `TC-11044 valid=false는 저장 완료지만 유효 기여가 아니고, 다른 기록의 실패는 내 상태를 바꾸지 않는다`() = runTest {
        pending.save(run())
        pending.save(run("other"))
        val remote = Remote(mapOf("mine" to Outcome.Success(UploadReceipt("mine", true, isValid = false))))
        UploadPendingRunsUseCase(pending, remote, uploads)()
        assertEquals(OwnUploadState.Stored(isValid = false, distance = null), port.observe("mine").first())
        assertEquals(OwnUploadState.Failed(AppError.Network), port.observe("other").first())
    }

    @Test
    fun `TC-11044 내 전송 실패는 다시 보낼 수 있는 실패, 서버 거부는 멈춘 상태다`() = runTest {
        pending.save(run())
        UploadPendingRunsUseCase(pending, Remote(emptyMap()), uploads)()
        assertEquals(OwnUploadState.Failed(AppError.Network), port.observe("mine").first())
        port.retry("mine")
        assertEquals(OwnUploadState.Pending, port.observe("mine").first())
        assertEquals("upload.requestUpload", log.calls.last())
        val rejected = Remote(mapOf("mine" to Outcome.Failure(AppError.Rejected("RUN_INVALID"))))
        UploadPendingRunsUseCase(pending, rejected, uploads)()
        assertEquals(OwnUploadState.Rejected("RUN_INVALID"), port.observe("mine").first())
    }

    @Test
    fun `보관함에서 사라졌다는 것만으로 성공으로 보지 않는다`() = runTest {
        assertEquals(OwnUploadState.Unknown, port.observe("gone").first())
        pending.save(run("boxed"))
        assertEquals(OwnUploadState.Pending, port.observe("boxed").first(), "앱을 다시 켜도 보관함에 있으면 대기다")
    }

    @Test
    fun `보관함에 넣지 못하면 저장 실패다`() = runTest {
        pending.saveFailuresLeft = 3
        RunArchive(pending, scheduler, backgroundScope, uploads).saveInBackground(run()) {}
        testScheduler.advanceTimeBy(10_000)
        runCurrent()
        assertEquals(OwnUploadState.SaveFailed, port.observe("mine").first())
    }
}
