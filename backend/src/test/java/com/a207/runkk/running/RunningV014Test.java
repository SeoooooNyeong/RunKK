package com.a207.runkk.running;

import static com.a207.runkk.running.RunFixtures.START;
import static com.a207.runkk.running.RunFixtures.id;
import static com.a207.runkk.running.RunFixtures.upload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.DomainEvent;
import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.EvaluationKind;
import com.a207.runkk.member.InMemoryMemberRepository;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.TierEvaluation;
import com.a207.runkk.member.TierProgress;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * v0.14 running 변경(D-231·D-232 기록 삭제·묘비, D-234 경로 모양, D-291·D-294 평가 표시, D-238 풀 최고 기록). test.md
 * 12.2.4 TC-9341~TC-9365, 기초 경로 TC-9513~TC-9520, 12.2.5 TC-9417~TC-9425.
 */
@DisplayName("v0.14 러닝: 기록 삭제·묘비·경로 모양·평가 표시")
class RunningV014Test {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 12, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    private final InMemoryRunningRepositories.Records records =
            new InMemoryRunningRepositories.Records();
    private final InMemoryRunningRepositories.Books books = new InMemoryRunningRepositories.Books();
    private final InMemoryMemberRepository members = new InMemoryMemberRepository();
    private final List<DomainEvent> published = new ArrayList<>();
    private final RunUploadService uploads =
            new RunUploadService(
                    records,
                    books,
                    (point, at) -> Optional.empty(),
                    new MemberQueryService(members),
                    memberId -> java.util.Optional.empty(),
                    published::add,
                    RunningServicesTest.NO_TX,
                    clock);
    private final RunDeletionService deletion =
            new RunDeletionService(records, published::add, clock);
    private final RunQueryService query = new RunQueryService(records, books, clock);

    private long member() {
        return members.save(MemberFixtures.member("러너" + (members.size() + 10))).getId();
    }

    private UploadResult send(long memberId, RunUpload u) {
        return uploads.upload(memberId, new RunUploadCommand(u));
    }

    /** 집계 제외(무효) 기록: 900m. */
    private String invalidRun(long memberId, int n) {
        send(memberId, upload(id(n), START.plusHours(n), 900, 300));
        assertThat(records.findById(id(n)).orElseThrow().isValid()).isFalse();
        return id(n);
    }

    private static void assertReason(Runnable call, ReasonCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(code);
    }

    @ParameterizedTest(name = "TC-9341 재판정 표시 {0}")
    @EnumSource(RejudgeState.class)
    @DisplayName("TC-9341·TC-9517 유효 기록은 재판정 표시와 상관없이 삭제할 수 없다")
    void tc9341_validNotDeletable(RejudgeState state) {
        RunRecord valid = RunFixtures.valid5k(id(1));
        assertThat(valid.isDeletable()).isFalse();
        valid.markRejudgeState(state);
        assertThat(valid.isDeletable()).isFalse();
    }

    @ParameterizedTest(name = "TC-9342~9345 무효, 재판정 표시 {0} → {1}")
    @CsvSource({",true", "PENDING,false", "VALID,false", "DONE,true", "SKIPPED,true"})
    @DisplayName("TC-9342~TC-9345·TC-9518~TC-9520 무효 기록은 재판정 PENDING·VALID가 아니면 삭제할 수 있다")
    void tc9342_invalidDeletable(RejudgeState state, boolean deletable) {
        long m = member();
        RunRecord record = records.findById(invalidRun(m, 2)).orElseThrow();
        record.markRejudgeState(state);
        assertThat(record.isDeletable()).isEqualTo(deletable);
        assertThat(query.detail(record.getRecordId(), java.util.OptionalInt.empty()).deletable())
                .isEqualTo(deletable);
    }

