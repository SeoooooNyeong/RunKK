package com.a207.runkk.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.domain.sync.RetryPolicy
import com.a207.runkk.app.domain.sync.UploadOutcome
import com.a207.runkk.app.domain.sync.UploadPendingRunsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 보관 기록을 서버로 보낸다(6.1.14 data.sync): doWork → UploadPendingRunsUseCase().
 * 다시 시도할 만한 실패(네트워크·인증·저장)가 하나라도 있으면 RetryPolicy.nextDelay(attempt) 뒤에 다시 돌도록 예약한다(D-15).
 * 서버가 거부한 기록(Rejected)만 남았으면 재전송을 예약하지 않는다(다음 requestUpload 때 다시 보낸다).
 *
 * 의존은 Hilt EntryPoint로 받는다. HiltWorkerFactory를 쓰려면 app의 Application이 WorkManager 설정을 바꿔야 해서(D-61)
 * 기본 WorkerFactory가 만든 이 작업이 그래프에서 직접 꺼낸다.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val outcomes = graph.uploadPendingRuns().invoke()
        val retryable = outcomes.any { it is UploadOutcome.Failed && it.error !is AppError.Rejected }
        if (retryable) {
            val attempt = inputData.getInt(KEY_ATTEMPT, 0)
            graph.uploadScheduler().scheduleRetry(attempt + 1, graph.retryPolicy().nextDelay(attempt))
        }
        return Result.success()
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
    }
}
