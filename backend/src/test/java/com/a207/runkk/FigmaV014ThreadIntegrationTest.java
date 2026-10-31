package com.a207.runkk;

import static com.a207.runkk.running.RunFixtures.START;
import static com.a207.runkk.running.RunFixtures.id;
import static com.a207.runkk.running.RunFixtures.upload;
import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.battle.FriendInviteIssue;
import com.a207.runkk.battle.FriendInviteService;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.WithdrawalService;
import com.a207.runkk.runimal.RunimalProfile;
import com.a207.runkk.runimal.RunimalProfileRepository;
import com.a207.runkk.runimal.RunimalType;
import com.a207.runkk.running.RunDeletionService;
import com.a207.runkk.running.RunUploadCommand;
import com.a207.runkk.running.RunUploadService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * v0.14 서버의 실제 PostgreSQL 시험(코드 리뷰 8차 BE8-12): 같은 기록의 동시 삭제(TC-9352), 삭제·재업로드 경쟁(TC-9828), 기한 지난 초대
 * 정리와 새 초대 경쟁(BE8-4), 만료 경계의 통합(TC-9805), 탈퇴 파기가 새
 * 표(run_record_tombstone·friend_invite·runimal_confirmed_history)를 지우는지. Docker가 없으면 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("v0.14 서버 동시성·탈퇴 파기(PostgreSQL)")
class FigmaV014ThreadIntegrationTest {

    private static final AtomicInteger NAMES = new AtomicInteger();

