package com.a207.runkk.running;

import static com.a207.runkk.running.RunFixtures.START;
import static com.a207.runkk.running.RunFixtures.id;
import static com.a207.runkk.running.RunFixtures.upload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.StandardDistance;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** RunRecord(6.1.3): 수신 검증, judgeValidity(한 번만), 신기록·날씨·심박·파기. PersonalRecordBook.apply(A-05). */
@org.junit.jupiter.api.DisplayName("러닝 기록·최고 기록·파기 지시 엔티티")
class RunRecordTest {

    private static RunUpload with(
            RunUpload u, RunMode mode, CardKind kind, Long battle, Long post) {
        return new RunUpload(
                u.recordId(),
                u.startedAt(),
                u.endedAt(),
                u.distance(),
                u.elapsed(),
                mode,
                kind,
                battle,
                post,
                u.declaredPointCount(),
                u.points(),
                u.unstableSegments(),
                u.heartRates());
    }

    @Test
    void receive_validatesInput() {
        RunUpload ok = upload(id(1), 5_000, 1_500);
        assertThat(RunRecord.receive(1, ok).getRecordId()).isEqualTo(id(1));

        RunUpload notUuid = upload("abc", 5_000, 1_500);
        assertThatThrownBy(() -> RunRecord.receive(1, notUuid))
                .isInstanceOf(IllegalArgumentException.class);
        RunUpload countMismatch =
                new RunUpload(
                        ok.recordId(),
                        ok.startedAt(),
                        ok.endedAt(),
                        ok.distance(),
                        ok.elapsed(),
                        ok.mode(),
                        ok.cardKind(),
                        null,
                        null,
                        ok.declaredPointCount() + 1,
                        ok.points(),
                        List.of(),
                        List.of());
        assertThatThrownBy(() -> RunRecord.receive(1, countMismatch))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                RunRecord.receive(
                                        1, with(ok, RunMode.FREE, CardKind.BATTLE_RUN, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                RunRecord.receive(
                                        1, with(ok, RunMode.BATTLE, CardKind.SELF_RUN, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                RunRecord.receive(
                                        1, with(ok, RunMode.FREE, CardKind.FREE_RUN, 5L, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(
                        RunRecord.receive(1, with(ok, RunMode.BATTLE, CardKind.CELEB_RUN, 5L, null))
                                .getCardKind())
                .isEqualTo(CardKind.CELEB_RUN);
        assertThat(
                        RunRecord.receive(
                                        1, with(ok, RunMode.CREW_RUN, CardKind.CREW_RUN, null, 9L))
                                .getMode())
                .isEqualTo(RunMode.CREW_RUN);
        assertThatThrownBy(() -> RunRecord.receive(1, upload(id(2), 0, 100)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void judgeValidity_validRunGetsScore_onlyOnce() {
        RunRecord r = RunRecord.receive(1, upload(id(1), 5_000, 1_500));
        assertThat(r.judgeValidity(ValidityRule.STANDARD)).isTrue();
        assertThat(r.getScore().value()).isCloseTo(38.3, within(0.05));
        assertThat(r.getAveragePace().secondsPerKm()).isEqualTo(300);
        assertThatThrownBy(() -> r.judgeValidity(ValidityRule.STANDARD))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void judgeValidity_invalidRuns() {
        assertThat(RunFixtures.judged(upload(id(1), 900, 300)).isValid()).isFalse();
        assertThat(RunFixtures.judged(upload(id(2), 1_000, 170)).isValid()).isFalse();
        RunRecord tooFast = RunFixtures.judged(upload(id(3), 5_000, 600));
        assertThat(tooFast.isValid()).isFalse();
        assertThat(tooFast.getScore()).isNull();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "L18: 선언 거리가 경로 길이의 두 배를 넘거나 선언 시간이 시작~종료보다 길면 다른 조건을 다 맞아도 무효")
    void judgeValidity_declaredValuesMustMatchTrack() {
        RunUpload marathon = upload(id(1), 42_195, 14_400);
        RunUpload twoPoints =
                new RunUpload(
                        marathon.recordId(),
                        marathon.startedAt(),
                        marathon.endedAt(),
                        marathon.distance(),
                        marathon.elapsed(),
                        marathon.mode(),
                        marathon.cardKind(),
                        null,
                        null,
                        2,
                        marathon.points().subList(0, 2),
                        List.of(),
                        List.of());
        assertThat(RunFixtures.judged(twoPoints).isValid()).isFalse();

        RunUpload fiveK = upload(id(2), 5_000, 1_500);
        RunUpload stretched =
                new RunUpload(
                        fiveK.recordId(),
                        fiveK.startedAt(),
                        fiveK.startedAt().plusSeconds(1_200),
                        fiveK.distance(),
                        fiveK.elapsed(),
                        fiveK.mode(),
                        fiveK.cardKind(),
                        null,
                        null,
                        fiveK.declaredPointCount(),
                        fiveK.points(),
                        List.of(),
                        List.of());
        assertThat(RunFixtures.judged(stretched).isValid()).isFalse();
        assertThat(RunFixtures.judged(upload(id(3), 5_000, 1_500)).isValid()).isTrue();
    }

    @Test
    void markNewRecords_ignoredWhenInvalid() {
        RunRecord invalid = RunFixtures.judged(upload(id(1), 900, 300));
        invalid.markNewRecords(
                List.of(new NewRecordMark(NewRecordMark.Kind.LONGEST, null, "직전 1.00 km")));
        assertThat(invalid.getNewRecords()).isEmpty();

        RunRecord valid = RunFixtures.valid5k(id(2));
        NewRecordMark mark =
                new NewRecordMark(NewRecordMark.Kind.BASE_DISTANCE, StandardDistance.KM1, "5:10");
        valid.markNewRecords(
                List.of(mark, new NewRecordMark(NewRecordMark.Kind.LONGEST, null, "직전 3.00 km")));
        assertThat(valid.getNewRecords()).hasSize(2).first().isEqualTo(mark);
    }

    @Test
    void splitPacesAndFastestKm() {
        RunRecord r = RunFixtures.valid5k(id(1));
        assertThat(r.splitPaces())
                .hasSize(5)
                .allSatisfy(p -> assertThat(p.secondsPerKm()).isCloseTo(300, within(1)));
        assertThat(r.fastestKm()).isPresent();
        assertThat(r.timeToReach(Distance.ofKm(5)).orElseThrow().toSeconds())
                .isCloseTo(1500, within(1L));
        assertThat(r.reached(Distance.ofKm(5))).isTrue();
        assertThat(r.reached(Distance.ofKm(10))).isFalse();
        assertThat(r.routePoints()).hasSize(1_501);
    }

    @Test
    void weatherAndHeartRates() {
        RunRecord r = RunFixtures.valid5k(id(1));
        r.attachWeather(null);
        assertThat(r.getWeather()).isNull();
        r.attachWeather(new Weather(12, SkyCondition.CLEAR));
        assertThat(r.getWeather()).isEqualTo(new Weather(12, SkyCondition.CLEAR));

        r.appendHeartRates(List.of(new HeartRateSample(START.plusSeconds(1), 150)));
        r.appendHeartRates(List.of(new HeartRateSample(START.plusSeconds(2), 170)));
        assertThat(r.heartRateZones(200)).isNotEmpty();
        assertThat(r.heartRateSummary()).isEqualTo(new HeartRateSummary(160, 170));
        r.appendHeartRates(List.of(new HeartRateSample(START.plusSeconds(2), 170)));
        assertThat(r.heartRateSummary()).isEqualTo(new HeartRateSummary(160, 170));
        assertThat(r.isValid()).isTrue();

        r.purgeHeartRates();
        assertThat(r.heartRateZones(200)).isEmpty();
        assertThat(r.heartRateSummary()).isEqualTo(new HeartRateSummary(160, 170));
    }

    @Test
    void purgeRoute_keepsSummaryValues() {
        RunRecord r = RunFixtures.valid5k(id(1));
        assertThat(r.hasRoute()).isTrue();
        r.purgeRoute();
        assertThat(r.hasRoute()).isFalse();
        assertThat(r.routePoints()).isEmpty();
        assertThat(r.timeToReach(Distance.ofKm(1))).isEmpty();
        assertThat(r.splitPaces()).isEmpty();
        assertThat(r.getDistance()).isEqualTo(Distance.ofKm(5));
        assertThat(r.getElapsed()).isEqualTo(Duration.ofMinutes(25));
        assertThat(r.getAveragePace().secondsPerKm()).isEqualTo(300);
    }

    @Test
    void personalRecordBook_firstRecordNoMarks_thenImprovements() {
        PersonalRecordBook book = PersonalRecordBook.of(1);
        assertThat(book.apply(RunFixtures.valid5k(id(1)))).isEmpty();
        assertThat(book.best(StandardDistance.KM5)).isPresent();
        assertThat(book.best(StandardDistance.KM10)).isEmpty();
        assertThat(book.longest().flatMap(BestEntry::distance)).contains(Distance.ofKm(5));

        RunRecord faster3k = RunFixtures.judged(upload(id(2), START.plusDays(1), 3_000, 840));
        List<NewRecordMark> marks = book.apply(faster3k);
        assertThat(marks)
                .extracting(NewRecordMark::distance)
                .containsExactly(StandardDistance.KM1, StandardDistance.KM3);
        assertThat(marks.get(0).previous()).isEqualTo("5:00");
        assertThat(book.best(StandardDistance.KM1).orElseThrow().recordId()).isEqualTo(id(2));
        assertThat(book.best(StandardDistance.KM5).orElseThrow().recordId()).isEqualTo(id(1));

        RunRecord longer = RunFixtures.judged(upload(id(3), START.plusDays(2), 10_000, 3_600));
        List<NewRecordMark> longMarks = book.apply(longer);
        assertThat(longMarks)
                .filteredOn(m -> m.kind() == NewRecordMark.Kind.LONGEST)
                .singleElement()
                .satisfies(m -> assertThat(m.previous()).isEqualTo("직전 5.00 km"));
        assertThat(book.best(StandardDistance.KM10).orElseThrow().time())
                .hasValueSatisfying(t -> assertThat(t.toSeconds()).isCloseTo(3600, within(1L)));

        assertThat(book.apply(RunFixtures.judged(upload(id(4), 900, 300)))).isEmpty();
        assertThat(book.best(StandardDistance.FULL)).as("풀은 v0.14부터 최고 기록 장부 거리다(D-238)").isEmpty();
    }

    @Test
    void disposalOrder_stateTransitions() {
        DisposalOrder a = DisposalOrder.schedule(1, DisposalKind.ROUTE, START);
        assertThat(a.isDue(START)).isTrue();
        assertThat(a.isOverdue(START.plusDays(7))).isFalse();
        assertThat(a.isOverdue(START.plusDays(7).plusSeconds(1))).isTrue();
        a.execute(START.plusDays(1));
        assertThat(a.status()).isEqualTo(DisposalStatus.EXECUTED);
        assertThat(a.isDue(START.plusDays(2))).isFalse();
        assertThatThrownBy(() -> a.execute(START.plusDays(2)))
                .isInstanceOf(IllegalStateException.class);
        a.confirmDevice(START.plusDays(2));
        assertThat(a.status()).isEqualTo(DisposalStatus.DEVICE_CONFIRMED);
        assertThatThrownBy(() -> a.confirmDevice(START.plusDays(3)))
                .isInstanceOf(IllegalStateException.class);

        DisposalOrder b = DisposalOrder.schedule(1, DisposalKind.HEART_RATE, START);
        b.confirmDevice(START.plusHours(1));
        assertThat(b.status()).isEqualTo(DisposalStatus.SCHEDULED);
        b.execute(START.plusDays(1));
        assertThat(b.status()).isEqualTo(DisposalStatus.DEVICE_CONFIRMED);
        assertThatThrownBy(() -> DisposalOrder.schedule(1, null, START))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
