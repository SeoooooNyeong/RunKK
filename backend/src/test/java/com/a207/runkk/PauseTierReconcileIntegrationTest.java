package com.a207.runkk;

import static com.a207.runkk.running.RunFixtures.START;
import static com.a207.runkk.running.RunFixtures.id;
import static com.a207.runkk.running.RunFixtures.upload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.a207.runkk.analysis.RecordDetailView;
import com.a207.runkk.analysis.RecordQueryService;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.TierLevelService;
import com.a207.runkk.running.CardKind;
import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.PauseInterval;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunUpload;
import com.a207.runkk.running.RunUploadCommand;
import com.a207.runkk.running.RunUploadService;
import com.a207.runkk.running.SignalState;
import com.a207.runkk.running.TierReconcileService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 시험 03 T-26 서버 부분(TC-6676·TC-6678·TC-6682)과 T-32 티어 부분(TC-6786~TC-6788)을 실제 PostgreSQL(V21)로 확인한다.
 * RunUploadService → RunUpload 검사 → RunRecord.judgeValidity(다섯째 조건) →
 * run_record.pauses·paused_millis → RecordQueryService.detail, TierUpdateListener 실패 →
 * TierReconcileService.reconcile → TierLevelService.recordValidRun.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("일시정지 업로드·판정(T-26)과 티어 보정(T-32)")
class PauseTierReconcileIntegrationTest {

    private static final LocalDateTime T10 = LocalDateTime.of(2026, 10, 22, 10, 0);

    @MockitoSpyBean TierLevelService tierLevelService;
    @Autowired RunUploadService uploads;
    @Autowired RunQueryService runQuery;
    @Autowired RecordQueryService recordQuery;
    @Autowired TierReconcileService reconcile;
    @Autowired MemberQueryService memberQuery;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    @BeforeEach
    void clean() {
        for (String table :
                List.of(
                        "runimal_profile",
                        "course_verdict",
                        "personal_best",
                        "personal_record_book",
                        "run_record",
                        "member_consent",
                        "member",
                        "audit_record")) {
            jdbc.update("delete from " + table);
        }
        reset(tierLevelService);
    }

    private long member(String nickname) {
        return tx.execute(
                s ->
                        members.save(
                                        MemberFixtures.member(
                                                nickname, LocalDateTime.now().minusDays(30)))
                                .getId());
    }

    private static List<LocationPoint> leg(
            LocalDateTime from, LocalDateTime to, double startMeters, double mps) {
        List<LocationPoint> points = new ArrayList<>();
        long seconds = Duration.between(from, to).toSeconds();
        for (int i = 0; i <= seconds; i++) {
            double meters = startMeters + i * mps;
            points.add(
                    new LocationPoint(
                            from.plusSeconds(i),
                            new GeoPoint(37.5 + meters / RunFixtures.METERS_PER_DEGREE, 127.0),
                            5,
                            SignalState.NORMAL));
        }
        return points;
    }

    /** 10:00~10:30, 정지 [10:10, 10:15]·[10:20, 10:21], 5km(경로 5km). */
    private static RunUpload pausedRun(String id, long elapsedMillis) {
        double v = 5_000 / 1_440.0;
        List<LocationPoint> points = new ArrayList<>(leg(T10, T10.plusMinutes(10), 0, v));
        points.addAll(leg(T10.plusMinutes(15), T10.plusMinutes(20), 600 * v, v));
        points.addAll(leg(T10.plusMinutes(21), T10.plusMinutes(30), 900 * v, v));
        return new RunUpload(
                id,
                T10,
                T10.plusMinutes(30),
                Distance.ofKm(5),
                Duration.ofMillis(elapsedMillis),
                RunMode.FREE,
                CardKind.FREE_RUN,
                null,
                null,
                points.size(),
                points,
                List.of(),
                List.of(),
                List.of(
                        new PauseInterval(T10.plusMinutes(10), T10.plusMinutes(15)),
                        new PauseInterval(T10.plusMinutes(20), T10.plusMinutes(21))));
    }

    @Test
    @DisplayName(
            "TC-6676·TC-6678·TC-6682: 정지 두 줄 저장, +1초 경계(1,441,000 유효 / 1,441,001 무효), 상세 pauses·정지"
                    + " 360초, 5km 최고 기록은 정지 제외")
    void t26_pausedUploadIsJudgedAndShown() {
        long m = member("정지곰");
        assertThat(uploads.upload(m, new RunUploadCommand(pausedRun(id(1), 1_440_000))).valid())
                .isTrue();
        assertThat(uploads.upload(m, new RunUploadCommand(pausedRun(id(2), 1_441_000))).valid())
                .isTrue();
        assertThat(uploads.upload(m, new RunUploadCommand(pausedRun(id(3), 1_441_001))).valid())
                .isFalse();

        assertThat(
                        jdbc.queryForObject(
                                "select paused_millis from run_record where record_id = ?",
                                Long.class,
                                id(1)))
                .isEqualTo(360_000L);
        assertThat(
                        jdbc.queryForObject(
                                "select pauses from run_record where record_id = ?",
                                String.class,
                                id(1)))
                .hasLineCount(2);

        RecordDetailView detail = recordQuery.detail(m, id(1));
        assertThat(detail.pauses()).hasSize(2);
        assertThat(detail.pausedSeconds()).isEqualTo(360);
        assertThat(detail.summary().elapsed()).isEqualTo(Duration.ofMinutes(24));
        assertThat(runQuery.best(m, StandardDistance.KM5).orElseThrow().time())
                .hasValueSatisfying(t -> assertThat(t.toSeconds()).isBetween(1_438L, 1_441L));
    }

