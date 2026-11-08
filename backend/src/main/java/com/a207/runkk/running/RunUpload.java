package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 기기가 보낸 완료 러닝 기록(자료사전 완료러닝기록, 설계 6.1.3 RunRecord.receive의 입력). 매개변수가 많아 입력 객체로 묶었다(6.1).
 *
 * @param recordId 기기 UUID(D-11)
 * @param startedAt 시작 일시
 * @param endedAt 종료 일시
 * @param distance 기기가 잰 누적 거리
 * @param elapsed 경과 시간
 * @param mode 러닝 모드
 * @param cardKind 카드 종류
 * @param battleId 대결 ID(대결일 때만)
 * @param crewRunPostId 크루런 게시글 ID(크루런일 때만)
 * @param declaredPointCount 기기가 선언한 포인트 수(NFR-010)
 * @param points 위치 포인트
 * @param unstableSegments 불안정 구간
 * @param heartRates 심박 측정값(없을 수 있음)
 * @param pauses 정지 구간(자유런·크루런만, null·빈 목록 = 정지 없음). elapsed는 정지 시간을 뺀 운동 시간이다(FR-036, FR-194, 크루런은
 *     FR-300)
 * @param startDevice 기록을 시작한 기기(W23, D-378). null(옛 앱) = PHONE
 * @param motions 기기가 잰 속도·케이던스 표본(S15P31A207-242). null(옛 앱)·빈 목록 = 없음
 */
public record RunUpload(
        String recordId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Distance distance,
        Duration elapsed,
        RunMode mode,
        CardKind cardKind,
        Long battleId,
        Long crewRunPostId,
        int declaredPointCount,
        List<LocationPoint> points,
        List<UnstableSegment> unstableSegments,
        List<HeartRateSample> heartRates,
        List<PauseInterval> pauses,
        StartDevice startDevice,
        List<MotionSample> motions) {

    /** 한 기록의 정지 구간 최대 개수(부록 F F.2.2 POST /runs, 제안 100개). */
    public static final int MAX_PAUSES = 100;

    /**
     * 정지 구간 검사(설계 6.1.3 RunUpload, F.2.2, D-163): 자유런·크루런만 비어 있지 않을 수 있다(대결은 정지가 없다), 최대 100개,
     * startedAt ≤ from, to ≤ endedAt, 시간순이고 겹치거나 맞닿지 않는다(앞 구간 to &lt; 뒤 구간 from). 위반은
     * IllegalArgumentException(→ 400).
     */
    public RunUpload {
        points = points == null ? List.of() : List.copyOf(points);
        unstableSegments = unstableSegments == null ? List.of() : List.copyOf(unstableSegments);
        heartRates = heartRates == null ? List.of() : List.copyOf(heartRates);
        pauses = pauses == null ? List.of() : List.copyOf(pauses);
        startDevice = startDevice == null ? StartDevice.PHONE : startDevice;
        motions = motions == null ? List.of() : List.copyOf(motions);
        requireValidPauses(pauses, mode, startedAt, endedAt);
    }

    /** 움직임 표본이 없는 기록(옛 앱). */
    public RunUpload(
            String recordId,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            Distance distance,
            Duration elapsed,
            RunMode mode,
            CardKind cardKind,
            Long battleId,
            Long crewRunPostId,
            int declaredPointCount,
            List<LocationPoint> points,
            List<UnstableSegment> unstableSegments,
            List<HeartRateSample> heartRates,
            List<PauseInterval> pauses,
            StartDevice startDevice) {
        this(
                recordId,
                startedAt,
                endedAt,
                distance,
                elapsed,
                mode,
                cardKind,
                battleId,
                crewRunPostId,
                declaredPointCount,
                points,
                unstableSegments,
                heartRates,
                pauses,
                startDevice,
                List.of());
    }

    /** 폰에서 시작한 기록(시작 기기 PHONE). */
    public RunUpload(
            String recordId,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            Distance distance,
            Duration elapsed,
            RunMode mode,
            CardKind cardKind,
            Long battleId,
            Long crewRunPostId,
            int declaredPointCount,
            List<LocationPoint> points,
            List<UnstableSegment> unstableSegments,
            List<HeartRateSample> heartRates,
            List<PauseInterval> pauses) {
        this(
                recordId,
                startedAt,
                endedAt,
                distance,
                elapsed,
                mode,
                cardKind,
                battleId,
                crewRunPostId,
                declaredPointCount,
                points,
                unstableSegments,
                heartRates,
                pauses,
                StartDevice.PHONE);
    }

    /** 정지 구간이 없는 기록(대결, 정지 없는 자유런·크루런). */
    public RunUpload(
            String recordId,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            Distance distance,
            Duration elapsed,
            RunMode mode,
            CardKind cardKind,
            Long battleId,
            Long crewRunPostId,
            int declaredPointCount,
            List<LocationPoint> points,
            List<UnstableSegment> unstableSegments,
            List<HeartRateSample> heartRates) {
        this(
                recordId,
                startedAt,
                endedAt,
                distance,
                elapsed,
                mode,
                cardKind,
                battleId,
                crewRunPostId,
                declaredPointCount,
                points,
                unstableSegments,
                heartRates,
                List.of(),
                StartDevice.PHONE);
    }

    private static void requireValidPauses(
            List<PauseInterval> pauses,
            RunMode mode,
            LocalDateTime startedAt,
            LocalDateTime endedAt) {
        if (!pauses.isEmpty()) {
            requireAllowed(pauses, mode);
            requireOrderedWithin(pauses, startedAt, endedAt);
        }
    }

    /**
     * 정지 구간은 자유런과 크루런(합산 방식이라 각자 멈춘다, FR-300, MR !91 BR-1)에만 있고 {@value #MAX_PAUSES}개까지다. 대결은 상대와
     * 같은 시계로 겨루므로 정지가 없다.
     */
    private static void requireAllowed(List<PauseInterval> pauses, RunMode mode) {
        if (mode == RunMode.BATTLE) {
            throw new IllegalArgumentException("대결에는 정지 구간이 없다");
        }
        if (pauses.size() > MAX_PAUSES) {
            throw new IllegalArgumentException("정지 구간은 " + MAX_PAUSES + "개까지");
        }
    }

    /** 정지 구간은 시작~종료 안에서 시간순이고 겹치거나 맞닿지 않는다. */
    private static void requireOrderedWithin(
            List<PauseInterval> pauses, LocalDateTime startedAt, LocalDateTime endedAt) {
        if (startedAt == null || endedAt == null) {
            throw new IllegalArgumentException("시작·종료 일시가 비었다");
        }
        PauseInterval previous = null;
        for (PauseInterval p : pauses) {
            if (p.from().isBefore(startedAt) || p.to().isAfter(endedAt)) {
                throw new IllegalArgumentException("정지 구간은 시작~종료 안에 있다");
            }
            if (previous != null && !previous.to().isBefore(p.from())) {
                throw new IllegalArgumentException("정지 구간은 시간순이고 겹치거나 맞닿지 않는다");
            }
            previous = p;
        }
    }

    /** 심박 동의가 없을 때 심박을 뺀 사본(NFR-023). */
    RunUpload withoutHeartRates() {
        return new RunUpload(
                recordId,
                startedAt,
                endedAt,
                distance,
                elapsed,
                mode,
                cardKind,
                battleId,
                crewRunPostId,
                declaredPointCount,
                points,
                unstableSegments,
                List.of(),
                pauses,
                startDevice,
                motions);
    }
}
