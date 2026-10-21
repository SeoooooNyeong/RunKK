package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 미전송 기록 보관 포트(6.1.14). 구현 data.store.RoomPendingRunRepository.
 * 미전송 기록의 읽기·쓰기는 모두 이 포트를 거친다(ui.home·ui.record도 observePending, 13.2).
 * 저장 오류는 구현이 AppError.Storage로 바꿔 Outcome으로 돌려준다(5.1.2 설계 규칙).
 */
interface PendingRunRepository {
    suspend fun save(run: CompletedRun): Outcome<Unit>

    suspend fun pending(): Outcome<List<CompletedRun>>

    fun observePending(): Flow<List<CompletedRun>>

    suspend fun remove(recordId: String): Outcome<Unit>

    suspend fun purgeRoutes(): Outcome<Unit>

    suspend fun purgeHeartRates(): Outcome<Unit>

    /** 20건 또는 7일을 넘기면 재전송 안내(FR-039). */
    suspend fun needsReminder(now: Instant): Outcome<Boolean>

    /** 늦게 온 심박 덧붙이기. RunTracker만 부른다(design-review.md M32). */
    suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit>

    /** 탈퇴 때 전부 지운다. */
    suspend fun clear(): Outcome<Unit>
}

/** 전송 작업 예약 포트. 구현 data.sync.WorkManagerUploadScheduler. */
interface UploadScheduler {
    fun requestUpload()
}

/** 워치 통신 포트. 구현 data.watch.WatchLink(D-20). */
interface WatchGateway {
    fun publish(d: WatchDisplay)

    val startRequests: Flow<Unit>

    val heartRates: Flow<List<HeartRateSample>>

    /** 워치의 종료 요청(길게 누름). RunTracker가 화면의 길게 누르기 종료와 같은 규칙으로 처리한다. 워치가 없으면 비어 있다. */
    val endRequests: Flow<Unit> get() = emptyFlow()

    fun isConnected(): Boolean
}

/** 안내 출력 포트(TTS·진동·화면 배너). 구현 data.device.AndroidGuidanceOutput. */
interface GuidanceOutput {
    fun play(g: Guidance)
}

/** OS 위치 권한 포트. 구현 data.device.AndroidLocationPermission. */
interface LocationPermission {
    fun granted(): Boolean
}

/** 포그라운드 측정 서비스 시작·정지 포트. 구현 data.device.MeasurementService. */
interface MeasurementHost {
    /** OS가 포그라운드 시작을 거부하면(Android 12+ 백그라운드 시작 제한 등) Failure. RunTracker가 시작을 되돌린다. */
    fun startForeground(): Outcome<Unit>

    fun stop()
}
