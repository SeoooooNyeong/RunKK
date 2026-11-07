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

    void delete(String recordId);

    void deleteByMember(long memberId);
}
