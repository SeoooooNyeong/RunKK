package com.a207.runkk.infra.running;

import static com.a207.runkk.running.RunFixtures.START;
import static com.a207.runkk.running.RunFixtures.id;
import static com.a207.runkk.running.RunFixtures.upload;
import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.running.BestEntry;
import com.a207.runkk.running.DisposalKind;
import com.a207.runkk.running.DisposalOrder;
import com.a207.runkk.running.HeartRateSample;
import com.a207.runkk.running.MotionSample;
import com.a207.runkk.running.NewRecordMark;
import com.a207.runkk.running.PauseInterval;
import com.a207.runkk.running.PersonalRecordBook;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunRecord;
import com.a207.runkk.running.RunSummary;
import com.a207.runkk.running.RunUpload;
import com.a207.runkk.running.SkyCondition;
import com.a207.runkk.running.ValidRunScore;
import com.a207.runkk.running.Weather;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 14장 순서 2: infra.running을 실제 PostgreSQL(Flyway V5)로 확인한다. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@org.junit.jupiter.api.DisplayName("러닝 기록·최고 기록·파기 지시 저장소(PostgreSQL)")
class JpaRunningRepositoriesIntegrationTest {

    @Autowired JpaRunRecordRepository records;
    @Autowired JpaPersonalRecordBookRepository books;
    @Autowired JpaDisposalOrderRepository orders;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired RunQueryService runQuery;

    @BeforeEach
    void clean() {
        jdbc.update("delete from personal_best");
        jdbc.update("delete from personal_record_book");
        jdbc.update("delete from run_record");
        jdbc.update("delete from disposal_order");
    }

    private void store(RunRecord r) {
        if (r.getSavedAt() == null) {
            r.markSaved(START.plusHours(1));
        }
        tx.executeWithoutResult(s -> records.save(r));
    }

    @Test
    void recordRoundTrip_keepsTrackWeatherMarksAndHeartRates() {
        RunRecord r =
                RunFixtures.judged(
                        RunFixtures.withHeartRates(
                                upload(id(1), 5_000, 1_500),
                                List.of(new HeartRateSample(START.plusSeconds(3), 150))));
        r.attachWeather(new Weather(8, SkyCondition.RAIN));
        r.markNewRecords(
                List.of(
                        new NewRecordMark(
                                NewRecordMark.Kind.BASE_DISTANCE, StandardDistance.KM5, "25:10"),
                        new NewRecordMark(NewRecordMark.Kind.LONGEST, null, "직전 4.00 km")));
        store(r);

        tx.executeWithoutResult(
                s -> {
                    RunRecord loaded = records.findById(id(1)).orElseThrow();
                    assertThat(loaded.routePoints()).hasSize(1_501);
                    assertThat(loaded.getWeather()).isEqualTo(new Weather(8, SkyCondition.RAIN));
                    assertThat(loaded.getNewRecords()).hasSize(2);
                    assertThat(loaded.heartRateZones(200)).isNotEmpty();
                    assertThat(loaded.timeToReach(Distance.ofKm(5))).isPresent();
                    loaded.purgeRoute();
                    loaded.purgeHeartRates();
                });
        assertThat(
                        jdbc.queryForObject(
                                "select route_data is null and route_purged and heart_rate_data is"
                                        + " null and heart_rate_max = 150 from run_record",
                                Boolean.class))
                .isTrue();
        assertThat(records.validRunsReaching(Distance.ofKm(5), List.of(1L))).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("V41: 속도·케이던스(motion_data) 왕복, 옛 앱 기록은 NULL, 경로 파기 때 함께 지운다")
    void motionsRoundTrip_andRoutePurgeClearsThem() {
        List<MotionSample> motions =
                List.of(
                        new MotionSample(START.plusSeconds(1), 3.41, null),
                        new MotionSample(START.plusSeconds(2), null, 171.5),
                        new MotionSample(START.plusSeconds(3), 3.5, 172.0));
        store(RunFixtures.judged(RunFixtures.withMotions(upload(id(1), 5_000, 1_500), motions)));
        store(RunFixtures.judged(upload(id(2), START.plusHours(1), 5_000, 1_500)));

        assertThat(runQuery.bodyInputOf(id(1)).orElseThrow().motions()).isEqualTo(motions);
        assertThat(runQuery.bodyInputOf(id(2)).orElseThrow().motions()).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from run_record where motion_data is null",
                                Long.class))
                .isEqualTo(1L);

