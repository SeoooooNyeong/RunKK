package com.a207.runkk.avatar;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.DayStats;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 내 러너 조회(W8, S15P31A207-299, 피그마 H-2, FR-163·FR-164 화면 근거). 체형·피부톤은 매일 0시에 정한 저장값을 쓰고, 최근 7일
 * 횟수·거리와 대표 시각은 조회 때 계산한다(오늘 달린 것도 바로 보이게).
 */
@Service
public class RunnerService {

    /** 최근 기간(일, 오늘 포함). */
    static final int RECENT_DAYS = 7;

    private final AvatarRepository avatarRepository;
    private final RunQueryService runQueryService;
    private final OperationPolicy operationPolicy;
    private final AppearanceCalculator appearanceCalculator;
    private final AvatarTransactions transactions;
    private final Clock clock;

    RunnerService(
            AvatarRepository avatarRepository,
            RunQueryService runQueryService,
            OperationPolicy operationPolicy,
            AppearanceCalculator appearanceCalculator,
            AvatarTransactions transactions,
            Clock clock) {
        this.avatarRepository = avatarRepository;
        this.runQueryService = runQueryService;
        this.operationPolicy = operationPolicy;
        this.appearanceCalculator = appearanceCalculator;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** GET /avatar/runner. 최근 7일 = 오늘 − 6일 ~ 오늘(서버 시간대 날짜). 한 읽기 트랜잭션에서 모두 읽는다. */
    public RunnerView runner(long memberId) {
        LocalDate today = LocalDate.now(clock);
        return transactions.read(() -> runnerOf(memberId, today));
    }

    private RunnerView runnerOf(long memberId, LocalDate today) {
        Collection<DayStats> week =
                runQueryService
                        .dailyStats(
                                memberId, new DateRange(today.minusDays(RECENT_DAYS - 1), today))
                        .values();
        int count = week.stream().mapToInt(DayStats::count).sum();
        Distance distance =
                Distance.ofMeters(week.stream().mapToLong(d -> d.distance().meters()).sum());
        // 대표 시각은 피부톤(FR-164)과 같은 근거(최근 유효 10회)로 정한다. 7일 기간과 다르다.
        List<LocalDateTime> starts =
                runQueryService.recentStartTimes(memberId, AppearanceService.RECENT_RUNS);
        Avatar avatar = avatarRepository.findOrCreate(memberId);
        Integer hour =
                appearanceCalculator
                        .typicalStartHour(starts, avatar.skinTone(), operationPolicy.skinToneRule())
                        .orElse(null);
        return new RunnerView(
                avatar.bodyShape(),
                avatar.skinTone(),
                hour,
                count,
                distance,
                operationPolicy.bodyShapeRule().restDaysBeforeDecline());
    }
}
