package com.a207.runkk.crew;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Week;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunRecordSaved;
import com.a207.runkk.running.RunSummary;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 크루 주간 거리 합산·조회·재합산(설계 6.1.7, srs.md 6.3 5.6, D-38, D-47). 합산은 WeeklyRaceRepository.addAtomically
 * 한 번으로 하고 (crewId, week, recordId) 고유 제약이 이중 합산을 막아, 수신자와 0시 재합산이 함께 돌아도 한 기록은 한 번만 더한다(5.3 H-06).
 */
@Service
public class WeeklyRaceService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyRaceService.class);
    private static final int DAYS_IN_WEEK = 7;
    private static final int PERCENT = 100;
    private static final String GOAL_ACHIEVED_TEXT = "크루 공동 목표를 달성했어요";

    private final WeeklyRaceRepository weeklyRaceRepository;
    private final CrewRepository crewRepository;
    private final CrewAccess crewAccess;
    private final RunQueryService runQueryService;
    private final MemberQueryService memberQueryService;
    private final NotificationService notificationService;
    private final CrewTransactions transactions;
    private final Clock clock;

    WeeklyRaceService(
            WeeklyRaceRepository weeklyRaceRepository,
            CrewRepository crewRepository,
            CrewAccess crewAccess,
            RunQueryService runQueryService,
            MemberQueryService memberQueryService,
            NotificationService notificationService,
            CrewTransactions transactions,
            Clock clock) {
        this.weeklyRaceRepository = weeklyRaceRepository;
        this.crewRepository = crewRepository;
        this.crewAccess = crewAccess;
        this.runQueryService = runQueryService;
        this.memberQueryService = memberQueryService;
        this.notificationService = notificationService;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * 유효 러닝만. 기록 시작일시에 소속이던 크루의 그 주에 addAtomically(FR-139). 커밋 뒤 동기(결과 화면에 바로 필요, D-03). 일시 오류는 다시
     * 하고 그래도 실패하면 로그만 남긴다. 빠진 기록은 0시 재합산(reconcileAll)이 지난주·이번 주를 다시 맞춘다(M4).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunSaved(RunRecordSaved e) {
        if (!e.valid()) {
            return;
        }
        transactions.afterCommit("weekly_race_add", e.recordId(), () -> addToRace(e));
    }

    /**
     * 기록 시작일시에 소속이던 크루 → Week.of(startedAt) → addAtomically. 더했으면 같은 트랜잭션에서 markAchievedIfReached를
     * 부르고 한 행을 바꿨으면 그 크루의 현재 크루원 모두에게 CREW_GOAL_ACHIEVED(푸시는 커밋 뒤). 늦게 저장된 지난 주 기록도 지난 주에 더하고 지난 주를
     * 판정한다(FR-217, D-105).
     */
    private void addToRace(RunRecordSaved e) {
        crewRepository.findMembershipHistory(e.memberId()).stream()
                .filter(m -> m.coversAt(e.startedAt()))
                .findFirst()
                .ifPresent(
                        m -> {
                            Week week = Week.of(e.startedAt());
                            if (weeklyRaceRepository.addAtomically(
                                    new RaceContributionKey(
                                            m.crewId(), week, e.memberId(), e.recordId()),
                                    e.distance())) {
                                judgeGoal(m.crewId(), week, LocalDateTime.now(clock));
                            }
                        });
    }

    /** 조건부 UPDATE로 달성을 한 번만 기록하고, 바꿨으면 현재 크루원 모두에게 알린다(A-12). */
    private boolean judgeGoal(long crewId, Week week, LocalDateTime now) {
        if (!weeklyRaceRepository.markAchievedIfReached(crewId, week, now)) {
            return false;
        }
        NavigationTarget target =
                new NavigationTarget(
                        NotificationType.CREW_GOAL_ACHIEVED.defaultScreen(),
                        String.valueOf(crewId));
        crewRepository
                .findById(crewId)
                .map(Crew::activeMemberIds)
                .orElse(List.of())
                .forEach(
                        id ->
                                notificationService.notify(
                                        new NotificationRequest(
                                                id,
                                                NotificationType.CREW_GOAL_ACHIEVED,
                                                GOAL_ACHIEVED_TEXT,
                                                target)));
        return true;
    }

    /**
     * check(SET_GOAL, 크루장·운영진) → 이번 주(Week.of(now)) 행을 만들거나(합계 0, 목표 이어 쓰기) 읽어
     * WeeklyRace.setGoal(1km 미만 GOAL_INVALID) → 정한 즉시 이번 주 합계가 목표 이상이면 그 자리에서 달성(이미 달성한 주는 다시 판정하지
     * 않는다) → 달성했으면 CREW_GOAL_ACHIEVED → status(FR-138, FR-217, D-105).
     */
    @Transactional
    public RaceStatusView setGoal(long memberId, long crewId, Distance goal, LocalDateTime now) {
        crewAccess.check(memberId, crewId, CrewAction.SET_GOAL);
        Week week = Week.of(now);
        WeeklyRace race = weeklyRaceRepository.findOrCreate(crewId, week);
        race.setGoal(goal, memberId);
        weeklyRaceRepository.saveGoal(race);
        judgeGoal(crewId, week, now);
        return status(memberId, crewId, Optional.of(week.monday()));
    }

    /** check(VIEW_CREW_INTERNAL) → 합계, 마감, 기여자별 표시명·거리·기여율, 본인 기여율(FR-140). 주를 비우면 이번 주. */
    @Transactional(readOnly = true)
    public RaceStatusView status(long viewerId, long crewId, Optional<LocalDate> weekStart) {
        crewAccess.check(viewerId, crewId, CrewAction.VIEW_CREW_INTERNAL);
        Week week =
                Week.of(
                        weekStart
                                .map(LocalDate::atStartOfDay)
                                .orElseGet(() -> LocalDateTime.now(clock)));
        WeeklyRace race =
                weeklyRaceRepository
                        .find(crewId, week)
                        .orElseGet(() -> WeeklyRace.empty(crewId, week));
        List<RaceContribution> contributions = race.contributions();
        Map<Long, MemberSummary> names =
                memberQueryService.summaries(
                        contributions.stream()
                                .map(RaceContribution::memberId)
                                .filter(Objects::nonNull)
                                .toList());
        List<RaceStatusView.Line> lines =
                contributions.stream()
                        .sorted(Comparator.comparing(RaceContribution::distance).reversed())
                        .map(
                                c ->
                                        new RaceStatusView.Line(
                                                nameOf(names, c),
                                                c.distance(),
                                                rateOf(race, c),
                                                c.memberId()))
                        .toList();
        return new RaceStatusView(
                crewId,
                week.monday(),
                race.total(),
                race.closesAt(),
                lines,
                race.contributionRate(viewerId),
                race.goal().orElse(null),
                race.goalProgressPercent().orElse(null),
                race.achievedAt().orElse(null),
                celebrationOf(crewId, race));
    }

    /** 지난 목표 한 페이지 크기 상한(contract-W7.md). */
    public static final int MAX_HISTORY_SIZE = 100;

    /**
     * check(VIEW_CREW_INTERNAL) → 이번 주 앞의, 목표가 있던 주를 최신 주부터 size개(피그마 맞춤 W7 F-21). before가 있으면 그보다
     * 앞선 주만(이번 주 월요일보다 늦으면 이번 주 월요일로 줄인다). 더 남았으면 nextBefore가 이 페이지 마지막 주 월요일이다. size가 1~100 밖이면
     * IllegalArgumentException(400).
     */
    @Transactional(readOnly = true)
    public GoalHistoryView goalHistory(
            long viewerId, long crewId, Optional<LocalDate> before, int size) {
        if (size < 1 || size > MAX_HISTORY_SIZE) {
            throw new IllegalArgumentException("페이지 크기는 1~" + MAX_HISTORY_SIZE + ": " + size);
        }
        crewAccess.check(viewerId, crewId, CrewAction.VIEW_CREW_INTERNAL);
        LocalDate thisMonday = Week.of(LocalDateTime.now(clock)).monday();
        LocalDate bound = before.filter(b -> b.isBefore(thisMonday)).orElse(thisMonday);
        List<GoalHistoryRow> rows = weeklyRaceRepository.goalHistory(crewId, bound, size + 1);
        List<GoalHistoryView.Line> page =
                rows.stream().limit(size).map(GoalHistoryView.Line::of).toList();
        LocalDate next = rows.size() > size ? page.get(page.size() - 1).weekStart() : null;
        return new GoalHistoryView(page, next);
    }

    /** 달성한 주만 축하 카드(크루명, 합계, 목표, 참여 인원 = 그 주 기여가 있는 회원 수). 저장하지 않고 조회 때 만든다(FR-141, D-105). */
    private CelebrationCard celebrationOf(long crewId, WeeklyRace race) {
        if (!race.isAchieved() || race.goal().isEmpty()) {
            return null;
        }
        int participants =
                (int) race.contributions().stream().filter(c -> c.distance().meters() > 0).count();
        String crewName = crewAccess.require(crewId).name().value();
        return new CelebrationCard(crewName, race.total(), race.goal().get(), participants);
    }

    private static int rateOf(WeeklyRace race, RaceContribution c) {
        if (race.total().meters() == 0) {
            return 0;
        }
        return (int) (c.distance().meters() * PERCENT / race.total().meters());
    }

    private static DisplayName nameOf(Map<Long, MemberSummary> names, RaceContribution c) {
        return Optional.ofNullable(c.memberId())
                .map(names::get)
                .map(MemberSummary::displayName)
                .orElse(DisplayName.WITHDRAWN);
    }

    /** 기록 하나의 반영 거리(결과 화면, analysis가 부른다). */
    @Transactional(readOnly = true)
    public Distance contributionOf(String recordId) {
        return weeklyRaceRepository.contributionOf(recordId);
    }

    /** 기록 하나가 반영된 크루의 이름(결과 화면 "OO 크루 목표에 반영", B-6 v2, S15P31A207-251). 반영이 없으면 빈 값. */
    @Transactional(readOnly = true)
    public Optional<String> contributedCrewNameOf(String recordId) {
        return weeklyRaceRepository
                .contributedCrewOf(recordId)
                .flatMap(crewRepository::findById)
                .map(crew -> crew.name().value());
    }

    /**
     * batch.crew의 WeeklyRaceReconcileJob이 매일 0시에 now만 넘겨 부른다(D-38, D-47). 이번 주와 지난주, 그 기간에 소속 행이 있던
     * 크루마다 크루원별 기대 합계(RunQueryService.distancesByMember)와 기여를 비교해 다르면 빠진 유효 기록만 addAtomically로 더한다.
     * 더한 기록 수를 돌려준다. 크루 하나의 실패는 로그만 남기고 다음 크루로 넘어간다.
     */
    public int reconcileAll(LocalDateTime now) {
        Week current = Week.of(now);
        List<Week> weeks = List.of(current.previous(), current);
        DateRange span =
                new DateRange(
                        current.previous().monday(), current.monday().plusDays(DAYS_IN_WEEK - 1));
        List<Long> crewIds = transactions.read(() -> crewRepository.crewIdsActiveBetween(span));
        int added = 0;
        for (long crewId : crewIds) {
            added += transactions.once("weekly_race_reconcile", () -> reconcile(crewId, weeks), 0);
        }
        log.info("weekly_race_reconciled crews={} added={}", crewIds.size(), added);
        return added;
    }

    private int reconcile(long crewId, List<Week> weeks) {
        Optional<Crew> crew = crewRepository.findById(crewId);
        if (crew.isEmpty()) {
            return 0;
        }
        int added = 0;
        for (Week week : weeks) {
            WeeklyRace race =
                    weeklyRaceRepository
                            .find(crewId, week)
                            .orElseGet(() -> WeeklyRace.empty(crewId, week));
            for (CrewMembership membership : crew.get().memberships()) {
                added += reconcileMember(race, membership, week);
            }
            judgeGoal(crewId, week, LocalDateTime.now(clock)); // 놓친 달성을 채운다(D-105)
        }
        return added;
    }

    private int reconcileMember(WeeklyRace race, CrewMembership membership, Week week) {
        Optional<DateRange> overlap = overlapOf(membership, week);
        if (overlap.isEmpty()) {
            return 0;
        }
        long memberId = membership.memberId();
        Distance expected =
                runQueryService.distancesByMember(List.of(memberId), overlap.get()).get(memberId);
        Distance actual =
                race.contributions().stream()
                        .filter(c -> Objects.equals(c.memberId(), memberId))
                        .map(RaceContribution::distance)
                        .reduce(Distance.ZERO, Distance::plus);
        if (actual.equals(expected)) {
            return 0;
        }
        int added = 0;
        for (RunSummary run : runQueryService.summariesBetween(memberId, overlap.get())) {
            if (run.valid()
                    && membership.coversAt(run.startedAt())
                    && week.contains(run.startedAt())
                    && race.contributionOf(run.recordId()).meters() == 0
                    && weeklyRaceRepository.addAtomically(
                            new RaceContributionKey(race.crewId(), week, memberId, run.recordId()),
                            run.distance())) {
                added++;
            }
        }
        return added;
    }

    /** 소속 기간과 주가 겹치는 날짜 범위. */
    private static Optional<DateRange> overlapOf(CrewMembership membership, Week week) {
        LocalDate weekEnd = week.monday().plusDays(DAYS_IN_WEEK - 1);
        LocalDate from = max(week.monday(), membership.joinedAt().toLocalDate());
        LocalDate to =
                membership
                        .leftAt()
                        .map(LocalDateTime::toLocalDate)
                        .map(d -> min(d, weekEnd))
                        .orElse(weekEnd);
        return to.isBefore(from) ? Optional.empty() : Optional.of(new DateRange(from, to));
    }

    private static LocalDate max(LocalDate left, LocalDate right) {
        return left.isAfter(right) ? left : right;
    }

    private static LocalDate min(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }
}
