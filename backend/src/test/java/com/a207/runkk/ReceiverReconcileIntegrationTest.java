package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.a207.runkk.batch.crew.CrewRunLinkReconcileJob;
import com.a207.runkk.batch.reward.RewardReconcileJob;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.CrewTag;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.crew.CrewCreateCommand;
import com.a207.runkk.crew.CrewPostRepository;
import com.a207.runkk.crew.CrewPostService;
import com.a207.runkk.crew.CrewRunDraft;
import com.a207.runkk.crew.CrewRunLinkReconcileService;
import com.a207.runkk.crew.CrewRunService;
import com.a207.runkk.crew.CrewService;
import com.a207.runkk.crew.CrewVisibility;
import com.a207.runkk.crew.JoinPolicy;
import com.a207.runkk.crew.JoinTarget;
import com.a207.runkk.crew.MembershipService;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.reward.AccrualReason;
import com.a207.runkk.reward.RewardReconcileService;
import com.a207.runkk.reward.RewardService;
import com.a207.runkk.reward.RewardWalletRepository;
import com.a207.runkk.running.CardKind;
import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunUpload;
import com.a207.runkk.running.RunUploadCommand;
import com.a207.runkk.running.RunUploadService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
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
 * 코드 리뷰 2차 M4 결정: 커밋 뒤 수신자(리워드 적립, 크루런 기록 연결)가 계속 실패해 빠진 결과를 일 단위 보정 배치가 실 PostgreSQL 위에서 채운다. 수신자
 * 실패를 흉내 낸 뒤 보정이 1건씩 복구하고, 다시 돌리면 0건이며, 리워드 하루 상한은 원래 사건일(저장 일시)로 센다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("커밋 뒤 수신자 일 단위 보정(M4 결정, PostgreSQL)")
class ReceiverReconcileIntegrationTest {

    private static final AtomicInteger NAMES = new AtomicInteger();

