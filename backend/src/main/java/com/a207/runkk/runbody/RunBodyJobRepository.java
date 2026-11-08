package com.a207.runkk.runbody;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 런바디 작업 저장소(설계 05-19 2.2). 구현은 infra.runbody. */
public interface RunBodyJobRepository {

    Optional<RunBodyJob> find(String recordId);

    void save(RunBodyJob job);

    /** 제출 대기·분석 중이고 다음 조회 시각이 now 이하인 기록 ID, 다음 조회 시각 순 최대 limit개. */
    List<String> dueRecordIds(LocalDateTime now, int limit);

    /**
     * 회원의 작업을 만든 순서의 역순(만든 일시, 기록 ID 내림차순)으로 최대 limit개. after가 있으면 그 작업보다 뒤(더 오래된 것)부터다(G-30 목록).
     */
    List<RunBodyJob> latestOf(long memberId, RunBodyJob after, int limit);

    /** before보다 먼저 만든 같은 회원의 작업 중 점수가 있는 가장 최근 것의 점수. 없으면 비어 있다(G-30 이전 러닝 대비 변화). */
    Optional<Integer> previousScore(RunBodyJob before);

    void delete(String recordId);

    void deleteByMember(long memberId);
}
