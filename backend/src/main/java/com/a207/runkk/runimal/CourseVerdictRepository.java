package com.a207.runkk.runimal;

import java.util.Collection;
import java.util.Map;

/** 코스 동일성 판정 저장소 포트(설계 6.1.8). 구현은 infra.runimal. */
public interface CourseVerdictRepository {

    void save(CourseVerdict verdict);

    /** 기록 ID → 같은 코스 여부. 판정이 없는 기록은 맵에 없다. */
    Map<String, Boolean> findByRecords(Collection<String> recordIds);

    void deleteByMember(long memberId);
}
