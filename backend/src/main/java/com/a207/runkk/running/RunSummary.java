package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 다른 패키지에 내주는 러닝 기록 한 건의 요약(설계 6.1.3, D-32). 좌표가 없다(NFR-021).
 *
 * @param recordId 기록 ID
 * @param memberId 회원 ID
 * @param startedAt 시작 일시
 * @param distance 거리
 * @param elapsed 경과 시간
 * @param averagePace 평균 페이스
 * @param valid 유효 러닝
 * @param score 러닝 점수(무효면 null)
 * @param mode 러닝 모드
 * @param cardKind 카드 종류
 * @param battleId 대결 ID
 * @param crewRunPostId 크루런 게시글 ID
 */
public record RunSummary(
        String recordId,
        long memberId,
        LocalDateTime startedAt,
        Distance distance,
        Duration elapsed,
        Pace averagePace,
        boolean valid,
        Double score,
        RunMode mode,
        CardKind cardKind,
        Long battleId,
        Long crewRunPostId) {}
