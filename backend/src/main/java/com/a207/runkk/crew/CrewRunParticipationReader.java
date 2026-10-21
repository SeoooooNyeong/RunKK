package com.a207.runkk.crew;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.runimal.CrewRunParticipationQuery;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * runimal.CrewRunParticipationQuery 구현(설계 6.1.7, D-46). 기간 안 크루런에 연결된 회원의 기록 ID가 Runimal 동행 축의 소셜런
 * 근거다(FR-146, FR-153). runimal의 RunSampleReader가 이 구현을 자동으로 쓴다.
 */
@Component
public class CrewRunParticipationReader implements CrewRunParticipationQuery {

    private final CrewPostRepository crewPostRepository;

    public CrewRunParticipationReader(CrewPostRepository crewPostRepository) {
        this.crewPostRepository = crewPostRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> linkedRecordIds(long memberId, DateRange range) {
        return crewPostRepository.findCrewRunsOf(memberId, range).stream()
                .flatMap(post -> post.participationOf(memberId).stream())
                .flatMap(p -> p.linkedRecordId().stream())
                .collect(Collectors.toUnmodifiableSet());
    }
}
