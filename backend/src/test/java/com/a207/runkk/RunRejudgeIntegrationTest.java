package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.infra.running.JpaRunRecordRepository;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.reward.AccrualReason;
import com.a207.runkk.reward.RewardService;
import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.OverspeedFixtures;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.running.RunRecord;
import com.a207.runkk.running.RunRejudgeService;
import com.a207.runkk.running.ValidityRule;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * v0.10 일회성 재판정을 실제 PostgreSQL(Flyway V27·V28)에서 확인한다(사용자 결정: 기존 무효 기록도 새 규칙으로). 배포 전 무효 기록에 V28과
 * 같은 PENDING 표시를 세우고 RunRejudgeService.run을 부른다: 조건부 갱신이 집계 거리·제외량·점수를 쓰고(ck 제약 통과), 리워드는 원래 저장일
 * 근거로 한 번만 적립하고, 알림은 남기지 않으며, 다시 돌려도 같다. 경로를 파기한 기록은 SKIPPED다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("v0.10 일회성 재판정(PostgreSQL V28)")
class RunRejudgeIntegrationTest {

    private static final AtomicInteger NAMES = new AtomicInteger();

    /** 옛 규칙의 무효 판정 상태(선언 값 그대로, 제외량 0)를 흉내 내는 판정 기준. */
    private static final ValidityRule OLD_INVALID =
            new ValidityRule(
                    Distance.ofKm(100_000),
                    Duration.ofMinutes(3),
                    new Pace(180),
                    new Pace(900),
                    Double.MAX_VALUE);

    @Autowired RunRejudgeService rejudge;
    @Autowired JpaRunRecordRepository records;
    @Autowired RewardService rewards;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        for (String table :
                List.of(
                        "notification",
                        "avatar_background",
                        "reward_accrual",
                        "reward_wallet",
                        "personal_best",
                        "personal_record_book",
                        "run_record")) {
            jdbc.update("delete from " + table);
        }
    }

    private long member() {
        String name = "재판정러너" + NAMES.incrementAndGet();
        return tx.execute(
                s ->
                        members.save(MemberFixtures.member(name, LocalDateTime.now().minusDays(30)))
                                .getId());
    }

    /** 배포 전에 저장된 옛 앱 기록(초과 구간 포함 선언, 무효)에 V28처럼 PENDING을 세운다. */
    private String storedInvalid(long memberId, int n, LocalDateTime savedAt) {
        List<LocationPoint> points = OverspeedFixtures.shortJump(RunFixtures.START.plusHours(n));
        RunRecord r =
                RunRecord.receive(
                        memberId,
                        OverspeedFixtures.upload(
                                RunFixtures.id(n),
                                points,
                                OverspeedFixtures.pathMeters(points),
                                Duration.ofSeconds(928)));
        r.judgeValidity(OLD_INVALID);
        r.markSaved(savedAt);
        tx.executeWithoutResult(s -> records.save(r));
        jdbc.update(
                "update run_record set rejudge_state = 'PENDING' where record_id = ?",
                r.getRecordId());
        return r.getRecordId();
    }

    private Map<String, Object> row(String recordId) {
        return jdbc.queryForMap(
                "select valid, distance_meters, elapsed_millis, average_pace_seconds, score,"
                        + " excluded_meters, excluded_millis, rejudge_state, tier_applied"
                        + " from run_record where record_id = ?",
                recordId);
    }

    @Test
    @DisplayName("무효였던 50m 순간이동 기록이 3.09km 유효가 되고, 리워드는 한 번, 알림은 없고, 다시 돌려도 같다")
    void rejudgesOnceWithSideEffects() {
        long m = member();
        LocalDateTime savedAt = LocalDateTime.now().minusDays(20).withNano(0);
        String recordId = storedInvalid(m, 1, savedAt);

        RunRejudgeService.Result first = rejudge.run();

        assertThat(first).isEqualTo(new RunRejudgeService.Result(1, 0, 0, 1, 0));
        Map<String, Object> after = row(recordId);
        assertThat(after.get("valid")).isEqualTo(true);
        assertThat(after.get("distance_meters")).isEqualTo(3_090L);
        assertThat(after.get("elapsed_millis")).isEqualTo(927_000L);
        assertThat(after.get("average_pace_seconds")).isEqualTo(300);
        assertThat(after.get("score")).isNotNull();
        assertThat(after.get("excluded_meters")).isEqualTo(50L);
        assertThat(after.get("excluded_millis")).isEqualTo(1_000L);
        assertThat(after.get("rejudge_state")).isEqualTo("DONE");
        assertThat(after.get("tier_applied")).isEqualTo(true);
        int accrued = rewards.accruedFor(AccrualReason.VALID_RUN, recordId, m);
        assertThat(accrued).isPositive();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from notification where recipient_id = ?",
                                Long.class,
                                m))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from personal_best where member_id = ?"
                                        + " and record_id = ?",
                                Long.class,
                                m,
                                recordId))
                .isPositive();

        assertThat(rejudge.run()).isEqualTo(new RunRejudgeService.Result(0, 0, 0, 0, 0));
        assertThat(rewards.accruedFor(AccrualReason.VALID_RUN, recordId, m)).isEqualTo(accrued);
        assertThat(row(recordId)).isEqualTo(after);
    }

    @Test
    @DisplayName("경로를 파기한 기록은 다시 판정하지 않고 SKIPPED로 남긴다")
    void skipsPurgedRoute() {
        long m = member();
        String recordId = storedInvalid(m, 2, LocalDateTime.now().minusDays(20).withNano(0));
        jdbc.update(
                "update run_record set route_data = null, route_purged = true where record_id = ?",
                recordId);

        assertThat(rejudge.run()).isEqualTo(new RunRejudgeService.Result(0, 0, 1, 0, 0));
        Map<String, Object> after = row(recordId);
        assertThat(after.get("valid")).isEqualTo(false);
        assertThat(after.get("distance_meters")).isEqualTo(3_140L);
        assertThat(after.get("rejudge_state")).isEqualTo("SKIPPED");
    }
}