    @Test
    @DisplayName("TC-6786·TC-6787: 순번 9·10 수신 실패 뒤 보정이 9, 10을 반영하고 다시 돌리면 0(멱등)")
    void t32_reconcileFillsMissingOrdinals() {
        long m = member("보정곰");
        tx.executeWithoutResult(
                s -> {
                    var member = members.findById(m).orElseThrow();
                    member.confirmPlacement(Tier.BRONZE); // 재평가는 랭크 티어만(D-484)
                    members.save(member);
                });
        doThrow(new IllegalStateException("수신자 장애"))
                .when(tierLevelService)
                .recordValidRun(anyLong(), argThat(input -> input.validRunOrdinal() >= 9));
        for (int i = 1; i <= 10; i++) {
            uploads.upload(
                    m, new RunUploadCommand(upload(id(i), START.plusHours(i), 5_000, 1_500)));
        }
        assertThat(memberQuery.appliedValidRunCount(m)).isEqualTo(8);
        reset(tierLevelService);

        LocalDateTime now = LocalDateTime.now(clock).plusMinutes(1);
        assertThat(reconcile.reconcile(now)).isEqualTo(1);
        assertThat(memberQuery.appliedValidRunCount(m)).isEqualTo(10);
        assertThat(members.findById(m).orElseThrow().getTier()).isEqualTo(Tier.GOLD);
        assertThat(reconcile.reconcile(now)).isZero();
    }

    @Test
    @DisplayName("TC-6788: 회원 A의 보정이 실패해도 B는 반영되고 작업은 예외를 던지지 않는다")
    void t32_failureOfOneMemberDoesNotStopOthers() {
        long a = member("실패곰");
        long b = member("성공곰");
        doThrow(new IllegalStateException("수신자 장애"))
                .when(tierLevelService)
                .recordValidRun(anyLong(), argThat(input -> input.validRunOrdinal() == 1));
        uploads.upload(a, new RunUploadCommand(upload(id(1), START, 5_000, 1_500)));
        uploads.upload(b, new RunUploadCommand(upload(id(2), START, 5_000, 1_500)));
        reset(tierLevelService);
        doThrow(new IllegalStateException("A 장애"))
                .when(tierLevelService)
                .recordValidRun(eq(a), argThat(input -> true));

        assertThat(reconcile.reconcile(LocalDateTime.now(clock).plusMinutes(1))).isEqualTo(1);
        assertThat(memberQuery.appliedValidRunCount(a)).isZero();
        assertThat(memberQuery.appliedValidRunCount(b)).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-6790(총괄 결정 7-3): 순번 3만 실패하고 4가 먼저 반영된 구멍을 보정이 tier_applied로 찾아 늦게 메운다")
    void t32_holeIsFilledByAppliedFlag() {
        long m = member("구멍곰");
        doThrow(new IllegalStateException("수신자 장애"))
                .when(tierLevelService)
                .recordValidRun(anyLong(), argThat(input -> input.validRunOrdinal() == 3));
        for (int i = 1; i <= 4; i++) {
            uploads.upload(
                    m, new RunUploadCommand(upload(id(i), START.plusHours(i), 5_000, 1_500)));
        }
        reset(tierLevelService);
        // 3회째 첫 배치가 없어졌다(J24-6): 4회째가 먼저 반영돼도 언랭 그대로
        assertThat(members.findById(m).orElseThrow().getTier()).isEqualTo(Tier.UNRANKED);
        assertThat(totalValidMeters(m)).isEqualTo(15_000L);
        assertThat(
                        jdbc.queryForList(
                                "select record_id from run_record where member_id = ? and valid"
                                        + " and not tier_applied",
                                String.class,
                                m))
                .containsExactly(id(3));

        assertThat(reconcile.reconcile(LocalDateTime.now(clock).plusMinutes(1))).isEqualTo(1);
        assertThat(totalValidMeters(m)).isEqualTo(20_000L);
        assertThat(memberQuery.appliedValidRunCount(m)).isEqualTo(4);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from run_record where member_id = ? and valid"
                                        + " and not tier_applied",
                                Integer.class,
                                m))
                .isZero();
        assertThat(reconcile.reconcile(LocalDateTime.now(clock).plusMinutes(1))).isZero();
        assertThat(totalValidMeters(m)).isEqualTo(20_000L);
    }

    private long totalValidMeters(long memberId) {
        return jdbc.queryForObject(
                "select total_valid_meters from member where id = ?", Long.class, memberId);
    }
}
