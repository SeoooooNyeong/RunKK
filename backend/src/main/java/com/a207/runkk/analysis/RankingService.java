package com.a207.runkk.analysis;

import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.crew.CrewMembershipReader;
import com.a207.runkk.crew.CrewSummary;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 개인·크루 랭킹(설계 6.1.4 RankingService, D-25, D-36, FR-070, FR-071). 0시에 개인·크루 × 주간·월간 4개 스냅숏을 만들고 조회는
 * 최근 스냅숏만 읽는다. 대상 회원(나만 보기 제외)과 크루원의 합집합으로 기간마다 RunQueryService.distancesByMember를 한 번 부르고, 표시명·크루
 * 이름도 묶음 조회 한 번으로 붙인다(N+1 방지, design-review.md M6). 크루 점수는 크루원 거리 합 ÷ 크루원 수(비공개 포함)다. 운영자는 관리 API로
 * 0시를 기다리지 않고 다시 집계할 수 있다(recompute, 설계서 갱신 요청).
 */
@Service
public class RankingService {

    /** 조회 응답에 담는 상위 줄 수. 본인 줄은 따로 준다. */
    public static final int TOP = 100;

    static final String PRIVATE_NOTICE = "나만 보기 설정으로 랭킹에 나오지 않음";
    static final String NO_CREW_NOTICE = "소속 크루가 없어 크루 순위가 비어 있음";

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);

    private final RankingSnapshotRepository snapshotRepository;
    private final MemberQueryService memberQueryService;
    private final CrewMembershipReader crewMembershipReader;
    private final RunQueryService runQueryService;
    private final AnalysisTransactions transactions;
    private final Clock clock;

    /**
     * 집계 하나만 돌게 하는 잠금(0시 작업·시작 집계·운영자 재계산 공용). 같은 종류·기간의 스냅숏 교체가 두 트랜잭션에서 겹치면 한쪽이 이미 지운 이전 스냅숏을 다시
     * 지우다 실패하거나 캐시 상위 키가 늦게 커밋된 옛 판을 가리킬 수 있어 집계 전체를 직렬로 한다. 서버가 한 대(infra/docker-compose api 하나)라
     * 프로세스 잠금으로 충분하다.
     */
    private final ReentrantLock rebuildLock = new ReentrantLock();

    public RankingService(
            RankingSnapshotRepository snapshotRepository,
            MemberQueryService memberQueryService,
            CrewMembershipReader crewMembershipReader,
            RunQueryService runQueryService,
            AnalysisTransactions transactions,
            Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.memberQueryService = memberQueryService;
        this.crewMembershipReader = crewMembershipReader;
        this.runQueryService = runQueryService;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * 0시. batch.analysis의 RankingRebuildJob이 부른다. 기간마다 스냅숏 저장은 따로 감싼다. 운영자 재계산({@link #recompute})이
     * 돌고 있으면 끝날 때까지 기다렸다 이어서 집계한다(0시 집계를 건너뛰지 않는다).
     */
    public void rebuild(LocalDateTime now) {
        rebuildLock.lock();
        try {
            rebuildLocked(now);
        } finally {
            rebuildLock.unlock();
        }
    }

    /**
     * 운영자 관리 API(web.admin RankingAdminController)가 0시를 기다리지 않고 지금 시각으로 다시 집계한다. 0시 작업과 같은 경로(4개
     * 스냅숏을 종류·기간마다 한 트랜잭션에서 바꾸고 커밋 뒤 캐시 판을 바꾼다)를 같은 잠금 안에서 탄다. 다른 집계(0시 작업, 시작 집계, 다른 운영자 요청)가 돌고
     * 있으면 기다리지 않고 IllegalStateException(web.error가 409로 바꾼다)을 던진다.
     */
    public RankingRecomputeResult recompute() {
        if (!rebuildLock.tryLock()) {
            throw new IllegalStateException("랭킹 집계가 이미 진행 중");
        }
        try {
            return rebuildLocked(LocalDateTime.now(clock));
        } finally {
            rebuildLock.unlock();
        }
    }

    private RankingRecomputeResult rebuildLocked(LocalDateTime now) {
        Set<Long> ranked = memberQueryService.rankedMemberIds();
        Map<Long, List<Long>> crews = crewMembershipReader.activeMembersByCrew();
        Set<Long> everyone = new HashSet<>(ranked);
        crews.values().forEach(everyone::addAll);
        Map<Long, MemberSummary> names = memberQueryService.summaries(ranked);
        Map<Long, DisplayName> crewNames = crewMembershipReader.crewNames(crews.keySet());
        int personalCount = 0;
        int crewCount = 0;
        int failed = 0;
        for (AggregationPeriod period : AggregationPeriod.values()) {
            DateRange range = period.rangeOf(now.toLocalDate());
            Map<Long, Distance> distances = runQueryService.distancesByMember(everyone, range);
            List<RankScore> personal = personalScores(ranked, names, distances);
            List<RankScore> crew = crewScores(crews, crewNames, distances);
            personalCount = personal.size();
            crewCount = crew.size();
            failed += save(RankingSnapshot.build(RankingKind.PERSONAL, period, now, personal));
            failed += save(RankingSnapshot.build(RankingKind.CREW, period, now, crew));
        }
        return new RankingRecomputeResult(now, personalCount, crewCount, failed);
    }

    /**
     * 시작 때 스냅숏이 하나도 없으면(첫 배포·DB 초기화) 한 번 집계해 조회 응답의 집계 기준 시각이 비지 않게 한다. 다음부터는 0시 작업만 집계한다(D-25). 시작
     * 작업을 그 작업을 가진 패키지가 하는 방식(D-55)이다. 실패는 로그만 남긴다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void rebuildIfMissing() {
        try {
            if (transactions
                    .read(
                            () ->
                                    snapshotRepository.latest(
                                            RankingKind.PERSONAL, AggregationPeriod.WEEKLY))
                    .isEmpty()) {
                rebuild(LocalDateTime.now(clock));
            }
        } catch (RuntimeException e) {
            log.warn("ranking_startup_rebuild_failed error={}", e.getClass().getSimpleName());
        }
    }

    /** 개인 최근 스냅숏 + 본인 줄 + 바로 위까지 남은 거리. 나만 보기면 안내 문구. */
    public RankingView personalRanking(long memberId, AggregationPeriod period) {
        String notice =
                memberQueryService
                                .preferenceOf(memberId)
                                .recordVisibility()
                                .includedInPersonalRanking()
                        ? null
                        : PRIVATE_NOTICE;
        return viewOf(RankingKind.PERSONAL, period, Optional.of(memberId), notice);
    }

    /** 크루 최근 스냅숏 + 본인 크루 줄. 크루가 없으면 본인 크루 순위를 비운다. */
    public RankingView crewRanking(long memberId, AggregationPeriod period) {
        Optional<Long> crewId =
                crewMembershipReader.activeCrewOf(memberId).map(CrewSummary::crewId);
        return viewOf(RankingKind.CREW, period, crewId, crewId.isEmpty() ? NO_CREW_NOTICE : null);
    }

    private RankingView viewOf(
            RankingKind kind, AggregationPeriod period, Optional<Long> subjectId, String notice) {
        Optional<RankingPage> latest =
                transactions.read(
                        () -> snapshotRepository.latestPage(kind, period, TOP, subjectId));
        if (latest.isEmpty()) {
            return new RankingView(kind, period, null, List.of(), null, null, notice);
        }
        RankingPage page = latest.get();
        return new RankingView(
                kind, period, page.computedAt(), page.top(), page.mine(), page.gapToNext(), notice);
    }

    private static List<RankScore> personalScores(
            Collection<Long> ranked,
            Map<Long, MemberSummary> names,
            Map<Long, Distance> distances) {
        List<RankScore> scores = new ArrayList<>();
        for (Long id : ranked) {
            MemberSummary summary = names.get(id);
            if (summary != null) {
                scores.add(
                        new RankScore(
                                id,
                                summary.displayName(),
                                summary.tier(),
                                distances.getOrDefault(id, Distance.ZERO)));
            }
        }
        return scores;
    }

    private static List<RankScore> crewScores(
            Map<Long, List<Long>> crews,
            Map<Long, DisplayName> crewNames,
            Map<Long, Distance> distances) {
        List<RankScore> scores = new ArrayList<>();
        crews.forEach(
                (crewId, memberIds) -> {
                    DisplayName name = crewNames.get(crewId);
                    if (name == null || memberIds.isEmpty()) {
                        return;
                    }
                    long sum =
                            memberIds.stream()
                                    .mapToLong(
                                            id ->
                                                    distances
                                                            .getOrDefault(id, Distance.ZERO)
                                                            .meters())
                                    .sum();
                    scores.add(
                            new RankScore(
                                    crewId, name, null, Distance.ofMeters(sum / memberIds.size())));
                });
        return scores;
    }

    /** 저장하면 0, 실패하면 1(로그만 남기고 다른 종류·기간은 이어서 저장한다). */
    private int save(RankingSnapshot snapshot) {
        try {
            transactions.write(
                    () -> {
                        snapshotRepository.save(snapshot);
                        return null;
                    });
            return 0;
        } catch (RuntimeException e) {
            log.error(
                    "ranking_snapshot_failed kind={} period={} error={}",
                    snapshot.getKind(),
                    snapshot.getPeriod(),
                    e.getClass().getSimpleName(),
                    e);
            return 1;
        }
    }
}
