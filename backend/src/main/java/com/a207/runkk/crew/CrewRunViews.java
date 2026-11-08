package com.a207.runkk.crew;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSummary;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 크루런 대기실·결과 화면을 만든다(CrewRunService.enterLobby·result). 다른 패키지 자료는
 * MemberQueryService(표시명)·RunQueryService(기록)로만 읽는다(D-32). CrewRunService의 생성자 의존을 Fan-Out 7 안에 두려고
 * 뗀 패키지 전용 보조다.
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

    /**
     * 합산 방식 결과(S15P31A207-236): 진행 참가자(입장 순)의 거리·시간·평균 페이스(연결된 러닝, 없으면 끝낼 때 남긴 값, 아직 달리는 중이면 마지막 중계
     * 값 live), 거리 합, 열람자의 연결 기록, 첫 크루런 여부, 크루 반영 거리 합(WeeklyRace.contributionOf).
     *
     * @param live 참가자별 마지막 중계 값(진행 중일 때만, 아니면 빈 맵)
     */
    CrewRunResultView result(CrewRunPost post, long viewerId, Map<Long, LiveProgress> live) {
        List<CrewRunParticipation> runners =
                post.participations().stream().filter(CrewRunParticipation::isRunner).toList();
        Map<Long, MemberSummary> names =
                memberQueryService.summaries(
                        runners.stream().flatMap(p -> p.memberId().stream()).toList());
        LocalDate day = post.scheduledAt().toLocalDate();
        List<CrewRunResultView.Line> lines =
                runners.stream().map(p -> lineOf(p, names, day, live)).toList();
        Distance total =
                lines.stream()
                        .map(CrewRunResultView.Line::distance)
                        .reduce(Distance.ZERO, Distance::plus);
        Distance contribution =
                post.linkedRecordIds().stream()
                        .map(weeklyRaceRepository::contributionOf)
                        .reduce(Distance.ZERO, Distance::plus);
        return new CrewRunResultView(
                post.getId(),
                post.status(),
                titleOf(post.body()),
                post.distance(),
                post.departedAt().orElse(null),
                total,
                lines,
                post.participationOf(viewerId)
                        .filter(CrewRunParticipation::isRunner)
                        .flatMap(CrewRunParticipation::linkedRecordId)
                        .orElse(null),
                isFirstCrewRun(post, viewerId),
                contribution);
    }

    /** 연결된 기록이 있으면 그 거리·시간·페이스, 없으면 끝낼 때 남긴 값, 그것도 없고 아직 달리는 중이면 마지막 중계 값(AN-3), 모두 없으면 거리 0. */
    private CrewRunResultView.Line lineOf(
            CrewRunParticipation p,
            Map<Long, MemberSummary> names,
            LocalDate day,
            Map<Long, LiveProgress> live) {
        Optional<RunSummary> run =
                p.memberId().flatMap(id -> p.linkedRecordId().flatMap(r -> summaryOf(id, r, day)));
        DisplayName name = p.memberId().map(id -> nameOf(names, id)).orElse(DisplayName.WITHDRAWN);
        Optional<LiveProgress> relayed =
                p.hasEnded() ? Optional.empty() : p.memberId().map(live::get);
        Distance distance =
                run.map(RunSummary::distance)
                        .or(p::finalDistance)
                        .or(() -> relayed.map(LiveProgress::distance))
                        .orElse(Distance.ZERO);
        Duration elapsed =
                run.map(RunSummary::elapsed)
                        .or(p::finalElapsed)
                        .or(() -> relayed.map(LiveProgress::elapsed))
                        .orElse(null);
        Pace pace = run.map(RunSummary::averagePace).orElseGet(() -> paceOf(elapsed, distance));
        return new CrewRunResultView.Line(
                name, distance, elapsed, pace, p.hasEnded(), p.memberId().orElse(null));
    }

    /** 끝낼 때 남긴 값으로 계산한 평균 페이스. 거리·시간이 없거나 0이면 null. */
    private static Pace paceOf(Duration elapsed, Distance distance) {
        if (elapsed == null || distance.meters() <= 0) {
            return null;
        }
        long secondsPerKm = Math.round(elapsed.toMillis() / (double) distance.meters());
        return secondsPerKm > 0 ? new Pace((int) secondsPerKm) : null;
    }

    /** 모집글 본문 첫 줄(앱이 제목과 본문을 줄바꿈으로 이어 싣는다). 비었으면 null. */
    static String titleOf(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        return body.strip().lines().findFirst().map(String::strip).orElse(null);
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

    private static DisplayName nameOf(Map<Long, MemberSummary> names, long memberId) {
        return Optional.ofNullable(names.get(memberId))
                .map(MemberSummary::displayName)
                .orElse(DisplayName.WITHDRAWN);
    }
}
