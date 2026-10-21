package com.a207.runkk.crew;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Week;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
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
import org.springframework.transaction.annotation.Propagation;
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

    private final WeeklyRaceRepository weeklyRaceRepository;
    private final CrewRepository crewRepository;
    private final CrewAccess crewAccess;
    private final RunQueryService runQueryService;
    private final MemberQueryService memberQueryService;
    private final CrewTransactions transactions;
    private final Clock clock;

    WeeklyRaceService(
            WeeklyRaceRepository weeklyRaceRepository,
            CrewRepository crewRepository,
            CrewAccess crewAccess,
            RunQueryService runQueryService,
            MemberQueryService memberQueryService,
            CrewTransactions transactions,
            Clock clock) {
        this.weeklyRaceRepository = weeklyRaceRepository;
        this.crewRepository = crewRepository;
        this.crewAccess = crewAccess;
        this.runQueryService = runQueryService;
        this.memberQueryService = memberQueryService;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** 유효 러닝만. 기록 시작일시에 소속이던 크루의 그 주에 addAtomically(FR-139). 커밋 뒤 동기(결과 화면에 바로 필요, D-03). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRunSaved(RunRecordSaved e) {
        if (!e.valid()) {
            return;
        }
        crewRepository.findMembershipHistory(e.memberId()).stream()
                .filter(m -> m.coversAt(e.startedAt()))
                .findFirst()
                .ifPresent(
                        m ->
                                weeklyRaceRepository.addAtomically(
                                        new RaceContributionKey(
                                                m.crewId(),
                                                Week.of(e.startedAt()),
                                                e.memberId(),
                                                e.recordId()),
                                        e.distance()));
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
                race.contributionRate(viewerId));
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
