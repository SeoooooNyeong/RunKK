package com.a207.runkk.app.api.avatar

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.coroutines.cancellation.CancellationException

/**
 * 셀카 작업을 화면 밖에서 끝까지 따라간다(design-oo 05-30 6.1, [임시:D-성-117]). 한 번의 실행은 [SelfieJobPoller.poll]이고, 실행 시간을
 * 다 쓰면 같은 작업을 뒤에 다시 건다. 시스템이 멈추면(제약 상실, 시간 제한) WorkManager가 다시 걸고, 총 상한은 접수 시각 기준이다.
 *
 * 의존은 Hilt EntryPoint로 받는다(UploadWorker 선례, D-61: HiltWorkerFactory를 쓰려면 Application의 WorkManager 설정을 바꿔야 한다).
 */
internal class SelfieJobWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB) ?: return Result.success()
        val graph = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val end =
            try {
                graph.selfiePoller().poll(jobId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 예상하지 못한 실패도 조용히 멈추지 않게 다시 건다. 총 상한(접수 시각 기준)이 끝을 보장한다.
                Log.w(TAG, "셀카 작업 조회 실패, 다시 건다: ${e.javaClass.simpleName}")
                SelfiePollEnd.CONTINUE
            }
        if (end == SelfiePollEnd.CONTINUE) graph.selfieFollower().continueLater(jobId)
        return Result.success()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface Dependencies {
        fun selfiePoller(): SelfieJobPoller

        fun selfieFollower(): WorkManagerSelfieJobFollower
    }

    internal companion object {
        const val KEY_JOB = "jobId"
        private const val TAG = "SelfieJobWorker"
    }
}