        tx.executeWithoutResult(st -> records.findById(id(1)).orElseThrow().purgeRoute());
        assertThat(runQuery.bodyInputOf(id(1)).orElseThrow().motions()).isEmpty();
    }

    @Test
    void projectionsAndStatistics() {
        store(RunFixtures.judged(upload(id(1), START.minusDays(1), 5_000, 1_500)));
        store(RunFixtures.judged(upload(id(2), START, 900, 300)));
        store(RunFixtures.judged(upload(id(3), START.plusHours(2), 3_000, 900)));
        DateRange today = new DateRange(START.toLocalDate(), START.toLocalDate());
        DateRange twoDays = new DateRange(START.toLocalDate().minusDays(1), START.toLocalDate());

        assertThat(records.exists(id(1))).isTrue();
        assertThat(records.countValid(1)).isEqualTo(2);
        assertThat(records.findSummary(id(1)))
                .map(RunSummary::elapsed)
                .contains(Duration.ofMinutes(25));
        assertThat(records.summariesBetween(1, today))
                .extracting(RunSummary::recordId)
                .containsExactly(id(3), id(2));
        assertThat(records.recentValidSummaries(1, 1))
                .extracting(RunSummary::recordId)
                .containsExactly(id(3));
        assertThat(records.recentValidSamples(1, 5)).hasSize(2);
        assertThat(records.validSamplesBetween(1, today)).hasSize(1);
        assertThat(records.recentValid(1, 5)).hasSize(2);
        assertThat(records.findByMemberBetween(1, twoDays)).hasSize(3);
        assertThat(records.dailyStats(1, twoDays))
                .hasSize(2)
                .hasEntrySatisfying(
                        START.toLocalDate(),
                        d -> assertThat(d.distance()).isEqualTo(Distance.ofKm(3)));
        assertThat(records.dailyDistances(List.of(1L, 2L), twoDays))
                .containsEntry(START.toLocalDate().minusDays(1), Distance.ofKm(5));
        assertThat(records.totals(1).count()).isEqualTo(2);
        assertThat(records.totals(1).distance()).isEqualTo(Distance.ofKm(8));
        assertThat(records.totals(99).count()).isZero();
        assertThat(records.distancesByMember(List.of(1L, 2L), twoDays))
                .containsOnlyKeys(1L)
                .containsEntry(1L, Distance.ofKm(8));
        assertThat(records.validRunsReaching(Distance.ofKm(5), List.of(1L))).hasSize(1);

        tx.executeWithoutResult(s -> records.deleteByMember(1));
        assertThat(records.findByMember(1)).isEmpty();
    }

    @Test
    void recordBook_lockCreatesRow_andRoundTrips() {
        tx.executeWithoutResult(
                s -> {
                    PersonalRecordBook book = books.findOrCreateForUpdate(1);
                    book.apply(RunFixtures.valid5k(id(1)));
                    books.save(book);
                });
        tx.executeWithoutResult(s -> books.findOrCreateForUpdate(1));
        PersonalRecordBook loaded = tx.execute(s -> books.findOrCreate(1));
        assertThat(loaded.best(StandardDistance.KM5)).map(BestEntry::recordId).contains(id(1));
        assertThat(loaded.longest().flatMap(BestEntry::distance)).contains(Distance.ofKm(5));
        assertThat(jdbc.queryForObject("select count(*) from personal_best", Integer.class))
                .isEqualTo(3);
        assertThat(books.findOrCreate(2).longest()).isEmpty();

        tx.executeWithoutResult(s -> books.deleteByMember(1));
        assertThat(jdbc.queryForObject("select count(*) from personal_record_book", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from personal_best", Integer.class))
                .isZero();
    }

    @Test
    void disposalOrders() {
        tx.executeWithoutResult(
                s -> {
                    orders.save(DisposalOrder.schedule(1, DisposalKind.ROUTE, START));
                    orders.save(DisposalOrder.schedule(2, DisposalKind.HEART_RATE, START));
                });
        assertThat(orders.findScheduled()).hasSize(2);
        long id =
                jdbc.queryForObject(
                        "select id from disposal_order where member_id = 1", Long.class);
        tx.executeWithoutResult(s -> orders.findById(id).orElseThrow().execute(START.plusDays(1)));
        assertThat(orders.findScheduled()).hasSize(1);
        assertThat(orders.findNotConfirmedByMember(1)).hasSize(1);
        tx.executeWithoutResult(
                s -> orders.findById(id).orElseThrow().confirmDevice(START.plusDays(2)));
        assertThat(orders.findNotConfirmedByMember(1)).isEmpty();
        tx.executeWithoutResult(s -> orders.deleteByMember(1));
        assertThat(orders.findById(id)).isEmpty();
        assertThat(LocalDate.of(2026, 10, 20)).isEqualTo(START.toLocalDate());
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "V21: 정지 구간(pauses·paused_millis) 왕복, validRunsAfter는 저장 일시·기록 ID 순 순번(D-108)")
    void pausesRoundTrip_andValidRunsAfter() {
        RunUpload base = upload(id(1), 5_000, 1_800);
        RunUpload paused =
                new RunUpload(
                        base.recordId(),
                        base.startedAt(),
                        base.endedAt().plusMinutes(5),
                        base.distance(),
                        base.elapsed(),
                        base.mode(),
                        base.cardKind(),
                        null,
                        null,
                        base.declaredPointCount(),
                        base.points(),
                        List.of(),
                        List.of(),
                        List.of(
                                new PauseInterval(START.plusMinutes(31), START.plusMinutes(33)),
                                new PauseInterval(
                                        START.plusMinutes(33).plusNanos(500_000_000),
                                        START.plusMinutes(34))));
        RunRecord first = RunFixtures.judged(paused);
        first.markSaved(START.plusHours(1));
        store(first);
        RunRecord invalid = RunFixtures.judged(upload(id(2), START.plusHours(1), 900, 300));
        invalid.markSaved(START.plusHours(2));
        store(invalid);
        RunRecord third = RunFixtures.judged(upload(id(3), START.plusHours(2), 3_000, 900));
        third.markSaved(START.plusHours(3));
        store(third);

        RunRecord loaded = tx.execute(st -> records.findById(id(1)).orElseThrow());
        assertThat(loaded.pauses()).isEqualTo(paused.pauses());
        assertThat(loaded.pausedTotal()).isEqualTo(Duration.ofMillis(179_500));
        assertThat(
                        jdbc.queryForObject(
                                "select paused_millis from run_record where record_id = ?",
                                Long.class,
                                id(1)))
                .isEqualTo(179_500L);

        List<ValidRunScore> after = tx.execute(st -> records.validRunsAfter(1L, 1));
        assertThat(after).extracting(ValidRunScore::ordinal).containsExactly(2);
        assertThat(after.get(0).distance()).isEqualTo(Distance.ofKm(3));
        assertThat(after.get(0).score()).isEqualTo(third.getScore().value());
        List<ValidRunScore> all = tx.execute(st -> records.validRunsAfter(1L, 0));
        assertThat(all).extracting(ValidRunScore::ordinal).containsExactly(1, 2);
        List<ValidRunScore> none = tx.execute(st -> records.validRunsAfter(2L, 0));
        assertThat(none).isEmpty();
    }
}
