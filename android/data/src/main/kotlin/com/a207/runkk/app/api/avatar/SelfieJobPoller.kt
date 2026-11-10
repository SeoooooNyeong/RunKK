package com.a207.runkk.app.api.avatar

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.PendingSelfiePhoto
import com.a207.runkk.app.domain.avatar.SelfieFailure
import com.a207.runkk.app.domain.avatar.SelfieJob
import com.a207.runkk.app.domain.avatar.SelfieProgress
import com.a207.runkk.app.domain.avatar.SelfieResultNotifier
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.delay

/**
 * 셀카 작업 조회(design-oo 05-30 6.1, [임시:D-성-117]~[임시:D-성-120]). [SelfieJobWorker] 한 번의 실행이 부른다. 화면 ViewModel에
 * 있던 폴링을 옮겼다.
 *
 * - 간격 [POLL_DELAYS](3·3·5·8·10·15초, 마지막 값을 되풀이)로 조회한다. 한 번의 실행은 [RUN_LIMIT_MILLIS](8분)까지이고, 넘으면
 *   [SelfiePollEnd.CONTINUE]로 끝나 워커가 같은 작업을 다시 건다.
 * - 총 상한은 접수 시각부터 [TOTAL_LIMIT_MILLIS](974초 약 16분, 앞 판 68번 조회와 같은 시간)다. 워커가 멈췄다 다시 시작해도 접수
 *   시각(기기 사본)에서 센다. 넘으면 실패(사유 [SelfieProgress.TIMEOUT])다.
 * - 성공(SUCCEEDED)이면 결과를 받아 내 얼굴을 바꾸고(finishSelfie, 하루 1회 차감은 서버가 한다) 보낸 사진을 지운 뒤 알림을 낸다.
 * - 실패(FAILED)·업무 거부(Rejected)는 바로 실패, 연결·서버 실패는 [MAX_FAILURES]번 이어지면 실패다. 실패는 하루 횟수에 세지 않는다
 *   (FR-155, 서버 규칙). 실패해도 사진을 지우고 알림을 낸다.
 * - 기기에 남긴 작업이 [jobId]가 아니면(새로 접수, 기본 캐릭터 고름, 로그아웃) 아무것도 하지 않고 끝낸다.
 */
internal class SelfieJobPoller(
    private val avatars: AvatarRepository,
    private val store: PendingSelfieStore,
    private val progress: SelfieProgressHub,
    private val photo: PendingSelfiePhoto,
    private val notifier: SelfieResultNotifier,
    private val clock: Clock,
    private val delays: List<Long>,
    private val sleep: suspend (Long) -> Unit
) {
    @Inject
    constructor(
        avatars: AvatarRepository,
        store: PendingSelfieStore,
        progress: SelfieProgressHub,
        photo: PendingSelfiePhoto,
        notifier: SelfieResultNotifier,
        clock: Clock
    ) : this(avatars, store, progress, photo, notifier, clock, POLL_DELAYS, { delay(it) })

    suspend fun poll(jobId: String): SelfiePollEnd {
        if (store.load() != jobId) return SelfiePollEnd.FINISHED
        val submittedAt = store.submittedAt() ?: clock.millis().also { store.save(jobId, it) }
        val deadline = submittedAt + TOTAL_LIMIT_MILLIS
        val runStart = clock.millis()
        var failures = 0
        var attempt = 0
        while (true) {
            val now = clock.millis()
            if (now >= deadline) return fail(jobId, SelfieProgress.TIMEOUT)
            if (now - runStart >= RUN_LIMIT_MILLIS) return SelfiePollEnd.CONTINUE
            sleep(delays[attempt.coerceAtMost(delays.lastIndex)])
            attempt++
            if (store.load() != jobId) return SelfiePollEnd.FINISHED
            val result = check(jobId)
            when {
                result == Check.DONE -> return SelfiePollEnd.FINISHED
                result == Check.WAIT -> failures = 0
                ++failures >= MAX_FAILURES -> return fail(jobId, null)
            }
        }
    }

    /** 한 번 조회한다. 끝났으면 결과를 처리하고 [Check.DONE]이다. */
    private suspend fun check(jobId: String): Check = when (val job = avatars.selfieJob(jobId)) {
        is Outcome.Success -> when (job.value.status) {
            SelfieJob.Status.SUCCEEDED -> finish(jobId)

            SelfieJob.Status.FAILED -> {
                // 저장소가 실패를 남기고 진행을 맞췄다.
                done(jobId, succeeded = false)
                Check.DONE
            }

            SelfieJob.Status.QUEUED, SelfieJob.Status.RUNNING -> Check.WAIT
        }

        is Outcome.Failure -> rejectedOrRetry(jobId, job.error)
    }

    private suspend fun finish(jobId: String): Check = when (val result = avatars.finishSelfie(jobId)) {
        is Outcome.Success -> {
            done(jobId, succeeded = true)
            Check.DONE
        }

        is Outcome.Failure -> rejectedOrRetry(jobId, result.error)
    }

    private suspend fun rejectedOrRetry(jobId: String, error: AppError): Check {
        if (error !is AppError.Rejected) return Check.RETRY
        fail(jobId, error.code)
        return Check.DONE
    }

    private suspend fun fail(jobId: String, code: String?): SelfiePollEnd {
        if (store.load() == jobId) store.clear()
        store.saveFailure(SelfieFailure(jobId, code))
        progress.sync()
        done(jobId, succeeded = false)
        return SelfiePollEnd.FINISHED
    }

    private suspend fun done(jobId: String, succeeded: Boolean) {
        photo.clear()
        notifier.notify(jobId, succeeded)
    }

    private enum class Check { DONE, WAIT, RETRY }

    internal companion object {
        /** 얼굴 생성은 보통 1~6분 걸린다. 3초부터 늘려 15초마다 본다. */
        val POLL_DELAYS = listOf(3_000L, 3_000L, 5_000L, 8_000L, 10_000L, 15_000L)

        /** 접수부터 이만큼 지나도 끝나지 않으면 실패다(앞 판 68번 조회 = 974초, 서버 생성 제한 15분 뒤). */
        const val TOTAL_LIMIT_MILLIS = 974_000L

        /** 워커 한 번의 실행 시간(CoroutineWorker 실행 제한 10분 안). */
        const val RUN_LIMIT_MILLIS = 8 * 60_000L

        /** 연속 연결·서버 실패 허용 횟수. */
        const val MAX_FAILURES = 3
    }
}
