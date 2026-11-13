package com.a207.runkk.crew;

import com.a207.runkk.common.Distance;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSummary;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 크루의 지난 크루런 요약(피그마 F-02 "누적 크루런 횟수"·"최근 크루런", W7 F-18, S15P31A207-295). 마친(FINISHED) 크루런만 세고 지운
 * 글·신고 숨김 글은 뺀다. 거리 합은 결과 화면(CrewRunViews.result)과 같은 규칙이다: 참가자마다 연결된 기록 거리 → 끝낼 때 남긴 거리 → 0. 기록은
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

    /** 최근 마친 크루런 최대 3건, 희망일시 최신순. 참가자 개인 정보는 담지 않는다(결정 C9). 연결 기록 요약은 한 번의 일괄 조회로 읽는다(코드 리뷰 M3). */
    List<CrewDetailView.RecentCrewRun> recent(long crewId) {
        List<CrewRunPost> posts =
                crewPostRepository.findRecentFinishedCrewRuns(
                        crewId, CrewDetailView.RECENT_CREW_RUNS);
        Set<String> recordIds =
                posts.stream()
                        .flatMap(post -> post.participations().stream())
                        .filter(CrewRunParticipation::isRunner)
                        .map(CrewRunHistory::countedRecordId)
                        .flatMap(Optional::stream)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, RunSummary> summaries =
                recordIds.isEmpty() ? Map.of() : runQueryService.summariesOf(recordIds);
        return posts.stream().map(post -> summaryOf(post, summaries)).toList();
    }

    /**
     * 마친(FINISHED) 크루런 상세의 참석자별 달린 거리(F-11d, FR-성-8, DV-성-2). 결과 화면과 같은 규칙이다: 진행 참가자의 연결 기록 거리 → 끝낼
     * 때 남긴 거리 → 없음. 달리지 않은 참석자와 마치지 않은 크루런은 없음이다. 연결 기록 요약은 한 번에 읽는다.
     */
    Function<CrewRunParticipation, Optional<Distance>> attendeeDistances(CrewRunPost post) {
        if (post.status() != CrewRunStatus.FINISHED) {
            return p -> Optional.empty();
        }
        Set<String> recordIds =
                post.participations().stream()
                        .filter(CrewRunParticipation::isRunner)
                        .map(CrewRunHistory::countedRecordId)
                        .flatMap(Optional::stream)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, RunSummary> summaries =
                recordIds.isEmpty() ? Map.of() : runQueryService.summariesOf(recordIds);
        return p -> p.isRunner() ? runDistanceOf(p, summaries) : Optional.empty();
    }

    private static CrewDetailView.RecentCrewRun summaryOf(
            CrewRunPost post, Map<String, RunSummary> summaries) {
        List<CrewRunParticipation> runners =
                post.participations().stream().filter(CrewRunParticipation::isRunner).toList();
        Distance total =
                runners.stream()
                        .map(p -> distanceOf(p, summaries))
                        .reduce(Distance.ZERO, Distance::plus);
        return new CrewDetailView.RecentCrewRun(
                post.getId(), post.scheduledAt(), runners.size(), total);
    }

    /** 탈퇴하지 않은 참가자의 연결 기록 ID. */
    private static Optional<String> countedRecordId(CrewRunParticipation p) {
        return p.memberId().flatMap(id -> p.linkedRecordId());
    }

    /** 연결된 기록(탈퇴하지 않은 참가자의 것) → 끝낼 때 남긴 값 → 0. 지워진 기록은 끝낸 값으로 넘어간다. */
    private static Distance distanceOf(CrewRunParticipation p, Map<String, RunSummary> summaries) {
        return runDistanceOf(p, summaries).orElse(Distance.ZERO);
    }

    /** 연결된 기록(탈퇴하지 않은 참가자의 것) → 끝낼 때 남긴 값 → 없음. */
    private static Optional<Distance> runDistanceOf(
            CrewRunParticipation p, Map<String, RunSummary> summaries) {
        return countedRecordId(p)
                .map(summaries::get)
                .map(RunSummary::distance)
                .or(p::finalDistance);
    }
}
