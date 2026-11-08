package com.a207.runkk.analysis;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.RecordVisibility;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.ViewerRelation;
import com.a207.runkk.crew.CrewMembershipReader;
import com.a207.runkk.crew.WeeklyRaceService;
import com.a207.runkk.member.MemberPreference;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.reward.AccrualReason;
import com.a207.runkk.reward.RewardService;
import com.a207.runkk.running.HeartRateSummary;
import com.a207.runkk.running.NewRecordMark;
import com.a207.runkk.running.PauseInterval;
import com.a207.runkk.running.RunDetail;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSummary;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기록 목록·상세·카드 값을 조회한다(설계 6.1.4 RecordQueryService, srs.md 6.3 3.2 의사결정표, FR-058~FR-060, FR-068,
 * FR-078, FR-079, FR-185, FR-186). 공개 판정은 기록 주인의 기록공개범위(RecordVisibility)로 하고, 코스 지도는 본인이거나 코스지도공유가
 * 켜졌을 때만 넣는다. 크루 반영 거리와 반영된 크루 이름은 WeeklyRaceService.contributionOf·contributedCrewNameOf, 적립량은
 * RewardService.accruedFor로 읽는다(5.2).
 */
@Service
@Transactional(readOnly = true)
public class RecordQueryService {

    /** 목록 한 번에 볼 수 있는 최대 기간(일). 페이지 대신 기간으로 자른다. */
    static final long MAX_LIST_DAYS = 366;

    private static final String LONGEST_LABEL = "최장 거리 신기록";
    private static final String HALF_LABEL = "하프";
    private static final String FULL_LABEL = "풀";

    /** 최대 심박 = 220 − 나이(FR-185). */
    private static final int MAX_HEART_RATE_BASE = 220;

    private final RunQueryService runQueryService;
    private final MemberQueryService memberQueryService;
    private final CrewMembershipReader crewMembershipReader;
    private final WeeklyRaceService weeklyRaceService;
    private final RewardService rewardService;
    private final OperationPolicy policy;
    private final Clock clock;

    public RecordQueryService(
            RunQueryService runQueryService,
            MemberQueryService memberQueryService,
            CrewMembershipReader crewMembershipReader,
            WeeklyRaceService weeklyRaceService,
            RewardService rewardService,
            OperationPolicy policy,
            Clock clock) {
        this.runQueryService = runQueryService;
        this.memberQueryService = memberQueryService;
        this.crewMembershipReader = crewMembershipReader;
        this.weeklyRaceService = weeklyRaceService;
        this.rewardService = rewardService;
        this.policy = policy;
        this.clock = clock;
    }

    /** 공개 판정 뒤 기간 안 기록 최신순. 무효 러닝은 valid = false("집계 제외"). 기간은 366일까지. */
    public RecordListView list(long viewerId, long ownerId, DateRange range) {
        if (ChronoUnit.DAYS.between(range.start(), range.end()) >= MAX_LIST_DAYS) {
            throw new IllegalArgumentException("조회 기간은 366일까지");
        }
        MemberPreference owner = memberQueryService.preferenceOf(ownerId);
        requireVisible(viewerId, ownerId, owner);
        // 경로 모양은 위치를 드러내지 않지만 코스 지도 공개 규칙과 같게 둔다(D-234)
        boolean showShape = viewerId == ownerId || owner.courseMapShared();
        return new RecordListView(
                ownerId,
                runQueryService.listRows(ownerId, range).stream()
                        .map(
                                row ->
                                        RecordRow.of(
                                                row.summary(),
                                                row.newRecords(),
                                                showShape ? row.routeShape() : null))
                        .toList());
    }

    /** 기록 상세. 본인 전용 항목(심박 구간, 크루 반영, 리워드)은 본인에게만 채운다. */
    public RecordDetailView detail(long viewerId, String recordId) {
        RunSummary summary = runQueryService.summary(recordId);
        long ownerId = summary.memberId();
        MemberPreference owner = memberQueryService.preferenceOf(ownerId);
        requireVisible(viewerId, ownerId, owner);
        boolean self = viewerId == ownerId;
        RunDetail detail =
                runQueryService.detail(
                        recordId, self ? maxHeartRateOf(ownerId) : OptionalInt.empty());
        Distance contribution = self ? contributionOf(recordId) : null;
        return new RecordDetailView(
                RecordRow.of(summary),
                self,
                self || owner.courseMapShared() ? runQueryService.routePoints(recordId) : List.of(),
                policy.calorieCoefficient().estimate(summary.distance()),
                detail.splitPaces(),
                detail.fastestKm(),
                heartRateOf(detail.heartRate()),
                self ? zonesOf(detail) : Map.of(),
                unstableOf(detail),
                weatherOf(detail),
                newRecordLabels(detail.newRecords()),
                contribution,
                contribution == null
                        ? null
                        : weeklyRaceService.contributedCrewNameOf(recordId).orElse(null),
                self ? rewardService.accruedFor(AccrualReason.VALID_RUN, recordId, ownerId) : null,
                detail.pauses().stream()
                        .map(p -> new RecordDetailView.PauseRange(p.from(), p.to()))
                        .toList(),
                detail.pauses().stream()
                        .map(PauseInterval::duration)
                        .reduce(Duration.ZERO, Duration::plus)
                        .toSeconds(),
                self && detail.deletable(),
                self ? owner.recordVisibility() : null);
    }

