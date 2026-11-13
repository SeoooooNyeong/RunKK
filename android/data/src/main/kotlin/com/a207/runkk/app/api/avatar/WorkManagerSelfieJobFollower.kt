package com.a207.runkk.app.api.avatar

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.a207.runkk.app.domain.avatar.SelfieJobFollower
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * 셀카 작업 따라가기를 WorkManager 고유 작업 [UNIQUE_WORK] 하나로 건다(design-oo 05-30 [임시:D-성-117]). 네트워크가 연결돼야 돈다.
 * - [follow](새 접수): 앞 작업을 바꾼다(REPLACE).
 * - [resume](앱 다시 켬·화면 다시 들어옴): 이미 걸려 있으면 그대로 둔다(KEEP).
 * - [checkNow](홈·얼굴 만들기가 보일 때): 워커와 별도로 지금 한 번 조회한다. 인터넷 확인이 안 되는 Wi-Fi처럼 워커 제약이 채워지지 않아도
 *   앞 화면에서는 끝난 결과를 받는다([임시:D-성-188]).
 * - [continueLater](워커가 실행 시간을 다 썼을 때): 돌고 있는 자기 뒤에 붙인다(APPEND_OR_REPLACE). 돌고 있는 자기 자신을 REPLACE로
 *   걸면 자기가 취소된다.
 */
@Singleton
internal class WorkManagerSelfieJobFollower
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val poller: Provider<SelfieJobPoller>
) : SelfieJobFollower {
    override fun follow(jobId: String) = enqueue(jobId, ExistingWorkPolicy.REPLACE)

    override fun resume(jobId: String) = enqueue(jobId, ExistingWorkPolicy.KEEP)

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }

    /** 앞 화면 직접 확인([임시:D-성-188]): 워커 제약(네트워크)과 상관없이 기기 작업을 한 번 조회한다. */
    override suspend fun checkNow() = poller.get().checkNow()

    fun continueLater(jobId: String) = enqueue(jobId, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun enqueue(jobId: String, policy: ExistingWorkPolicy) {
        val request =
            OneTimeWorkRequestBuilder<SelfieJobWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .setInputData(workDataOf(SelfieJobWorker.KEY_JOB to jobId))
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, policy, request)
    }

    internal companion object {
        const val UNIQUE_WORK = "selfie-job"

        /** 예외로 끝난 실행을 다시 걸기까지 첫 대기(지수 백오프, [임시:D-성-159]). */
        const val BACKOFF_SECONDS = 30L
    }
}
