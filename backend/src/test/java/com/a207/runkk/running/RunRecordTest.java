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
import java.util.Map;
import org.junit.jupiter.api.Test;

/** RunRecord(6.1.3): 수신 검증, judgeValidity(한 번만), 신기록·날씨·심박·파기. PersonalRecordBook.apply(A-05). */
@org.junit.jupiter.api.DisplayName("러닝 기록·최고 기록·파기 지시 엔티티")
class RunRecordTest {

    @Test
    void lookProjectionUsesOneValueAndPreservesEveryColumn() {
        assertThat(
                        java.util.Arrays.stream(RunRecord.class.getDeclaredMethods())
                                .filter(method -> method.getName().equals("lookOf")))
                .singleElement()
                .satisfies(method -> assertThat(method.getParameterCount()).isEqualTo(1));
        RunRecord record = RunFixtures.valid5k(id(1));
        var look =
                new com.a207.runkk.common.AvatarLook(
                        "FEMALE",
                        Map.of(
                                com.a207.runkk.common.ItemSlot.TOP,
                                11L,
                                com.a207.runkk.common.ItemSlot.BOTTOM,
                                22L,
                                com.a207.runkk.common.ItemSlot.SHOES,
                                33L),
                        4,
                        99,
                        "head:1:old");
        record.attachLook(look);
        assertThat(record.look()).contains(look);
    }

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

    private static RunUpload startedOn(RunUpload u, StartDevice device) {
        return new RunUpload(
                u.recordId(),
                u.startedAt(),
                u.endedAt(),
                u.distance(),
                u.elapsed(),
                u.mode(),
                u.cardKind(),
                u.battleId(),
                u.crewRunPostId(),
                u.declaredPointCount(),
                u.points(),
                u.unstableSegments(),
                u.heartRates(),
                u.pauses(),
                device);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("W23·D-378: 시작 기기는 보낸 값으로 저장하고, 없으면(옛 앱) PHONE이다")
    void receive_keepsStartDevice() {
        RunUpload phone = upload(id(1), 5_000, 1_500);
        assertThat(phone.startDevice()).isEqualTo(StartDevice.PHONE);
        assertThat(startedOn(phone, null).startDevice()).isEqualTo(StartDevice.PHONE);
        assertThat(RunRecord.receive(1, phone).getStartDevice()).isEqualTo(StartDevice.PHONE);

        RunUpload watch = startedOn(phone, StartDevice.WATCH);
        assertThat(RunRecord.receive(1, watch).getStartDevice()).isEqualTo(StartDevice.WATCH);
        assertThat(watch.withoutHeartRates().startDevice())
                .as("심박 동의가 없어 심박을 뺀 사본도 시작 기기를 지킨다")
                .isEqualTo(StartDevice.WATCH);
    }

    private static RunUpload withMotions(RunUpload u, List<MotionSample> motions) {
        return new RunUpload(
                u.recordId(),
                u.startedAt(),
                u.endedAt(),
                u.distance(),
                u.elapsed(),
                u.mode(),
                u.cardKind(),
                u.battleId(),
                u.crewRunPostId(),
                u.declaredPointCount(),
                u.points(),
                u.unstableSegments(),
                u.heartRates(),
                u.pauses(),
                u.startDevice(),
                motions);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "S15P31A207-242: 속도·케이던스 표본은 시간순으로 저장하고 같은 일시는 합치며, 경로 파기 때 함께 지운다")
    void receive_storesMotions_andRoutePurgeClearsThem() {
        RunUpload base = upload(id(1), 5_000, 1_500);
        assertThat(base.motions()).as("옛 앱 형식은 빈 목록").isEmpty();
        assertThat(RunRecord.receive(1, base).motionSamples()).isEmpty();

        List<MotionSample> motions =
                List.of(
                        new MotionSample(START.plusSeconds(2), 3.25, null),
                        new MotionSample(START.plusSeconds(1), null, 170.0),
                        new MotionSample(START.plusSeconds(2), 9.0, 168.5),
                        new MotionSample(START.plusNanos(1_500_000_000L), 3.0, null));
        RunRecord record = RunRecord.receive(1, withMotions(base, motions));

        assertThat(record.motionSamples())
                .containsExactly(
                        new MotionSample(START.plusSeconds(1), null, 170.0),
                        new MotionSample(START.plusNanos(1_500_000_000L), 3.0, null),
                        new MotionSample(START.plusSeconds(2), 3.25, 168.5));
        assertThat(withMotions(base, motions).withoutHeartRates().motions())
                .as("심박을 뺀 사본도 움직임 표본을 지킨다")
                .hasSize(4);

        record.purgeRoute();
        assertThat(record.motionSamples()).isEmpty();
    }

    @Test
    void motionSample_requiresAValue_andRejectsNegative() {
        assertThatThrownBy(() -> new MotionSample(START, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MotionSample(START, -1.0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MotionSample(START, null, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
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
    @org.junit.jupiter.api.DisplayName(
            "2026-10-30 결정: 친구 대결 기록(BATTLE·BATTLE_RUN·대결 ID)은 같은 경로의 자유런과 유효 판정·점수·거리가 같다")
    void judgeValidity_friendBattleRunLikeFreeRun() {
        RunUpload free = upload(id(1), 5_000, 1_500);
        RunRecord freeRun = RunFixtures.judged(free);
        RunRecord battleRun =
                RunFixtures.judged(with(free, RunMode.BATTLE, CardKind.BATTLE_RUN, 12L, null));
        assertThat(battleRun.isValid()).isTrue().isEqualTo(freeRun.isValid());
        assertThat(battleRun.getScore().value()).isEqualTo(freeRun.getScore().value());
        assertThat(battleRun.getDistance()).isEqualTo(freeRun.getDistance());
        assertThat(battleRun.getElapsed()).isEqualTo(freeRun.getElapsed());
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
    @org.junit.jupiter.api.DisplayName(
            "BR-1: 멈춘 크루런 기록도 유효 판정·도달 시간에서 정지 시간을 뺀다(2km 10:00 운동 + 2:00 정지)")
    void crewRunPauses_excludedFromReachAndValidity() {
        RunRecord r =
                RunFixtures.judged(
                        RunFixtures.pausedCrewRun(
                                id(1), START, 9L, 2_000, 600, new RunFixtures.Pause(300, 120)));
        assertThat(r.getMode()).isEqualTo(RunMode.CREW_RUN);
        assertThat(r.isValid()).isTrue();
        assertThat(r.pausedTotal()).isEqualTo(Duration.ofMinutes(2));
        assertThat(r.getElapsed()).isEqualTo(Duration.ofMinutes(10));
        assertThat(r.timeToArrive(Distance.ofKm(2)).orElseThrow().toSeconds())
                .isCloseTo(600, within(1L));
        assertThat(r.timeToReach(Distance.ofKm(1)).orElseThrow().toSeconds())
                .isCloseTo(300, within(1L));
        assertThat(r.timeToReach(Distance.ofMeters(1_500)).orElseThrow().toSeconds())
                .as("정지 뒤 지점도 운동 시간으로 잰다")
                .isCloseTo(450, within(1L));
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
