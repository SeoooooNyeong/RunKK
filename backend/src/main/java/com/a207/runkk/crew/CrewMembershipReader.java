package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * analysis·profile·crew.chat이 읽는 크루 소속 공개 조회(설계 6.1.7, D-32, D-101). crew의 Repository·엔티티를 내주지 않고
 * CrewSummary·ID·표시명만 준다.
 */
@Service
@Transactional(readOnly = true)
public class CrewMembershipReader {

    private final CrewRepository crewRepository;

    public CrewMembershipReader(CrewRepository crewRepository) {
        this.crewRepository = crewRepository;
    }

    /** 소속 중 크루의 표시 정보. 없으면 비어 있다. */
    public Optional<CrewSummary> activeCrewOf(long memberId) {
        return activeCrewId(memberId)
                .flatMap(crewRepository::findById)
                .map(c -> new CrewSummary(c.getId(), c.name().value(), c.logoUrl()));
    }

    /** 두 회원이 같은 크루에 소속 중인가. */
    public boolean sameCrew(long a, long b) {
        Optional<Long> crewOfA = activeCrewId(a);
        return crewOfA.isPresent() && crewOfA.equals(activeCrewId(b));
    }

    /** 회원과 크루의 관계(crew.chat이 읽을 때마다 부른다, 설계 F.6.2 N1). 크루가 없거나 소속이 아니면 OUTSIDER. */
    public CrewRelation relationOf(long memberId, long crewId) {
        return crewRepository
                .findById(crewId)
                .map(c -> c.relationOf(memberId))
                .orElse(CrewRelation.OUTSIDER);
    }

    public List<Long> activeMemberIds(long crewId) {
        return crewRepository.findById(crewId).map(Crew::activeMemberIds).orElse(List.of());
    }

    /** 크루 ID별 활동 크루원(랭킹 집계용). */
    public Map<Long, List<Long>> activeMembersByCrew() {
        return crewRepository.findActiveMemberships().stream()
                .collect(
                        Collectors.groupingBy(
                                CrewMembership::crewId,
                                Collectors.mapping(CrewMembership::memberId, Collectors.toList())));
    }

    /** 크루 랭킹 표시용 크루 이름(design-review.md M6). 없는 크루는 빠진다. 한 번의 조회로 읽는다(B5 리뷰 N+1). */
    public Map<Long, DisplayName> crewNames(Collection<Long> crewIds) {
        Map<Long, DisplayName> names = new HashMap<>();
        crewRepository
                .findAllById(crewIds)
                .forEach(c -> names.put(c.getId(), DisplayName.of(c.name().value())));
        return names;
    }

    private Optional<Long> activeCrewId(long memberId) {
        return crewRepository.findMembershipHistory(memberId).stream()
                .filter(CrewMembership::isActive)
                .map(CrewMembership::crewId)
                .findFirst();
    }
}
