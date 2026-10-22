package com.a207.runkk.runimal;

import java.util.Collection;
import java.util.Map;

/** Runimal 판정 상태 저장소 포트(설계 6.1.8). 구현은 infra.runimal. RunimalQuery만 typesOf를 밖에 내준다. */
public interface RunimalProfileRepository {

    RunimalProfile findOrCreate(long memberId);

    void save(RunimalProfile profile);

    /** 확정 우선, 없으면 임시. 유형이 없는 회원은 맵에 없다. */
    Map<Long, RunimalType> typesOf(Collection<Long> memberIds);

    void deleteByMember(long memberId);
}
