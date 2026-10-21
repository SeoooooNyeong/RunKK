package com.a207.runkk.runimal;

import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 패키지가 읽는 회원별 Runimal 유형(설계 6.1.8). web.crew(CrewMembersAssembler, D-46)와 profile이 쓴다.
 * RunimalProfile 엔티티를 내주지 않는다(design-review.md A23).
 */
@Service
public class RunimalQuery {

    private final RunimalProfileRepository runimalProfileRepository;

    public RunimalQuery(RunimalProfileRepository runimalProfileRepository) {
        this.runimalProfileRepository = runimalProfileRepository;
    }

    /** 확정 우선, 없으면 임시. 유형이 없는 회원은 맵에 없다. */
    @Transactional(readOnly = true)
    public Map<Long, RunimalType> typesOf(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return runimalProfileRepository.typesOf(memberIds);
    }
}
