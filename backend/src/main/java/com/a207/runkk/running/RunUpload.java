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
        List<HeartRateSample> heartRates) {

    public RunUpload {
        points = points == null ? List.of() : List.copyOf(points);
        unstableSegments = unstableSegments == null ? List.of() : List.copyOf(unstableSegments);
        heartRates = heartRates == null ? List.of() : List.copyOf(heartRates);
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
                List.of());
    }
}
