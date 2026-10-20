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
import com.a207.runkk.running.NewRecordMark;
import com.a207.runkk.running.PersonalRecordBook;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.running.RunRecord;
import com.a207.runkk.running.RunSummary;
import com.a207.runkk.running.SkyCondition;
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

    @BeforeEach
    void clean() {
        jdbc.update("delete from personal_best");
        jdbc.update("delete from personal_record_book");
        jdbc.update("delete from run_record");
        jdbc.update("delete from disposal_order");
    }

    private void store(RunRecord r) {
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
        assertThat(records.totals(1).count()).isEqualTo(3);
        assertThat(records.totals(1).validDistance()).isEqualTo(Distance.ofKm(8));
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
}
