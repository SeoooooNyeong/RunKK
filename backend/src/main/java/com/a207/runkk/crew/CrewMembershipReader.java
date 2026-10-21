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
 * analysis·profile이 읽는 크루 소속 공개 조회(설계 6.1.7, D-32). crew의 Repository·엔티티를 내주지 않고
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

    /** 크루 랭킹 표시용 크루 이름(design-review.md M6). 없는 크루는 빠진다. */
    public Map<Long, DisplayName> crewNames(Collection<Long> crewIds) {
        Map<Long, DisplayName> names = new HashMap<>();
        crewIds.forEach(
                id ->
                        crewRepository
                                .findById(id)
                                .ifPresent(c -> names.put(id, DisplayName.of(c.name().value()))));
        return names;
    }

    private Optional<Long> activeCrewId(long memberId) {
        return crewRepository.findMembershipHistory(memberId).stream()
                .filter(CrewMembership::isActive)
                .map(CrewMembership::crewId)
                .findFirst();
    }
}
