package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant

/**
 * 확정된 러닝 한 회(기기 보관 형태, D-54). Room 엔티티(data.store)와 서버 DTO(api.run)는 각 데이터 모듈의 매퍼가 만든다.
 * RunSession이 domain.run 형식만으로 만든다(design-review.md M32).
 */
data class CompletedRun(
    val recordId: String,
    val mode: RunMode,
    val cardKind: CardKind,
    val battleId: Long?,
    val crewRunPostId: Long?,
    val startedAt: Instant,
    val elapsed: Duration,
    val distance: Distance,
    val route: List<RoutePoint>,
    val unstable: List<UnstableSegment>,
    val heartRates: List<HeartRateSample>,
    /** 정지 구간(자유런만, FR-194). [elapsed]는 이 구간을 뺀 운동 시간이다. */
    val pauses: List<PauseInterval> = emptyList(),
    /** 기록을 시작한 기기(RunStartSpec.startDevice, D-378, W23). 서버 기록에 함께 저장된다. */
    val startDevice: RunStartSpec.StartDevice = RunStartSpec.StartDevice.PHONE,
    val uploadState: UploadState = UploadState.NOT_SENT,
    val savedAt: Instant,
    /** 서버가 받지 않은 사유 코드(uploadState = REJECTED일 때만, 03-00 "4xx는 거부 사유와 함께 보존", R12 D2-b). */
    val rejectedReason: String? = null,
    /** 기기가 잰 속도·케이던스 표본(시간순, S15P31A207-242). 재지 못했으면 빈 목록이고 서버가 속도를 위치로 셈한다. */
    val motions: List<MotionSample> = emptyList()
) {
    /** 정지 시간 합. */
    val pausedTotal: Duration
        get() = pauses.fold(Duration.ZERO) { sum, p -> sum.plus(Duration.between(p.from, p.to)) }

    /** 기록 끝 = 시작 + 운동 시간 + 정지 합. 서버 endedAt이고 정지 구간은 이 안에 있어야 한다(F.2.2). */
    val endedAt: Instant
        get() = startedAt.plus(elapsed).plus(pausedTotal)

    /**
     * 전송 직전 방어(R12 D2): 정지 구간을 [startedAt, endedAt] 안으로 자르고, 길이 0인 구간은 빼고, 겹치거나 맞닿은 구간은 합친다.
     * 기록 끝은 그대로 두고 잘린 만큼 운동 시간을 늘려 "끝 = 시작 + 운동 시간 + 정지 합"을 지킨다. 고칠 것이 없으면 자기 자신이다.
     * 정지 중 종료 때 정지 끝과 기록 끝을 다른 반올림으로 정하던 옛 판이 보관함에 남긴 기록(끝이 1~2ms 넘침)도 이것으로 올라간다.
     */
    fun withPausesInRange(): CompletedRun {
        if (pauses.isEmpty()) return this
        val end = endedAt
        val fitted = mutableListOf<PauseInterval>()
        for (p in pauses.sortedBy { it.from }) {
            val from = maxOf(p.from, startedAt)
            val to = minOf(p.to, end)
            if (!from.isBefore(to)) continue
            val last = fitted.lastOrNull()
            if (last != null && !last.to.isBefore(from)) {
                fitted[fitted.lastIndex] = PauseInterval(last.from, maxOf(last.to, to))
            } else {
                fitted += PauseInterval(from, to)
            }
        }
        if (fitted == pauses) return this
        val fittedTotal = fitted.fold(Duration.ZERO) { sum, p -> sum.plus(Duration.between(p.from, p.to)) }
        val workout = Duration.between(startedAt, end).minus(fittedTotal)
        return copy(pauses = fitted, elapsed = if (workout.isNegative) Duration.ZERO else workout)
    }

    /** 위치 동의 철회 파기 지시(FR-026): 경로 좌표와 움직임 표본(속도는 위치에서 나온 값)을 지운 사본. 서버도 함께 지운다. */
    fun purgeRoute(): CompletedRun = copy(route = emptyList(), motions = emptyList())

    /** 심박 동의 철회 파기 지시: 심박을 지운 사본. */
    fun purgeHeartRates(): CompletedRun = copy(heartRates = emptyList())
}
