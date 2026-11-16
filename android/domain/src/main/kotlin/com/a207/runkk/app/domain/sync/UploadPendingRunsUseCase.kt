package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.LateHeartRates
import com.a207.runkk.app.domain.run.OwnRunUploads
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.UploadState
import javax.inject.Inject

/**
 * 보관 기록을 서버로 보낸다(6.1.14 domain.sync). data.sync.UploadWorker가 부르고,
 * 실패가 있으면 RetryPolicy 간격 뒤 다시 실행된다(D-15).
 * 성공·"이미 저장됨"이면 보관함에서 지운다. "이미 저장됨"인 기록에 심박이 있으면 늦게 붙은 심박을 따로 보낸다(FR-043).
 * 보내기 직전에 정지 구간을 기록 범위 안으로 자른다(CompletedRun.withPausesInRange, R12 D2).
 * 서버가 4xx로 거부하면(AppError.Rejected) 지우지 않고 사유와 함께 REJECTED로 남기고, 그 뒤로는 자동으로 다시 보내지 않는다
 * (03-00 앱의 응답 해석, R12 D2-b). 네트워크·인증·5xx 실패는 미전송으로 남아 재전송된다.
 * 머리 정보 없는 기록의 늦은 심박 항목(LateHeartRates)도 이 UseCase 하나만 보낸다(D-77, D-80).
 */
class UploadPendingRunsUseCase
@Inject
constructor(
    private val pendingRuns: PendingRunRepository,
    private val remote: RunUploadRemote,
    /** 기록별 서버 응답을 알릴 곳(설계 05-32 OwnRunUploadPort: 크루런 결과 「확인」이 내 기록만 기다린다). */
    private val ownUploads: OwnRunUploads
) {
    /** 응답을 따로 모으지 않는 조립(시험). */
    constructor(pendingRuns: PendingRunRepository, remote: RunUploadRemote) :
        this(pendingRuns, remote, OwnRunUploads())

    /** 보관 기록 결과(Sent·AlreadySaved·Failed) 뒤에 늦은 심박 항목 결과를 붙인다. 보관함을 읽지 못하면 실패 하나로 끝낸다(D-80). */
    suspend operator fun invoke(): List<UploadOutcome> {
        val runs =
            when (val pending = pendingRuns.pending()) {
                is Outcome.Failure -> return listOf(UploadOutcome.Failed(null, pending.error))
                is Outcome.Success -> pending.value
            }
        return runs.filter { it.uploadState != UploadState.REJECTED }.map { upload(it) } + uploadLateHeartRates()
    }

    private suspend fun upload(run: CompletedRun): UploadOutcome {
        val receipt =
            when (val uploaded = remote.upload(run.withPausesInRange())) {
                is Outcome.Failure -> return failed(run, uploaded.error)
                is Outcome.Success -> uploaded.value
            }
        ownUploads.onStored(run.recordId, receipt.isValid)
        // receipt.isAlreadySaved는 여기서 한 번만 본다(D-80). 늦게 붙은 심박을 보내지 못하면 보관함에 남겨 다음 재전송 때 다시 보낸다.
        val done: UploadOutcome =
            if (receipt.isAlreadySaved) UploadOutcome.AlreadySaved(run.recordId) else UploadOutcome.Sent(run.recordId)
        val appended =
            if (done is UploadOutcome.AlreadySaved && run.heartRates.isNotEmpty()) {
                remote.appendHeartRates(run.recordId, run.heartRates)
            } else {
                Outcome.Success(Unit)
            }
        val removed = if (appended is Outcome.Failure) appended else pendingRuns.remove(run.recordId)
        return if (removed is Outcome.Failure) UploadOutcome.Failed(run.recordId, removed.error) else done
    }

    /** 거부(4xx)면 사유와 함께 남겨 자동 재전송을 멈춘다. 남기지 못하면 저장 실패로 돌려 다음 실행에 다시 본다. */
    private suspend fun failed(run: CompletedRun, error: AppError): UploadOutcome.Failed {
        ownUploads.onFailed(run.recordId, error)
        if (error is AppError.Rejected) {
            val marked = pendingRuns.markRejected(run, error.code)
            if (marked is Outcome.Failure) return UploadOutcome.Failed(run.recordId, marked.error)
        }
        return UploadOutcome.Failed(run.recordId, error)
    }

    /**
     * 머리 정보 없는 기록의 늦은 심박 항목을 보내고 보낸 항목을 지운다(D-80). 성공이거나 서버에 그 기록이 없으면 지우고 결과를
     * 더하지 않는다(지우기 실패만 더한다). 그 밖의 실패는 항목을 남겨 다음 실행에 다시 보내고 실패를 더한다(NFR-032).
     */
    private suspend fun uploadLateHeartRates(): List<UploadOutcome> {
        val entries =
            when (val late = pendingRuns.pendingLateHeartRates()) {
                is Outcome.Failure -> return listOf(UploadOutcome.Failed(null, late.error))
                is Outcome.Success -> late.value
            }
        return entries.mapNotNull { send(it) }
    }

    /** 항목 하나. 미전송으로 남는 것이 없으면 null. */
    private suspend fun send(entry: LateHeartRates): UploadOutcome? {
        val sent = remote.appendHeartRates(entry.recordId, entry.samples)
        val result = if (sent.isDone()) pendingRuns.removeLateHeartRates(entry) else sent
        return (result as? Outcome.Failure)?.let { UploadOutcome.Failed(entry.recordId, it.error) }
    }

    /** 더 보낼 필요가 없음: 서버가 받았거나 그 기록이 서버에 없다. */
    private fun Outcome<Unit>.isDone(): Boolean =
        this is Outcome.Success || (this as? Outcome.Failure)?.error == AppError.Rejected(RECORD_NOT_FOUND)

    companion object {
        /** 서버에 그 기록이 없음(서버 ReasonCode.RECORD_NOT_FOUND). 늦은 심박 항목을 더 보낼 곳이 없어 지운다(D-80). */
        const val RECORD_NOT_FOUND = "RECORD_NOT_FOUND"
    }
}
