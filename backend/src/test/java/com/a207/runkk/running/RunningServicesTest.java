package com.a207.runkk.running;

import static com.a207.runkk.running.RunFixtures.START;
import static com.a207.runkk.running.RunFixtures.id;
import static com.a207.runkk.running.RunFixtures.upload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.audit.InMemoryAuditRepository;
import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.DomainEvent;
import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.ConsentWithdrawn;
import com.a207.runkk.member.InMemoryMemberRepository;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.member.RunScoreInput;
import com.a207.runkk.member.TierLevelService;
import com.a207.runkk.policy.InMemoryOperationSettingRepository;
import com.a207.runkk.policy.OperationPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** running 응용 서비스(인메모리 저장소·가짜 날씨·고정 시계): A-04 업로드, 파기(SQ-13), 조회 투영, 티어 수신자. */
class RunningServicesTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 12, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    /** 트랜잭션 경계만 흉내 내는 관리자(인메모리 저장소용). */
    static final PlatformTransactionManager NO_TX =
            new PlatformTransactionManager() {
                @Override
                public TransactionStatus getTransaction(TransactionDefinition definition) {
                    return new SimpleTransactionStatus();
                }

                @Override
                public void commit(TransactionStatus status) {}

                @Override
                public void rollback(TransactionStatus status) {}
            };

    private final InMemoryRunningRepositories.Records records =
            new InMemoryRunningRepositories.Records();
    private final InMemoryRunningRepositories.Books books = new InMemoryRunningRepositories.Books();
    private final InMemoryRunningRepositories.Orders orders =
            new InMemoryRunningRepositories.Orders();
    private final InMemoryMemberRepository members = new InMemoryMemberRepository();
    private final InMemoryAuditRepository audits = new InMemoryAuditRepository();
    private final List<DomainEvent> published = new ArrayList<>();
    private final List<GeoPoint> weatherAsked = new ArrayList<>();
    private RuntimeException weatherFailure;

    private final WeatherGateway weather =
            (point, at) -> {
                weatherAsked.add(point);
                if (weatherFailure != null) {
                    throw weatherFailure;
                }
                return Optional.of(new Weather(12, SkyCondition.CLEAR));
            };

    private final RunUploadService upload =
            new RunUploadService(
                    records,
                    books,
                    weather,
                    new MemberQueryService(members),
                    published::add,
                    NO_TX,
                    clock);
    private final DisposalService disposal =
            new DisposalService(orders, records, new AuditService(audits, clock), clock);
    private final RunQueryService query = new RunQueryService(records, books, clock);

    private long member() {
        return members.save(MemberFixtures.member("러너" + (members.size() + 10))).getId();
    }

    private UploadResult send(long memberId, RunUpload u) {
        return upload.upload(memberId, new RunUploadCommand(u));
    }

    private List<RunRecordSaved> saved() {
        return published.stream().map(RunRecordSaved.class::cast).toList();
    }

    @Test
    void upload_assignsOrdinals_skipsInvalid_andIsIdempotent() {
        long m = member();
        assertThat(send(m, upload(id(1), 5_000, 1_500)))
                .isEqualTo(UploadResult.stored(id(1), true));
        assertThat(send(m, upload(id(2), START.plusHours(1), 900, 300)).valid()).isFalse();
        send(m, upload(id(3), START.plusHours(2), 3_000, 900));
        assertThat(send(m, upload(id(1), 5_000, 1_500)))
                .isEqualTo(UploadResult.alreadyStored(id(1)));

        assertThat(saved()).extracting(RunRecordSaved::validRunOrdinal).containsExactly(1, 0, 2);
        RunRecordSaved first = saved().get(0);
        assertThat(first.memberId()).isEqualTo(m);
        assertThat(first.score()).isNotNull();
        assertThat(first.hasRoute()).isTrue();
        assertThat(first.occurredAt()).isEqualTo(NOW);
        assertThat(records.size()).isEqualTo(3);
        assertThat(books.lockCount()).isEqualTo(3);
    }

    @Test
    void upload_attachesWeatherWithCoarsenedPoint_orStoresWithout() {
        long m = member();
        send(m, upload(id(1), 5_000, 1_500));
        assertThat(weatherAsked).containsExactly(new GeoPoint(37.5, 127.0));
        assertThat(records.findById(id(1)).orElseThrow().getWeather())
                .isEqualTo(new Weather(12, SkyCondition.CLEAR));

        weatherFailure = new IllegalStateException("timeout");
        send(m, upload(id(2), START.plusHours(1), 5_000, 1_500));
        assertThat(records.findById(id(2)).orElseThrow().getWeather()).isNull();
    }

    @Test
    void upload_marksNewRecordsFromBook() {
        long m = member();
        send(m, upload(id(1), 5_000, 1_500));
        send(m, upload(id(2), START.plusDays(1), 5_000, 1_400));
        assertThat(records.findById(id(2)).orElseThrow().getNewRecords())
                .extracting(NewRecordMark::distance)
                .contains(StandardDistance.KM5);
    }

    @Test
    void upload_heartRatesNeedConsent() {
        long m = member();
        List<HeartRateSample> hr = List.of(new HeartRateSample(START.plusSeconds(5), 150));
        send(m, RunFixtures.withHeartRates(upload(id(1), 5_000, 1_500), hr));
        assertThat(records.findById(id(1)).orElseThrow().heartRateSummary()).isNull();

        members.findById(m).orElseThrow().giveConsent(ConsentItem.HEART_RATE, NOW);
        send(m, RunFixtures.withHeartRates(upload(id(2), START.plusHours(1), 5_000, 1_500), hr));
        assertThat(records.findById(id(2)).orElseThrow().heartRateSummary()).isNotNull();
    }

    @Test
    void appendLateHeartRates_ownerConsentAndExistence() {
        long m = member();
        long other = member();
        send(m, upload(id(1), 5_000, 1_500));
        LateHeartRateCommand late =
                new LateHeartRateCommand(
                        id(1), List.of(new HeartRateSample(START.plusSeconds(9), 140)));

        upload.appendLateHeartRates(m, late);
        assertThat(records.findById(id(1)).orElseThrow().heartRateSummary()).isNull();

        members.findById(m).orElseThrow().giveConsent(ConsentItem.HEART_RATE, NOW);
        upload.appendLateHeartRates(m, late);
        assertThat(records.findById(id(1)).orElseThrow().heartRateSummary().max()).isEqualTo(140);
        assertThat(records.findById(id(1)).orElseThrow().isValid()).isTrue();

        assertThatThrownBy(() -> upload.appendLateHeartRates(other, late))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(
                        () ->
                                upload.appendLateHeartRates(
                                        m, new LateHeartRateCommand(id(9), List.of())))
                .isInstanceOf(BusinessRuleException.class);

        records.findById(id(1)).orElseThrow().purgeHeartRates();
        upload.appendLateHeartRates(m, late);
        assertThat(records.findById(id(1)).orElseThrow().heartRateZones(200)).isEmpty();
    }

    @Test
    void disposal_consentWithdrawnSchedulesOrder_executeDuePurgesAndAudits() {
        long m = member();
        send(m, upload(id(1), 5_000, 1_500));
        disposal.onConsentWithdrawn(
                new ConsentWithdrawn(m, ConsentItem.LOCATION, NOW.minusDays(1)));
        disposal.onConsentWithdrawn(new ConsentWithdrawn(m, ConsentItem.MARKETING, NOW));
        assertThat(orders.all())
                .singleElement()
                .satisfies(
                        o -> {
                            assertThat(o.kind()).isEqualTo(DisposalKind.ROUTE);
                            assertThat(o.view().dueAt()).isEqualTo(NOW.plusDays(6));
                        });
        assertThat(disposal.pendingFor(m)).hasSize(1);

        assertThat(disposal.executeDue(NOW)).isEqualTo(1);
        RunRecord r = records.findById(id(1)).orElseThrow();
        assertThat(r.routePoints()).isEmpty();
        assertThat(r.getDistance()).isEqualTo(Distance.ofKm(5));
        assertThat(audits.all())
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.action()).isEqualTo(AuditAction.ROUTE_DISPOSAL);
                            assertThat(a.targetId()).isEqualTo(String.valueOf(m));
                            assertThat(a.before()).isEqualTo("ROUTE");
                        });
        assertThat(disposal.executeDue(NOW)).isZero();

        long orderId = disposal.pendingFor(m).get(0).id();
        disposal.confirmDevice(m, orderId);
        assertThat(disposal.pendingFor(m)).isEmpty();
        assertThat(orders.findById(orderId).orElseThrow().status())
                .isEqualTo(DisposalStatus.DEVICE_CONFIRMED);
    }

    @Test
    void disposal_heartRateOrder_andOwnership() {
        long m = member();
        long other = member();
        members.findById(m).orElseThrow().giveConsent(ConsentItem.HEART_RATE, NOW);
        send(
                m,
                RunFixtures.withHeartRates(
                        upload(id(1), 5_000, 1_500),
                        List.of(new HeartRateSample(START.plusSeconds(1), 150))));
        disposal.onConsentWithdrawn(new ConsentWithdrawn(m, ConsentItem.HEART_RATE, NOW));
        disposal.executeDue(NOW);
        RunRecord r = records.findById(id(1)).orElseThrow();
        assertThat(r.heartRateZones(200)).isEmpty();
        assertThat(r.heartRateSummary()).isNotNull();
        assertThat(r.routePoints()).isNotEmpty();
        assertThat(audits.all())
                .singleElement()
                .satisfies(a -> assertThat(a.before()).isEqualTo("HEART_RATE"));

        long orderId = orders.all().get(0).id();
        assertThatThrownBy(() -> disposal.confirmDevice(other, orderId))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> disposal.confirmDevice(m, 999))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void query_projectionsAndStats() {
        long m = member();
        long other = member();
        send(m, upload(id(1), START.minusDays(1), 5_000, 1_500));
        send(m, upload(id(2), START, 900, 300));
        send(m, upload(id(3), START.plusHours(3), 3_000, 900));
        send(other, upload(id(4), START, 10_000, 3_600));

        assertThat(query.summary(id(1)).distance()).isEqualTo(Distance.ofKm(5));
        assertThatThrownBy(() -> query.summary(id(9)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.RECORD_NOT_FOUND);
        assertThat(query.recentSummaries(m, 20))
                .extracting(RunSummary::recordId)
                .containsExactly(id(3), id(1));
        assertThat(query.runSamples(m, 1))
                .extracting(RunSampleRow::recordId)
                .containsExactly(id(3));
        DateRange today = new DateRange(START.toLocalDate(), START.toLocalDate());
        assertThat(query.runSamples(m, today)).hasSize(1);
        assertThat(query.recentStartTimes(m, 10))
                .containsExactly(START.plusHours(3), START.minusDays(1));
        assertThat(query.summariesBetween(m, today))
                .extracting(RunSummary::recordId)
                .containsExactly(id(3), id(2));
        assertThat(
                        query.dailyStats(
                                m,
                                new DateRange(
                                        START.toLocalDate().minusDays(1), START.toLocalDate())))
                .hasSize(2)
                .hasEntrySatisfying(START.toLocalDate(), d -> assertThat(d.count()).isEqualTo(1));
        assertThat(query.dailyDistances(List.of(m, other), today))
                .containsEntry(START.toLocalDate(), Distance.ofKm(13));
        assertThat(query.dailyDistances(List.of(), today)).isEmpty();
        assertThat(query.totals(m).count()).isEqualTo(3);
        assertThat(query.totals(m).validDistance()).isEqualTo(Distance.ofKm(8));
        assertThat(query.distancesByMember(List.of(m, other, 999L), today))
                .containsEntry(m, Distance.ofKm(3))
                .containsEntry(other, Distance.ofKm(10))
                .containsEntry(999L, Distance.ZERO);
        assertThat(query.validRunsReaching(StandardDistance.KM5, List.of(m, other)))
                .extracting(OwnedRun::recordId)
                .containsExactlyInAnyOrder(id(1), id(4));
        assertThat(query.validRunsReaching(StandardDistance.KM5, List.of())).isEmpty();
        assertThat(query.best(m, StandardDistance.KM3)).isPresent();
        assertThat(query.longestDistance(m)).contains(Distance.ofKm(5));
        assertThat(query.longest(999)).isEmpty();
        assertThat(query.timeToReach(id(1), Distance.ofKm(1))).isPresent();
        assertThat(query.routePoints(id(1))).hasSize(1_501);
    }

    @Test
    void query_detailComparisonCurve() {
        long m = member();
        send(m, upload(id(1), 5_000, 1_500));
        RunDetail detail = query.detail(id(1), OptionalInt.of(190));
        assertThat(detail.splitPaces()).hasSize(5);
        assertThat(detail.weather()).isNotNull();
        assertThat(detail.heartRateZones()).isEmpty();
        assertThat(query.detail(id(1), OptionalInt.empty()).fastestKm()).isNotNull();

        RunComparison cmp = query.comparisonOf(id(1));
        assertThat(cmp.elapsed()).isEqualTo(Duration.ofMinutes(25));

        RunCurve curve = query.curve(id(1), Distance.ofKm(3));
        assertThat(curve.samples().get(0).distance()).isEqualTo(Distance.ZERO);
        assertThat(curve.samples().get(curve.samples().size() - 1).distance())
                .isEqualTo(Distance.ofKm(3));
        assertThat(curve.samples()).hasSizeBetween(900, 902);

        records.findById(id(1)).orElseThrow().purgeRoute();
        assertThatThrownBy(() -> query.curve(id(1), Distance.ofKm(3)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.ROUTE_PURGED);
    }

    @Test
    void query_periodTotals_weeklyAndMonthly() {
        long m = member();
        send(m, upload(id(1), LocalDateTime.of(2026, 10, 19, 7, 0), 5_000, 1_500));
        send(m, upload(id(2), LocalDateTime.of(2026, 10, 12, 7, 0), 3_000, 900));
        send(m, upload(id(3), LocalDateTime.of(2026, 9, 1, 7, 0), 10_000, 3_600));

        List<PeriodStats> weeks = query.periodTotals(m, AggregationPeriod.WEEKLY, 8);
        assertThat(weeks).hasSize(8);
        assertThat(weeks.get(7).from()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(weeks.get(7).distance()).isEqualTo(Distance.ofKm(5));
        assertThat(weeks.get(6).count()).isEqualTo(1);
        assertThat(weeks.get(5).averagePace()).isNull();

        List<PeriodStats> months = query.periodTotals(m, AggregationPeriod.MONTHLY, 6);
        assertThat(months.get(5).distance()).isEqualTo(Distance.ofKm(8));
        assertThat(months.get(4).distance()).isEqualTo(Distance.ofKm(10));
        assertThatThrownBy(() -> query.periodTotals(m, AggregationPeriod.WEEKLY, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tierUpdateListener_forwardsValidRunsOnly_andRetriesConflicts() {
        long m = member();
        TierLevelService real =
                new TierLevelService(
                        members, new OperationPolicy(new InMemoryOperationSettingRepository()));
        TierUpdateListener listener = new TierUpdateListener(real, NO_TX);
        for (int i = 1; i <= 3; i++) {
            send(m, upload(id(i), START.plusHours(i), 5_000, 1_500));
        }
        saved().forEach(listener::onRunSaved);
        assertThat(members.findById(m).orElseThrow().getTier()).isEqualTo(Tier.GOLD);

        TierLevelService flaky = mock(TierLevelService.class);
        when(flaky.recordValidRun(anyLong(), any()))
                .thenThrow(new OptimisticLockingFailureException("x"))
                .thenThrow(new OptimisticLockingFailureException("x"))
                .thenReturn(null);
        new TierUpdateListener(flaky, NO_TX).onRunSaved(saved().get(0));
        verify(flaky, times(3)).recordValidRun(anyLong(), any(RunScoreInput.class));

        TierLevelService broken = mock(TierLevelService.class);
        when(broken.recordValidRun(anyLong(), any())).thenThrow(new IllegalStateException("탈퇴"));
        new TierUpdateListener(broken, NO_TX).onRunSaved(saved().get(0));

        TierLevelService unused = mock(TierLevelService.class);
        send(m, upload(id(9), START.plusDays(1), 900, 300));
        new TierUpdateListener(unused, NO_TX).onRunSaved(saved().get(saved().size() - 1));
        verifyNoInteractions(unused);
    }

    @Test
    void runWithdrawalListener_deletesRecordsBookAndOrders() {
        long m = member();
        long other = member();
        send(m, upload(id(1), 5_000, 1_500));
        send(other, upload(id(2), 5_000, 1_500));
        disposal.onConsentWithdrawn(new ConsentWithdrawn(m, ConsentItem.LOCATION, NOW));

        new RunWithdrawalListener(records, books, orders)
                .onMemberWithdrawn(new MemberWithdrawn(m, NOW));

        assertThat(records.findByMember(m)).isEmpty();
        assertThat(records.findByMember(other)).hasSize(1);
        assertThat(books.has(m)).isFalse();
        assertThat(books.has(other)).isTrue();
        assertThat(orders.all()).isEmpty();
    }
}
