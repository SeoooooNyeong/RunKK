package com.a207.runkk.crew;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSummary;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 크루런 대기실·결과 화면과 도착 시각을 만든다(CrewRunService.enterLobby·result·onRunSaved). 다른 패키지 자료는
 * MemberQueryService(표시명)·RunQueryService(기록·도달 시간)로만 읽는다(D-32). CrewRunService의 생성자 의존을 Fan-Out 7
 * 안에 두려고 뗀 패키지 전용 보조다.
 */
@Component
class CrewRunViews {

    /** 첫 크루런 여부를 볼 때의 가장 이른 날(서비스 시작 전). */
    static final LocalDate SERVICE_START = LocalDate.of(2026, 1, 1);

    private final MemberQueryService memberQueryService;
    private final RunQueryService runQueryService;
    private final WeeklyRaceRepository weeklyRaceRepository;
    private final CrewPostRepository crewPostRepository;

    CrewRunViews(
            MemberQueryService memberQueryService,
            RunQueryService runQueryService,
            WeeklyRaceRepository weeklyRaceRepository,
            CrewPostRepository crewPostRepository) {
        this.memberQueryService = memberQueryService;
        this.runQueryService = runQueryService;
        this.weeklyRaceRepository = weeklyRaceRepository;
        this.crewPostRepository = crewPostRepository;
    }

    /** 게시글ID + 모집희망일시 + 크루런거리 + 남은시간 + {갈래요 참가자 표시명 + 준비상태}(srs.md 6.3 5.7.1). */
    LobbyView lobby(CrewRunPost post, LocalDateTime now) {
        List<CrewRunParticipation> going =
                post.participations().stream()
                        .filter(
                                p ->
                                        p.answer() == AttendanceAnswer.GOING
                                                && p.memberId().isPresent())
                        .toList();
        Map<Long, MemberSummary> names =
                memberQueryService.summaries(
                        going.stream().map(p -> p.memberId().orElseThrow()).toList());
        Duration remaining = Duration.between(now, post.scheduledAt());
        return new LobbyView(
                post.getId(),
                post.scheduledAt(),
                post.distance(),
                remaining.isNegative() ? Duration.ZERO : remaining,
                post.status(),
                going.stream()
                        .map(
                                p -> {
                                    long id = p.memberId().orElseThrow();
                                    return new LobbyView.Entry(
                                            id, nameOf(names, id), p.readiness());
                                })
                        .toList());
    }

    /** 순위별 표시명·기록·평균 페이스(연결된 러닝), 열람자의 첫 크루런 여부, 크루 반영 거리 합(WeeklyRace.contributionOf)(FR-191). */
    CrewRunResultView result(CrewRunPost post, long viewerId) {
        List<CrewRunParticipation> runners =
                post.participations().stream()
                        .filter(CrewRunParticipation::isRunner)
                        .sorted(
                                Comparator.comparing(
                                        CrewRunParticipation::rank,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();
        Map<Long, MemberSummary> names =
                memberQueryService.summaries(
                        runners.stream().flatMap(p -> p.memberId().stream()).toList());
        List<CrewRunResultView.Line> lines =
                runners.stream()
                        .map(p -> lineOf(p, names, post.scheduledAt().toLocalDate()))
                        .toList();
        int finishers = (int) runners.stream().filter(p -> p.rank() != null).count();
        Distance contribution =
                post.linkedRecordIds().stream()
                        .map(weeklyRaceRepository::contributionOf)
                        .reduce(Distance.ZERO, Distance::plus);
        return new CrewRunResultView(
                post.getId(),
                post.status(),
                finishers,
                lines,
                isFirstCrewRun(post, viewerId),
                contribution);
    }

    private CrewRunResultView.Line lineOf(
            CrewRunParticipation p, Map<Long, MemberSummary> names, LocalDate day) {
        Optional<RunSummary> run =
                p.memberId().flatMap(id -> p.linkedRecordId().flatMap(r -> summaryOf(id, r, day)));
        DisplayName name = p.memberId().map(id -> nameOf(names, id)).orElse(DisplayName.WITHDRAWN);
        return new CrewRunResultView.Line(
                p.rank(),
                name,
                run.map(RunSummary::elapsed).orElse(null),
                run.map(RunSummary::averagePace).orElse(null),
                p.hasQuit(),
                p.memberId().orElse(null));
    }

    /**
     * 연결된 기록의 요약(크루런 날 앞뒤 하루). 탈퇴·파기로 기록이 지워졌으면 비어 있다(결과 화면은 기록 없이 순위만). 없는 기록을 예외로 읽으면 읽기 트랜잭션이 롤백
     * 표시되므로 목록 조회로 찾는다.
     */
    private Optional<RunSummary> summaryOf(long memberId, String recordId, LocalDate day) {
        return runQueryService
                .summariesBetween(memberId, new DateRange(day.minusDays(1), day.plusDays(1)))
                .stream()
                .filter(s -> s.recordId().equals(recordId))
                .findFirst();
    }

    /** 열람자가 이 크루런의 진행 참가자이고 그 전에 진행 참가한 크루런이 없으면 참. */
    private boolean isFirstCrewRun(CrewRunPost post, long viewerId) {
        if (post.participationOf(viewerId).filter(CrewRunParticipation::isRunner).isEmpty()) {
            return false;
        }
        DateRange before = new DateRange(SERVICE_START, post.scheduledAt().toLocalDate());
        return crewPostRepository.findCrewRunsOf(viewerId, before).stream()
                .filter(other -> !other.getId().equals(post.getId()))
                .filter(other -> other.scheduledAt().isBefore(post.scheduledAt()))
                .noneMatch(other -> other.runners().contains(viewerId));
    }

    /** 기록이 크루런 거리에 닿았으면 도착 시각 = 시작 일시 + 처음 닿은 경과 시간(FR-191). */
    Optional<LocalDateTime> arrivalAt(String recordId, LocalDateTime startedAt, Distance d) {
        return runQueryService.timeToReach(recordId, d).map(startedAt::plus);
    }

    private static DisplayName nameOf(Map<Long, MemberSummary> names, long memberId) {
        return Optional.ofNullable(names.get(memberId))
                .map(MemberSummary::displayName)
                .orElse(DisplayName.WITHDRAWN);
    }
}