    @Autowired MemberRepository members;
    @Autowired RunUploadService uploads;
    @Autowired RunDeletionService deletions;
    @Autowired WithdrawalService withdrawals;
    @Autowired FriendInviteService invites;
    @Autowired RunimalProfileRepository runimalProfiles;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        for (String table :
                List.of(
                        "friend_invite",
                        "runimal_confirmed_history",
                        "runimal_profile",
                        "run_record_tombstone",
                        "personal_best",
                        "personal_record_book",
                        "run_record",
                        "disposal_order",
                        "battle_disconnection",
                        "battle_participant",
                        "battle",
                        "member_consent",
                        "member",
                        "audit_record")) {
            jdbc.update("delete from " + table);
        }
    }

    private long newMember() {
        String name = "신규" + NAMES.incrementAndGet();
        return tx.execute(
                s ->
                        members.save(MemberFixtures.member(name, LocalDateTime.now().minusDays(30)))
                                .getId());
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    /** 모든 호출을 같은 순간에 출발시키고 결과(값 또는 던진 업무 규칙 사유)를 모은다. */
    private static List<Object> race(List<Callable<Object>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> call : calls) {
            futures.add(
                    pool.submit(
                            () -> {
                                go.await();
                                try {
                                    return call.call();
                                } catch (BusinessRuleException e) {
                                    return e.reason();
                                }
                            }));
        }
        go.countDown();
        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> f : futures) {
            outcomes.add(f.get());
        }
        pool.shutdown();
        return outcomes;
    }

    private String invalidRun(long memberId, int n) {
        uploads.upload(memberId, new RunUploadCommand(upload(id(n), START.plusHours(n), 900, 300)));
        return id(n);
    }

    @Test
    @DisplayName("TC-9352 같은 기록을 두 요청이 동시에 삭제하면 한쪽만 성공하고 다른 쪽은 RECORD_NOT_FOUND, 묘비 1건")
    void tc9352_concurrentDelete() throws Exception {
        long m = newMember();
        String recordId = invalidRun(m, 2);

        List<Object> outcomes =
                race(
                        List.of(
                                () -> {
                                    deletions.delete(m, recordId);
                                    return "deleted";
                                },
                                () -> {
                                    deletions.delete(m, recordId);
                                    return "deleted";
                                }));

        assertThat(outcomes).containsExactlyInAnyOrder("deleted", ReasonCode.RECORD_NOT_FOUND);
        assertThat(count("select count(*) from run_record_tombstone where record_id = ?", recordId))
                .isEqualTo(1);
        assertThat(count("select count(*) from run_record where record_id = ?", recordId)).isZero();
    }

    @Test
    @DisplayName("TC-9828 삭제와 같은 ID 재업로드가 겹쳐도 기록과 묘비가 동시에 있는 상태는 없다")
    void tc9828_deleteReuploadRace() throws Exception {
        for (int round = 0; round < 5; round++) {
            long m = newMember();
            String recordId = invalidRun(m, 2);

            race(
                    List.of(
                            () -> {
                                deletions.delete(m, recordId);
                                return "deleted";
                            },
                            () ->
                                    uploads.upload(
                                            m,
                                            new RunUploadCommand(
                                                    upload(
                                                            recordId,
                                                            START.plusHours(2),
                                                            900,
                                                            300)))));

            int records = count("select count(*) from run_record where record_id = ?", recordId);
            int tombstones =
                    count(
                            "select count(*) from run_record_tombstone where record_id = ?",
                            recordId);
            assertThat(records + tombstones)
                    .as("기록 0건 또는 1건으로 일관, 묘비와 기록이 함께 있지 않다(round %d)", round)
                    .isLessThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("BE8-4 기한 지난 자기 초대를 정리하는 작업과 새 초대가 겹쳐도 409 CONCURRENT_UPDATE 없이 새 초대가 만들어진다")
    void be8_4_inviteVersusExpiryJob() throws Exception {
        for (int round = 0; round < 5; round++) {
            long inviter = newMember();
            invites.invite(inviter, StandardDistance.KM3);
            jdbc.update(
                    "update friend_invite set expires_at = ? where inviter_member_id = ?",
                    LocalDateTime.now().minusSeconds(1),
                    inviter);

            List<Object> outcomes =
                    race(
                            List.of(
                                    () -> invites.invite(inviter, StandardDistance.KM5),
                                    () -> invites.expireDue(LocalDateTime.now())));

            assertThat(outcomes.get(0)).isInstanceOf(FriendInviteIssue.class);
            assertThat(
                            count(
                                    "select count(*) from friend_invite where inviter_member_id = ?"
                                            + " and status = 'PENDING'",
                                    inviter))
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("TC-9805 만료 경계(통합): 기한 지난 수락은 정리 작업 전이라도 INVITE_EXPIRED, 기한 전 수락은 성공")
    void tc9805_expiryBoundary() throws Exception {
        long inviter = newMember();
        long late = newMember();
        long early = newMember();
        FriendInviteIssue issue = invites.invite(inviter, StandardDistance.KM3);

        jdbc.update(
                "update friend_invite set expires_at = ? where id = ?",
                LocalDateTime.now().minusSeconds(1),
                issue.inviteId());
        List<Object> expired = race(List.of(() -> invites.accept(late, issue.token())));
        assertThat(expired).containsExactly(ReasonCode.INVITE_EXPIRED);
        assertThat(
                        count(
                                "select count(*) from friend_invite where id = ? and status ="
                                        + " 'PENDING'",
                                issue.inviteId()))
                .as("정리 작업 전이라 저장 상태는 그대로")
                .isEqualTo(1);

        jdbc.update(
                "update friend_invite set expires_at = ? where id = ?",
                LocalDateTime.now().plusMinutes(1),
                issue.inviteId());
        assertThat(invites.accept(early, issue.token())).isPositive();
    }

    @Test
    @DisplayName(
            "탈퇴 파기: 묘비·초대·확정 이력 표(run_record_tombstone·friend_invite·runimal_confirmed_history)가"
                    + " 지워진다")
    void withdrawalErasesNewTables() {
        long m = newMember();
        long other = newMember();
        String recordId = invalidRun(m, 2);
        deletions.delete(m, recordId);
        invites.invite(m, StandardDistance.KM1);
        invites.invite(other, StandardDistance.KM1);
        for (long member : List.of(m, other)) {
            tx.executeWithoutResult(
                    s -> {
                        RunimalProfile profile = runimalProfiles.findOrCreate(member);
                        ReflectionTestUtils.setField(
                                profile,
                                "confirmedHistory",
                                new HashSet<>(Set.of(RunimalType.values()[0])));
                        runimalProfiles.save(profile);
                    });
        }
        assertThat(count("select count(*) from runimal_confirmed_history where member_id = ?", m))
                .isEqualTo(1);

        assertThat(withdrawals.withdraw(m).withdrawn()).isTrue();

        assertThat(count("select count(*) from run_record_tombstone where member_id = ?", m))
                .isZero();
        assertThat(
                        count(
                                "select count(*) from friend_invite where inviter_member_id = ?"
                                        + " or joiner_member_id = ?",
                                m,
                                m))
                .isZero();
        assertThat(count("select count(*) from runimal_confirmed_history where member_id = ?", m))
                .isZero();
        assertThat(count("select count(*) from runimal_profile where member_id = ?", m)).isZero();
        assertThat(count("select count(*) from friend_invite where inviter_member_id = ?", other))
                .isEqualTo(1);
        assertThat(
                        count(
                                "select count(*) from runimal_confirmed_history where member_id ="
                                        + " ?",
                                other))
                .isEqualTo(1);
    }
}
