package com.a207.runkk.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.a207.runkk.app.domain.run.UploadScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 보관 기록 전송 작업을 예약한다(6.1.14 data.sync, D-15). 작업은 고유 이름 하나이고 네트워크가 연결돼야 돈다.
 * - requestUpload(측정 종료 직후 등): 기다리는 재전송이 있어도 바꿔 지금 보낸다(REPLACE). 서버가 기록ID로 중복을 막으므로(D-11)
 *   돌던 작업을 바꿔도 같은 기록이 두 번 저장되지 않는다.
 * - scheduleRetry(UploadWorker가 실패 뒤): RetryPolicy 간격(30초 × 2^n, 최대 30분)으로 뒤에 붙인다(APPEND_OR_REPLACE).
 *   WorkManager의 지수 백오프는 상한이 5시간이라 D-15의 30분 상한을 지키려고 간격을 직접 정한다.
 */
@Singleton
class WorkManagerUploadScheduler
@Inject
constructor(@ApplicationContext private val context: Context) : UploadScheduler {
    override fun requestUpload() {
        enqueue(ExistingWorkPolicy.REPLACE, Duration.ZERO, attempt = 0)
    }

    internal fun scheduleRetry(attempt: Int, delay: Duration) {
        enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE, delay, attempt)
    }

    private fun enqueue(policy: ExistingWorkPolicy, delay: Duration, attempt: Int) {
        val request =
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(delay)
                .setInputData(workDataOf(UploadWorker.KEY_ATTEMPT to attempt))
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, policy, request)
    }

    internal companion object {
        const val UNIQUE_WORK = "runkk-upload-pending-runs"
    }
}