    /** 본인 기록만(srs.md 6.3 3.5). 앞면 값 + 뒷면 경로 + 코스지도공유 값. */
    public RunCardData cardData(long memberId, String recordId) {
        RunSummary summary = runQueryService.summary(recordId);
        if (summary.memberId() != memberId) {
            throw new PermissionDeniedException("본인 기록 카드만 볼 수 있다");
        }
        RunDetail detail = runQueryService.detail(recordId, OptionalInt.empty());
        List<GeoPoint> route = runQueryService.routePoints(recordId);
        HeartRateSummary heart = detail.heartRate();
        return new RunCardData(
                recordId,
                summary.cardKind().name(),
                summary.startedAt(),
                summary.distance(),
                summary.elapsed(),
                summary.averagePace(),
                heart == null ? null : heart.average(),
                newRecordLabels(detail.newRecords()),
                route,
                memberQueryService.preferenceOf(memberId).courseMapShared(),
                weatherOf(detail),
                nicknameOf(memberId));
    }

    private String nicknameOf(long memberId) {
        MemberSummary summary = memberQueryService.summaries(List.of(memberId)).get(memberId);
        return (summary == null ? DisplayName.WITHDRAWN : summary.displayName()).value();
    }

    /** srs.md 6.3 3.2 규칙 1~4: 본인 → 전부, 비공개 → 거부, 크루 공개인데 다른 크루 → 거부, 나머지 허용. */
    private void requireVisible(long viewerId, long ownerId, MemberPreference owner) {
        ViewerRelation relation = relationOf(viewerId, ownerId);
        RecordVisibility visibility = owner.recordVisibility();
        if (visibility.allows(relation)) {
            return;
        }
        throw new BusinessRuleException(
                visibility == RecordVisibility.CREW
                        ? ReasonCode.RECORD_CREW_ONLY
                        : ReasonCode.RECORD_PRIVATE);
    }

    private ViewerRelation relationOf(long viewerId, long ownerId) {
        if (viewerId == ownerId) {
            return ViewerRelation.OWNER;
        }
        return crewMembershipReader.sameCrew(viewerId, ownerId)
                ? ViewerRelation.SAME_CREW
                : ViewerRelation.OTHER;
    }

    private OptionalInt maxHeartRateOf(long ownerId) {
        OptionalInt age = memberQueryService.ageOf(ownerId, LocalDate.now(clock).getYear());
        return age.isPresent()
                ? OptionalInt.of(MAX_HEART_RATE_BASE - age.getAsInt())
                : OptionalInt.empty();
    }

    private Distance contributionOf(String recordId) {
        Distance contribution = weeklyRaceService.contributionOf(recordId);
        return contribution.meters() == 0 ? null : contribution;
    }

    private static RecordDetailView.HeartRate heartRateOf(HeartRateSummary heart) {
        return heart == null ? null : new RecordDetailView.HeartRate(heart.average(), heart.max());
    }

    private static Map<String, Duration> zonesOf(RunDetail detail) {
        Map<String, Duration> zones = new LinkedHashMap<>();
        detail.heartRateZones().forEach((zone, time) -> zones.put(zone.name(), time));
        return zones;
    }

    private static List<RecordDetailView.UnstableRange> unstableOf(RunDetail detail) {
        LocalDateTime start = detail.summary().startedAt();
        return detail.unstableSegments().stream()
                .map(
                        segment ->
                                new RecordDetailView.UnstableRange(
                                        Duration.between(start, segment.from()),
                                        Duration.between(start, segment.to()),
                                        segment.distance()))
                .toList();
    }

    private static RecordDetailView.WeatherInfo weatherOf(RunDetail detail) {
        return detail.weather() == null
                ? null
                : new RecordDetailView.WeatherInfo(
                        detail.weather().temperatureC(), detail.weather().sky().name());
    }

    /** "최장 거리 신기록 · 직전 n km", "n km 신기록"(FR-186). */
    private static List<String> newRecordLabels(List<NewRecordMark> marks) {
        return marks.stream()
                .map(
                        mark ->
                                mark.kind() == NewRecordMark.Kind.LONGEST
                                        ? LONGEST_LABEL + " · " + mark.previous()
                                        : baseLabel(mark.distance()) + " 신기록")
                .toList();
    }

    private static String baseLabel(StandardDistance distance) {
        if (distance == StandardDistance.HALF) {
            return HALF_LABEL;
        }
        if (distance == StandardDistance.FULL) {
            return FULL_LABEL; // 풀 최고 기록(v0.14 D-238)
        }
        return distance.distance().meters() / Distance.METERS_PER_KM + " km";
    }
}