    @Test
    @DisplayName("TC-9346·TC-9516 본인의 집계 제외 기록: 삭제·묘비·RunRecordDeleted(대결·크루런 ID 포함) 1회")
    void tc9346_delete() {
        long m = member();
        String recordId = invalidRun(m, 2);
        published.clear();

        deletion.delete(m, recordId);

        assertThat(records.findById(recordId)).isEmpty();
        assertThat(records.isBuried(recordId)).isTrue();
        assertThat(records.tombstoneCount()).isEqualTo(1);
        assertThat(published).hasSize(1);
        RunRecordDeleted event = (RunRecordDeleted) published.get(0);
        assertThat(event.recordId()).isEqualTo(recordId);
        assertThat(event.memberId()).isEqualTo(m);
        assertThat(event.at()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("TC-9347·TC-9348·TC-9515 유효 기록·재판정 대기 기록 → RECORD_VALID_NOT_DELETABLE, 변화 0")
    void tc9347_notDeletable() {
        long m = member();
        send(m, upload(id(1), 5_000, 1_500));
        assertReason(() -> deletion.delete(m, id(1)), ReasonCode.RECORD_VALID_NOT_DELETABLE);
        String pending = invalidRun(m, 2);
        records.findById(pending).orElseThrow().markRejudgeState(RejudgeState.PENDING);
        assertReason(() -> deletion.delete(m, pending), ReasonCode.RECORD_VALID_NOT_DELETABLE);
        assertThat(records.findById(id(1))).isPresent();
        assertThat(records.findById(pending)).isPresent();
        assertThat(records.tombstoneCount()).isZero();
    }

    @Test
    @DisplayName("TC-9349·TC-9350·TC-9514 남의 기록은 유효 여부와 상관없이 403(주인 검사가 먼저)")
    void tc9349_othersRecord() {
        long owner = member();
        long other = member();
        String invalid = invalidRun(owner, 2);
        send(owner, upload(id(1), 5_000, 1_500));
        assertThatThrownBy(() -> deletion.delete(other, invalid))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> deletion.delete(other, id(1)))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(records.findById(invalid)).isPresent();
    }

    @Test
    @DisplayName("TC-9351·TC-9513 없는 기록·이미 지운 기록 → RECORD_NOT_FOUND")
    void tc9351_notFound() {
        long m = member();
        assertReason(() -> deletion.delete(m, id(9)), ReasonCode.RECORD_NOT_FOUND);
        String recordId = invalidRun(m, 2);
        deletion.delete(m, recordId);
        assertReason(() -> deletion.delete(m, recordId), ReasonCode.RECORD_NOT_FOUND);
        assertThat(records.tombstoneCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-9353 묘비 저장이 실패하면 사건을 내지 않는다(트랜잭션이 롤백한다)")
    void tc9353_buryFailure() {
        long m = member();
        String recordId = invalidRun(m, 2);
        published.clear();
        records.nextBuryFailure = new IllegalStateException("db down");
        assertThatThrownBy(() -> deletion.delete(m, recordId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("TC-9354·TC-9355 묘비가 있는 ID 재업로드는 alreadyStored·저장 0·사건 0, 한 글자 다른 ID는 정상 저장")
    void tc9354_tombstoneBlocksReupload() {
        long m = member();
        String recordId = invalidRun(m, 2);
        deletion.delete(m, recordId);
        published.clear();

        assertThat(send(m, upload(recordId, START.plusHours(2), 900, 300)))
                .isEqualTo(UploadResult.alreadyStored(recordId));
        assertThat(records.findById(recordId)).isEmpty();
        assertThat(published).isEmpty();

        String sibling = recordId.substring(0, recordId.length() - 1) + "f";
        assertThat(send(m, upload(sibling, START.plusHours(3), 900, 300)).alreadyStored())
                .isFalse();
        assertThat(records.findById(sibling)).isPresent();
    }

    @Test
    @DisplayName(
            "TC-9360·TC-9361 RouteShape.of: 100점 → 24점 이하·0~1, 0·1점 빈 모양, 2·24점 그대로, 25점 → 24점")
    void tc9360_routeShape() {
        RouteShape big = RouteShape.of(points(100));
        assertThat(big.points()).hasSizeLessThanOrEqualTo(RouteShape.MAX_POINTS);
        assertThat(big.points())
                .allMatch(p -> p.x() >= 0 && p.x() <= 1 && p.y() >= 0 && p.y() <= 1);
        assertThat(RouteShape.of(points(0)).isEmpty()).isTrue();
        assertThat(RouteShape.of(points(1)).isEmpty()).isTrue();
        assertThat(RouteShape.of(points(2)).points()).hasSize(2);
        assertThat(RouteShape.of(points(24)).points()).hasSize(24);
        assertThat(RouteShape.of(points(25)).points()).hasSize(24);
        assertThat(big.encode()).doesNotContain("37.").doesNotContain("127.");
    }

    @Test
    @DisplayName("TC-9362·TC-9363 encode·decode 왕복, 같은 좌표·한 줄 경로도 예외 없이 정규화")
    void tc9362_encodeDecode() {
        RouteShape shape = RouteShape.of(points(24));
        assertThat(RouteShape.decode(shape.encode())).isEqualTo(shape);
        assertThat(RouteShape.decode("").isEmpty()).isTrue();
        assertThat(RouteShape.decode(null).isEmpty()).isTrue();

        GeoPoint same = new GeoPoint(37.5, 127.0);
        RouteShape dot = RouteShape.of(List.of(same, same, same));
        assertThat(dot.points()).allMatch(p -> p.x() == 0.5 && p.y() == 0.5);
        RouteShape line =
                RouteShape.of(List.of(new GeoPoint(37.5, 127.0), new GeoPoint(37.6, 127.0)));
        assertThat(line.points()).extracting(RouteShape.ShapePoint::x).containsOnly(0.5);
    }

    private static List<GeoPoint> points(int n) {
        return IntStream.range(0, n)
                .mapToObj(i -> new GeoPoint(37.5 + i * 0.0001, 127.0 + Math.sin(i) * 0.0001))
                .toList();
    }

    @Test
    @DisplayName("TC-9365 경로가 있는 업로드는 저장할 때 모양을 계산한다")
    void tc9365_receiveComputesShape() {
        long m = member();
        send(m, upload(id(1), 5_000, 1_500));
        RunRecord record = records.findById(id(1)).orElseThrow();
        assertThat(record.getRouteShape()).isPresent();
        assertThat(record.getRouteShape().get().points()).hasSizeLessThanOrEqualTo(24);
    }

    @Test
    @DisplayName("TC-9364 되채우기: 모양이 없고 경로가 남은 기록만 채우고 두 번째는 0")
    void tc9364_backfill() {
        long m = member();
        send(m, upload(id(1), 5_000, 1_500));
        send(m, upload(id(2), START.plusHours(1), 3_000, 900));
        RunRecord first = records.findById(id(1)).orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(first, "routeShape", null);
        RunRecord second = records.findById(id(2)).orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(second, "routeShape", null);
        second.purgeRoute();
        RouteShapeBackfillService backfill =
                new RouteShapeBackfillService(records, RunningServicesTest.NO_TX);
        RouteShapeBackfillService.Batch batch = backfill.fillMissing("", 10);
        assertThat(batch.filled()).isEqualTo(1);
        assertThat(first.getRouteShape()).isPresent();
        assertThat(second.getRouteShape()).isEmpty();
        assertThat(backfill.fillMissing(batch.lastRecordId(), 10).lastRecordId()).isNull();
    }

    @Test
    @DisplayName("BE8-11 되채우기: 마지막으로 본 기록 ID부터 이어 가 한 번에 한 건씩 봐도 뒤 기록까지 채운다")
    void be8_11_backfillCursorMovesOn() {
        long m = member();
        send(m, upload(id(1), 5_000, 1_500));
        send(m, upload(id(2), START.plusHours(1), 3_000, 900));
        for (String id : List.of(id(1), id(2))) {
            org.springframework.test.util.ReflectionTestUtils.setField(
                    records.findById(id).orElseThrow(), "routeShape", null);
        }
        RouteShapeBackfillService backfill =
                new RouteShapeBackfillService(records, RunningServicesTest.NO_TX);
        int total = 0;
        int batches = 0;
        String cursor = "";
        while (cursor != null) {
            RouteShapeBackfillService.Batch batch = backfill.fillMissing(cursor, 1);
            total += batch.filled();
            cursor = batch.lastRecordId();
            batches++;
        }
        assertThat(total).isEqualTo(2);
        assertThat(batches).isEqualTo(3); // 두 건과 끝을 알리는 빈 묶음
    }

    @Test
    @DisplayName("TC-9426·TC-9428 목록 줄: 신기록 종류와 모양을 한 번의 조회로 싣는다")
    void tc9426_listRows() {
        long m = member();
        send(m, upload(id(0), START.minusHours(1), 3_000, 1_200));
        send(m, upload(id(1), 5_000, 1_500));
        send(m, upload(id(2), START.plusHours(1), 900, 300));
        List<RunListRow> rows =
                query.listRows(m, new DateRange(START.toLocalDate(), START.toLocalDate()));
        assertThat(records.listRowsCalls).isEqualTo(1);
        // PL1-B5: 홈 최근 러닝은 무효 포함 시작 시각이 가장 늦은 1건(기간 무관)
        assertThat(query.latestListRow(m).orElseThrow().summary().recordId()).isEqualTo(id(2));
        assertThat(query.latestListRow(m + 1)).isEmpty();
        RunListRow first =
                rows.stream()
                        .filter(r -> r.summary().recordId().equals(id(1)))
                        .findFirst()
                        .orElseThrow();
        assertThat(first.newRecord()).isTrue();
        assertThat(first.newRecords())
                .extracting(NewRecordMark::kind)
                .contains(NewRecordMark.Kind.LONGEST, NewRecordMark.Kind.BASE_DISTANCE);
        assertThat(first.newRecords())
                .filteredOn(mark -> mark.kind() == NewRecordMark.Kind.BASE_DISTANCE)
                .extracting(NewRecordMark::distance)
                .contains(StandardDistance.KM1, StandardDistance.KM3);
        assertThat(first.routeShape()).isNotNull();
        RunListRow invalid =
                rows.stream()
                        .filter(r -> r.summary().recordId().equals(id(2)))
                        .findFirst()
                        .orElseThrow();
        assertThat(invalid.newRecords()).isEmpty();
        assertThat(invalid.newRecord()).isFalse();
    }

    @Test
    @DisplayName(
            "TC-9417·TC-9418·TC-9420·TC-9424 평가 시점(v0.18 변경, J24-6·D-484): 3회째 첫 배치 없음,"
                    + " 1·2·3·4·5·9회 없음, 10·20회 재평가(10, 20)")
    void tc9417_evaluationPoints() {
        for (int n : new int[] {1, 2, 3, 4, 5, 9}) {
            assertThat(TierProgress.evaluationBetween(n - 1, n)).as("순번 " + n).isEmpty();
        }
        assertThat(TierProgress.evaluationBetween(9, 10))
                .contains(new TierEvaluation(EvaluationKind.REEVALUATION, 10));
        assertThat(TierProgress.evaluationBetween(19, 20))
                .contains(new TierEvaluation(EvaluationKind.REEVALUATION, 20));
        assertThat(TierProgress.evaluationBetween(2, 4)).as("첫 배치 창이 없다").isEmpty();
        assertThat(TierProgress.evaluationBetween(9, 11))
                .as("건너뛰어도 10을 놓치지 않는다(D-40)")
                .contains(new TierEvaluation(EvaluationKind.REEVALUATION, 10));
        assertThat(TierProgress.evaluationBetween(3, 3)).isEmpty();
    }

    @Test
    @DisplayName(
            "TC-9419·TC-9422·TC-9423·TC-9425 평가 표시 저장: 반영 표시와 함께 한 번만, 확정 유형 노트는 한 열, 없는 평가는 비어 있다")
    void tc9419_evaluationMark() {
        long m = member();
        send(m, upload(id(1), 5_000, 1_500));
        assertThat(query.evaluationOf(id(1))).as("마이그레이션 전·평가 없는 기록").isEmpty();

        // TierApplier: member가 정한 평가(TierChange.evaluation)를 반영 표시와 함께 저장만 한다(D-294)
        com.a207.runkk.member.TierLevelService tiers =
                org.mockito.Mockito.mock(com.a207.runkk.member.TierLevelService.class);
        MemberQueryService applied = org.mockito.Mockito.mock(MemberQueryService.class);
        org.mockito.Mockito.when(
                        tiers.recordValidRun(
                                org.mockito.ArgumentMatchers.anyLong(),
                                org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        new com.a207.runkk.member.TierChange(
                                Tier.SILVER,
                                Tier.GOLD,
                                1,
                                2,
                                new TierEvaluation(EvaluationKind.REEVALUATION, 10, Tier.SILVER)));
        TierApplier applier = new TierApplier(tiers, applied, records);
        com.a207.runkk.member.RunScoreInput input =
                new com.a207.runkk.member.RunScoreInput(
                        50.0, com.a207.runkk.common.Distance.ofKm(5), 10);
        applier.apply(m, id(1), input);
        assertThatThrownBy(() -> applier.apply(m, id(1), input))
                .as("두 번째는 바뀌지 않는다(멱등)")
                .isInstanceOf(IllegalStateException.class);
        assertThat(records.markTierApplied(id(1), Optional.empty())).isFalse();
        new RunEvaluationNote(records).noteRunimalType(id(1), "LONE_WOLF");

        EvaluationView view = query.evaluationOf(id(1)).orElseThrow();
        assertThat(view.kind()).isEqualTo("REEVALUATION");
        assertThat(view.tier()).isEqualTo(Tier.GOLD);
        assertThat(view.validRuns()).isEqualTo(10);
        assertThat(view.runimalType()).isEqualTo("LONE_WOLF");
        assertThat(view.previousTier()).as("재평가 직전 티어(D-성-972)").isEqualTo(Tier.SILVER);

        send(m, upload(id(2), START.plusHours(1), 5_000, 1_500));
        new RunEvaluationNote(records).noteRunimalType(id(2), "LONE_WOLF");
        assertThat(query.evaluationOf(id(2))).as("평가 종류가 없으면 비어 있다").isEmpty();
        new RunEvaluationNote(records).noteRunimalType(id(99), "LONE_WOLF");
    }

    @Test
    @DisplayName("TC-9421 집계 제외 기록은 순번을 올리지 않아 다음 유효 기록이 3회째다")
    void tc9421_invalidDoesNotCount() {
        long m = member();
        send(m, upload(id(1), 5_000, 1_500));
        send(m, upload(id(2), START.plusHours(1), 5_000, 1_500));
        invalidRun(m, 3);
        send(m, upload(id(4), START.plusHours(4), 5_000, 1_500));
        List<RunRecordSaved> saved =
                published.stream()
                        .filter(RunRecordSaved.class::isInstance)
                        .map(RunRecordSaved.class::cast)
                        .toList();
        assertThat(saved).extracting(RunRecordSaved::validRunOrdinal).containsExactly(1, 2, 0, 3);
    }

    @Test
    @DisplayName("D-238 풀(42.195km)은 최고 기록 장부 거리이고 셀프런 거리에 1km가 들어간다")
    void fullIsRecordBase() {
        assertThat(StandardDistance.FULL.isRecordBase()).isTrue();
        assertThat(StandardDistance.KM1.isSelfBattle()).isTrue();
        assertThat(StandardDistance.FULL.isSelfBattle()).isFalse();
    }
}
