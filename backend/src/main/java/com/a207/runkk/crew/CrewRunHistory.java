package com.a207.runkk.crew;

import com.a207.runkk.common.Distance;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSummary;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 크루의 지난 크루런 요약(피그마 F-02 "누적 크루런 횟수"·"최근 크루런", W7 F-18, S15P31A207-295). 마친(FINISHED) 크루런만 세고 지운 글은
 * 뺀다. 거리 합은 결과 화면(CrewRunViews.result)과 같은 규칙이다: 참가자마다 연결된 기록 거리 → 끝낼 때 남긴 거리 → 0. 기록은
 * RunQueryService로만 읽는다(D-32). CrewQueryService의 생성자 의존을 늘리지 않으려고 뗀 패키지 전용 보조다.
 */
@Component
class CrewRunHistory {

    private final CrewPostRepository crewPostRepository;
    private final RunQueryService runQueryService;

    CrewRunHistory(CrewPostRepository crewPostRepository, RunQueryService runQueryService) {
        this.crewPostRepository = crewPostRepository;
        this.runQueryService = runQueryService;
    }

    /** 마친 크루런 수. */
    int finishedCount(long crewId) {
        return crewPostRepository.countFinishedCrewRuns(crewId);
    }

    /** 최근 마친 크루런 최대 3건, 희망일시 최신순. 참가자 개인 정보는 담지 않는다(결정 C9). */
    List<CrewDetailView.RecentCrewRun> recent(long crewId) {
        return crewPostRepository
                .findRecentFinishedCrewRuns(crewId, CrewDetailView.RECENT_CREW_RUNS)
                .stream()
                .map(this::summaryOf)
                .toList();
    }

    private CrewDetailView.RecentCrewRun summaryOf(CrewRunPost post) {
        List<CrewRunParticipation> runners =
                post.participations().stream().filter(CrewRunParticipation::isRunner).toList();
        Distance total =
                runners.stream().map(this::distanceOf).reduce(Distance.ZERO, Distance::plus);
        return new CrewDetailView.RecentCrewRun(
                post.getId(), post.scheduledAt(), runners.size(), total);
    }

    /** 연결된 기록(탈퇴하지 않은 참가자의 것) → 끝낼 때 남긴 값 → 0. 지워진 기록은 끝낸 값으로 넘어간다. */
    private Distance distanceOf(CrewRunParticipation p) {
        return p.memberId()
                .flatMap(id -> p.linkedRecordId())
                .flatMap(runQueryService::findSummary)
                .map(RunSummary::distance)
                .or(p::finalDistance)
                .orElse(Distance.ZERO);
    }
}