    @MockitoSpyBean RewardWalletRepository wallets;
    @MockitoSpyBean CrewPostRepository crewPosts;
    @Autowired RewardService rewards;
    @Autowired RewardReconcileService rewardReconcile;
    @Autowired CrewRunLinkReconcileService linkReconcile;
    @Autowired RewardReconcileJob rewardJob;
    @Autowired CrewRunLinkReconcileJob linkJob;
    @Autowired OperationPolicy policy;
    @Autowired RunUploadService uploads;
    @Autowired CrewService crews;
    @Autowired CrewPostService posts;
    @Autowired CrewRunService crewRuns;
    @Autowired MembershipService membership;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        for (String table :
                List.of(
                        "race_contribution",
                        "weekly_race",
                        "crew_run_participation",
                        "crew_post",
                        "join_request",
                        "crew_membership",
                        "crew",
                        "reward_accrual",
                        "owned_item",
                        "reward_wallet",
                        "personal_best",
                        "personal_record_book",
                        "run_record",
                        "member_consent",
                        "member",
                        "audit_record")) {
            jdbc.update("delete from " + table);
        }
    }

    @AfterEach
    void restore() {
        reset(wallets, crewPosts);
    }

    private long member() {
        String name = "보정러너" + NAMES.incrementAndGet();
        return tx.execute(
                s ->
                        members.save(MemberFixtures.member(name, LocalDateTime.now().minusDays(30)))
                                .getId());
    }

    private static RunUpload crewRun(long postId, LocalDateTime start, long meters, int seconds) {
        List<LocationPoint> points =
                RunFixtures.straightLine(start, (double) meters / seconds, seconds);
        return new RunUpload(
                UUID.randomUUID().toString(),
                start,
                start.plusSeconds(seconds),
                Distance.ofMeters(meters),
                Duration.ofSeconds(seconds),
                RunMode.CREW_RUN,
                CardKind.CREW_RUN,
                null,
                postId,
                points.size(),
                points,
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("리워드·크루런 연결 수신자가 실패한 기록 1건을 보정이 채우고, 다시 돌리면 0건이다")
    void failedReceivers_areRestoredOnce() {
        long leader = member();
        long crewId =
                crews.create(
                                leader,
                                new CrewCreateCommand(
                                        "보정크루" + NAMES.incrementAndGet(),
                                        null,
                                        Set.of(CrewTag.WEEKDAY_EVENING),
                                        CrewVisibility.PUBLIC,
                                        JoinPolicy.FREE,
                                        null))
                        .crewId();
        LocalDateTime scheduledAt = LocalDateTime.now().plusMinutes(20).withNano(0);
        long postId =
                posts.writeCrewRun(
                                leader,
                                crewId,
                                new CrewRunDraft("보정 크루런", scheduledAt, StandardDistance.KM1, null))
                        .postId();
        long mate = member();
        membership.apply(mate, JoinTarget.byCrew(crewId));
        for (long m : List.of(leader, mate)) { // 최소 인원(2명)이 준비해야 출발한다
            crewRuns.enterLobby(m, postId);
            crewRuns.toggleReady(m, postId);
        }
        crewRuns.tick(scheduledAt);
        assertThat(
                        jdbc.queryForObject(
                                "select status from crew_post where id = ?", String.class, postId))
                .isEqualTo("RUNNING");
        LocalDateTime start = scheduledAt.plusSeconds(3);

        doThrow(new IllegalStateException("수신자 장애")).when(wallets).findOrCreateForUpdate(anyLong());
        doThrow(new IllegalStateException("수신자 장애"))
                .when(crewPosts)
                .findCrewRunByIdForUpdate(postId);
        String recordId =
                uploads.upload(leader, new RunUploadCommand(crewRun(postId, start, 1_000, 300)))
                        .recordId();
        reset(wallets, crewPosts);
        assertThat(rewards.accruedFor(AccrualReason.VALID_RUN, recordId, leader)).isZero();
        assertThat(linkedRecord(postId, leader)).isNull();

        LocalDateTime now = LocalDateTime.now();
        assertThat(rewardReconcile.reconcile(now)).isEqualTo(1);
        assertThat(linkReconcile.reconcileLinks(now)).isEqualTo(1);
        assertThat(rewards.accruedFor(AccrualReason.VALID_RUN, recordId, leader)).isEqualTo(10);
        assertThat(linkedRecord(postId, leader)).isEqualTo(recordId);

        assertThat(rewardReconcile.reconcile(LocalDateTime.now())).isZero();
        assertThat(linkReconcile.reconcileLinks(LocalDateTime.now())).isZero();
        rewardJob.run();
        linkJob.run();
        assertThat(rewards.balanceOf(leader)).isEqualTo(10);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from reward_accrual where source_id = ?",
                                Integer.class,
                                recordId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("늦게 보정해도 상한은 원래 사건일(저장 일시) 기준: 전날 남은 10만 그날 날짜로 적립한다")
    void dailyCap_countsOriginalDay() {
        long runner = member();
        doThrow(new IllegalStateException("수신자 장애")).when(wallets).findOrCreateForUpdate(anyLong());
        String recordId =
                uploads.upload(
                                runner,
                                new RunUploadCommand(
                                        RunFixtures.upload(
                                                UUID.randomUUID().toString(),
                                                LocalDateTime.now().minusDays(1).withHour(7),
                                                5_000,
                                                1_500)))
                        .recordId();
        reset(wallets);
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1).withHour(12).withNano(0);
        jdbc.update(
                "update run_record set saved_at = ? where record_id = ?",
                Timestamp.valueOf(yesterday),
                recordId);
        int cap = policy.rewardRule().dailyCap();
        // V26 이후 지갑은 집계 칼럼(가장 최근 적립일·그날 합계)을 적립 내역과 맞춰 둔다(D-109)
        jdbc.update(
                "insert into reward_wallet (member_id, balance, last_accrual_date,"
                        + " last_accrual_date_amount, version) values (?, ?, ?, ?, 0)",
                runner,
                cap - 10,
                java.sql.Date.valueOf(yesterday.toLocalDate()),
                cap - 10);
        jdbc.update(
                "insert into reward_accrual (member_id, reason, source_id, amount, accrued_at)"
                        + " values (?, 'BATTLE_WIN', 'filler', ?, ?)",
                runner,
                cap - 10,
                Timestamp.valueOf(yesterday.withHour(9)));

        assertThat(rewardReconcile.reconcile(LocalDateTime.now())).isEqualTo(1);
        assertThat(rewards.accruedFor(AccrualReason.VALID_RUN, recordId, runner)).isEqualTo(10);
        assertThat(rewards.balanceOf(runner)).isEqualTo(cap);
        assertThat(
                        jdbc.queryForObject(
                                "select accrued_at from reward_accrual where source_id = ?",
                                Timestamp.class,
                                recordId))
                .isEqualTo(Timestamp.valueOf(yesterday));
        assertThat(rewardReconcile.reconcile(LocalDateTime.now())).isZero();
    }

    @Test
    @DisplayName("T-32 TC-6792: 지갑 집계 칼럼이 오늘(30)인 회원의 전날 보정은 sumOn(전날)으로 상한을 세고 칼럼은 그대로다(D-109)")
    void olderDayReconcile_keepsDailyTotalColumns() {
        long runner = member();
        doThrow(new IllegalStateException("수신자 장애")).when(wallets).findOrCreateForUpdate(anyLong());
        String recordId =
                uploads.upload(
                                runner,
                                new RunUploadCommand(
                                        RunFixtures.upload(
                                                UUID.randomUUID().toString(),
                                                LocalDateTime.now().minusDays(1).withHour(7),
                                                5_000,
                                                1_500)))
                        .recordId();
        reset(wallets);
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1).withHour(12).withNano(0);
        jdbc.update(
                "update run_record set saved_at = ? where record_id = ?",
                Timestamp.valueOf(yesterday),
                recordId);
        int cap = policy.rewardRule().dailyCap();
        java.time.LocalDate today = LocalDateTime.now().toLocalDate();
        jdbc.update(
                "insert into reward_wallet (member_id, balance, last_accrual_date,"
                        + " last_accrual_date_amount, version) values (?, ?, ?, 30, 0)",
                runner,
                cap - 20 + 30,
                java.sql.Date.valueOf(today));
        jdbc.update(
                "insert into reward_accrual (member_id, reason, source_id, amount, accrued_at)"
                    + " values (?, 'BATTLE_WIN', 'prev', ?, ?), (?, 'BATTLE_WIN', 'now', 30, ?)",
                runner,
                cap - 20,
                Timestamp.valueOf(yesterday.withHour(9)),
                runner,
                Timestamp.valueOf(today.atStartOfDay()));

        assertThat(rewardReconcile.reconcile(LocalDateTime.now())).isEqualTo(1);

        assertThat(rewards.accruedFor(AccrualReason.VALID_RUN, recordId, runner)).isEqualTo(20);
        assertThat(
                        jdbc.queryForMap(
                                "select last_accrual_date, last_accrual_date_amount from"
                                        + " reward_wallet where member_id = ?",
                                runner))
                .containsEntry("last_accrual_date", java.sql.Date.valueOf(today))
                .containsEntry("last_accrual_date_amount", 30);
        assertThat(rewardReconcile.reconcile(LocalDateTime.now())).isZero();
    }

    private String linkedRecord(long postId, long memberId) {
        List<String> linked =
                jdbc.queryForList(
                        "select p.linked_record_id from crew_run_participation p"
                                + " where p.member_id = ? and p.post_id = ?",
                        String.class,
                        memberId,
                        postId);
        return linked.isEmpty() ? null : linked.get(0);
    }
}
