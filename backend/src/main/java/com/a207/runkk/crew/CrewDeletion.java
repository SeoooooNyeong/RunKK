package com.a207.runkk.crew;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 크루 하나의 자료를 모두 지운다(srs.md 6.3 5.1.4 크루삭제지시: 크루·소속·대기 신청 D8, 게시글·크루런 D9, 주간 거리 D10).
 * CrewService.delete와 혼자 남은 크루를 지우는 CrewWithdrawalListener가 함께 쓴다.
 */
@Component
class CrewDeletion {

    private final CrewRepository crewRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final CrewPostRepository crewPostRepository;
    private final WeeklyRaceRepository weeklyRaceRepository;

    CrewDeletion(
            CrewRepository crewRepository,
            JoinRequestRepository joinRequestRepository,
            CrewPostRepository crewPostRepository,
            WeeklyRaceRepository weeklyRaceRepository) {
        this.crewRepository = crewRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.crewPostRepository = crewPostRepository;
        this.weeklyRaceRepository = weeklyRaceRepository;
    }

    /** 크루 삭제 때 끝낼 크루런(LOBBY_OPEN·RUNNING, D-66). */
    List<CrewRunPost> activeCrewRunsOf(long crewId) {
        return crewPostRepository.findActiveCrewRunsOf(crewId);
    }

    void deleteAll(long crewId) {
        joinRequestRepository.deleteByCrew(crewId);
        crewPostRepository.deleteByCrew(crewId);
        weeklyRaceRepository.deleteByCrew(crewId);
        crewRepository.delete(crewId);
    }
}
