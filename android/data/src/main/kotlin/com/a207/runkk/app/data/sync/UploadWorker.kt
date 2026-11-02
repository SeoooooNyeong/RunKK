package com.a207.runkk.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.domain.sync.RetryPolicy
import com.a207.runkk.app.domain.sync.UploadOutcome
import com.a207.runkk.app.domain.sync.UploadPendingRunsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.coroutines.cancellation.CancellationException

/**
 * 보관 기록을 서버로 보낸다(6.1.14 data.sync): doWork → UploadPendingRunsUseCase().
 * 다시 시도할 만한 실패(네트워크·인증·저장)가 하나라도 있으면 RetryPolicy.nextDelay(attempt) 뒤에 다시 돌도록 예약한다(D-15).
 * 서버가 거부한 기록(Rejected, 4xx)은 UseCase가 REJECTED로 사유와 함께 남기고 다시 보내지 않으므로 재전송을 예약하지 않는다(R12 D2-b).
 * 이번 실행에서 거부된 기록 수는 출력 데이터 [KEY_REJECTED]와 경고 로그(사유 코드만, 기록·회원 식별자 없음)로 드러낸다.
 *
 * 의존은 Hilt EntryPoint로 받는다. HiltWorkerFactory를 쓰려면 app의 Application이 WorkManager 설정을 바꿔야 해서(D-61)
 * 기본 WorkerFactory가 만든 이 작업이 그래프에서 직접 꺼낸다.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val outcomes =
            try {
                graph.uploadPendingRuns().invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 예상하지 못한 실패도 재전송을 예약해 업로드가 조용히 멈추지 않게 한다.
                null
            }
        val failures = outcomes?.filterIsInstance<UploadOutcome.Failed>().orEmpty()
        val rejected = failures.mapNotNull { (it.error as? AppError.Rejected)?.code }
        val retryable = outcomes == null || failures.size > rejected.size
        if (retryable) {
            val attempt = inputData.getInt(KEY_ATTEMPT, 0)
            graph.uploadScheduler().scheduleRetry(attempt + 1, graph.retryPolicy().nextDelay(attempt))
        }
        if (rejected.isNotEmpty()) {
            Log.w(TAG, "서버가 거부한 전송 ${rejected.size}건(재전송하지 않음): ${rejected.distinct()}")
        }
        return Result.success(workDataOf(KEY_REJECTED to rejected.size))
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun uploadPendingRuns(): UploadPendingRunsUseCase

        fun retryPolicy(): RetryPolicy

        fun uploadScheduler(): WorkManagerUploadScheduler
    }

    internal companion object {
        const val KEY_ATTEMPT = "attempt"

        /** 이번 실행에서 서버가 거부(4xx)한 전송 수(WorkInfo.outputData). 보관 기록은 REJECTED로 남는다. */
        const val KEY_REJECTED = "rejected"
        private const val TAG = "UploadWorker"
    }
}
