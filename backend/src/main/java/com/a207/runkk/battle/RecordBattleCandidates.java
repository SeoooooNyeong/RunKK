package com.a207.runkk.battle;

import com.a207.runkk.common.Distance;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * 기록 대결 후보 목록(자료사전 후보기록목록, FR-096, FR-097, FR-100).
 *
 * @param candidates 후보
 */
public record RecordBattleCandidates(List<Candidate> candidates) {

    public RecordBattleCandidates {
        candidates = List.copyOf(candidates);
    }

    /**
     * 후보 하나. 나와의 싸움 거리에 기록이 없으면 finishTime이 null이고 displayName이 "아직 기록이 없어요"다.
     *
     * @param celebrityId 유명인 기록 ID, 나와의 싸움이면 null
     * @param displayName 상대 표시명
     * @param distance 대결 거리
     * @param finishTime 완주 시간
     * @param recordedOn 기록일(나와의 싸움)
     * @param source 출처(유명인)
     * @param estimated 구간 기록 없이 평균 페이스로 추정(A-006)
     * @param myBest 그 거리 내 최고 기록, 없으면 null
     * @param figureKey 유명인 캐릭터 그림 키, 없으면 null(2026-10-31 결정)
     */
    public record Candidate(
            Long celebrityId,
            String displayName,
            Distance distance,
            Duration finishTime,
            LocalDate recordedOn,
            Source source,
            boolean estimated,
            Duration myBest,
            String figureKey) {

        /** 캐릭터 그림 키가 없는 후보. */
        public Candidate(
                Long celebrityId,
                String displayName,
                Distance distance,
                Duration finishTime,
                LocalDate recordedOn,
                Source source,
                boolean estimated,
                Duration myBest) {
            this(
                    celebrityId,
                    displayName,
                    distance,
                    finishTime,
                    recordedOn,
                    source,
                    estimated,
                    myBest,
                    null);
        }

        /** 내 최고 기록을 모르는 후보(나와의 싸움 후보 등). */
        public Candidate(
                Long celebrityId,
                String displayName,
                Distance distance,
                Duration finishTime,
                LocalDate recordedOn,
                Source source,
                boolean estimated) {
            this(
                    celebrityId,
                    displayName,
                    distance,
                    finishTime,
                    recordedOn,
                    source,
                    estimated,
                    null);
        }
    }
}
