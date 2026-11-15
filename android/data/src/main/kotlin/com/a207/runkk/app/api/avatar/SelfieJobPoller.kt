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
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 셀카 작업 조회(design-oo 05-30 6.1, D-749~D-752). [SelfieJobWorker] 한 번의 실행이 부른다. 화면 ViewModel에
 * 있던 폴링을 옮겼다.
 *
 * - 간격 [POLL_DELAYS](3·3·5·8·10·15초, 마지막 값을 되풀이)로 조회한다. 한 번의 실행은 [RUN_LIMIT_MILLIS](8분)까지이고, 넘으면
 *   [SelfiePollEnd.CONTINUE]로 끝나 워커가 같은 작업을 다시 건다.
 * - 총 상한은 접수 시각부터 [TOTAL_LIMIT_MILLIS](974초 약 16분, 앞 판 68번 조회와 같은 시간)다. 워커가 멈췄다 다시 시작해도 접수
 *   시각(기기 사본)에서 센다. 넘으면 실패(사유 [SelfieProgress.TIMEOUT])다.
 * - 성공(SUCCEEDED)이면 결과를 받아 내 얼굴을 바꾸고(finishSelfie, 하루 1회 차감은 서버가 한다) 보낸 사진을 지운 뒤 알림을 낸다.
 * - 실패(FAILED)·업무 거부(Rejected)는 바로 실패, 서버 실패(5xx 등 Server)는 [MAX_FAILURES]번 이어지면 실패다. 연결 실패(오프라인)는
 *   세지 않고 총 상한까지 이어 간다(리뷰 R2-10, D-791). 실패는 하루 횟수에 세지 않는다(FR-155, 서버 규칙). 실패해도 사진을
 *   지우고 알림을 낸다. 그사이 다른 작업이 기기에 남았으면 뒷정리·알림을 하지 않는다.
 * - 기기에 남긴 작업이 [jobId]가 아니면(새로 접수, 기본 캐릭터 고름, 로그아웃) 아무것도 하지 않고 끝낸다.
 * - 앞 화면 직접 확인([checkNow], D-820): 워커가 네트워크 제약(인터넷 확인된 연결)을 못 채워 돌지 않아도, 홈·얼굴 만들기가
 *   보일 때 기기 작업을 한 번 조회해 같은 규칙으로 끝낸다. 워커와 겹치지 않게 조회 한 번([checkOnce])은 프로세스에 하나만 돌고(이 객체는
 *   싱글턴), 기다리는 사이 다른 쪽이 끝냈으면(기기 작업이 바뀜) 묻지 않는다.
 */
@Singleton
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
        notifier: Optional<SelfieResultNotifier>,
        clock: Clock
    ) : this(avatars, store, progress, photo, notifier.orElse(SILENT), clock, POLL_DELAYS, { delay(it) })

    private val lock = Mutex()

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
            when (checkOnce(jobId)) {
                Check.DONE -> return SelfiePollEnd.FINISHED

                Check.WAIT -> failures = 0

                // 연결 실패(오프라인)는 세지 않고 총 상한까지 기다린다(리뷰 R2-10).
                Check.OFFLINE -> Unit

                Check.SERVER -> if (++failures >= MAX_FAILURES) return fail(jobId, null)
            }
        }
    }

    /**
     * 앞 화면이 기기 작업을 한 번 직접 확인한다(D-820). 끝났으면(성공·실패·거부) 워커와 같이 결과를 처리하고, 만드는 중·연결
     * 실패·서버 실패는 그대로 둔다(상한·연속 실패는 워커가 센다). 기기에 남긴 작업이 없으면 묻지 않는다.
     */
    suspend fun checkNow() {
        val jobId = store.load() ?: return
        checkOnce(jobId)
    }

    /** 워커와 앞 화면 확인이 겹치지 않게 조회 한 번을 하나씩 한다. 기다리는 사이 기기 작업이 바뀌었으면(끝냄, 새 접수) 묻지 않는다. */
    private suspend fun checkOnce(jobId: String): Check = lock.withLock {
        if (store.load() != jobId) Check.DONE else check(jobId)
    }

    /** 한 번 조회한다. 끝났으면 결과를 처리하고 [Check.DONE]이다. */
    private suspend fun check(jobId: String): Check = when (val job = avatars.selfieJob(jobId)) {
        is Outcome.Success -> when (job.value.status) {
            // 조회하는 사이 기기 작업이 바뀌었으면(새 접수, 기본 캐릭터) 결과를 받지 않는다(리뷰 R2-9).
            SelfieJob.Status.SUCCEEDED -> if (store.load() == jobId) finish(jobId) else Check.DONE

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

    private suspend fun rejectedOrRetry(jobId: String, error: AppError): Check = when (error) {
        is AppError.Rejected -> {
            fail(jobId, error.code)
            Check.DONE
        }

        is AppError.Server -> Check.SERVER

        AppError.Network, AppError.Unauthorized, AppError.Storage -> Check.OFFLINE
    }

    private suspend fun fail(jobId: String, code: String?): SelfiePollEnd {
        val current = store.load()
        if (current != null && current != jobId) return SelfiePollEnd.FINISHED
        // 그사이 아바타 조회가 이 작업의 머리를 보고 성공으로 정리했으면 실패로 덮지 않는다(D-820).
        val shown = progress.progress.value
        if (shown is SelfieProgress.Succeeded && shown.jobId == jobId) return SelfiePollEnd.FINISHED
        store.clear()
        store.saveFailure(SelfieFailure(jobId, code))
        progress.sync()
        done(jobId, succeeded = false)
        return SelfiePollEnd.FINISHED
    }

    /** 끝난 작업의 뒷정리. 그사이 다른 작업이 기기에 남았으면(새 접수) 그 사진을 지우지 않고 알리지 않는다(리뷰 R2-9). */
    private suspend fun done(jobId: String, succeeded: Boolean) {
        val current = store.load()
        if (current != null && current != jobId) return
        photo.clear()
        notifier.notify(jobId, succeeded)
    }

    private enum class Check { DONE, WAIT, OFFLINE, SERVER }

    internal companion object {
        /** 알림 구현이 없는 앱(워치)에서 쓰는 빈 알림. */
        private val SILENT = object : SelfieResultNotifier {
            override fun notify(jobId: String, succeeded: Boolean) = Unit
        }

        /** 얼굴 생성은 보통 1~6분 걸린다. 3초부터 늘려 15초마다 본다. */
        val POLL_DELAYS = listOf(3_000L, 3_000L, 5_000L, 8_000L, 10_000L, 15_000L)

        /** 접수부터 이만큼 지나도 끝나지 않으면 실패다(앞 판 68번 조회 = 974초, 서버 생성 제한 15분 뒤). */
        const val TOTAL_LIMIT_MILLIS = 974_000L

        /** 워커 한 번의 실행 시간(CoroutineWorker 실행 제한 10분 안). */
        const val RUN_LIMIT_MILLIS = 8 * 60_000L

        /** 연속 서버 실패(Server) 허용 횟수. 연결 실패는 세지 않는다. */
        const val MAX_FAILURES = 3
    }
}
