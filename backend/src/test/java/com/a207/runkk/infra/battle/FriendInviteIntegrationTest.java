package com.a207.runkk.infra.battle;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.battle.Battle;
import com.a207.runkk.battle.BattleRepository;
import com.a207.runkk.battle.BattleType;
import com.a207.runkk.battle.BattleWin;
import com.a207.runkk.battle.FriendInvite;
import com.a207.runkk.battle.FriendInviteIssue;
import com.a207.runkk.battle.FriendInviteRepository;
import com.a207.runkk.battle.FriendInviteService;
import com.a207.runkk.battle.FriendInviteStatus;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * 친구 대결 초대의 동시성과 저장소(PostgreSQL, Flyway V30): 같은 초대를 둘이 동시에 수락하면 한 명만 참가하고(T-33, TC-9803), 같은 회원의
 * 동시 초대는 하나만 남으며(TC-9804, uq_friend_invite_pending), 일 단위 리워드 보정 조회는 친구 대결 승리를 빼고(TC-9315), 만료 정리
 * 조회는 기한이 지난 PENDING만 고른다. Docker가 없으면 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("친구 대결 초대 동시성·저장소(PostgreSQL)")
class FriendInviteIntegrationTest {

    @Autowired FriendInviteService invites;
    @Autowired FriendInviteRepository inviteRepository;
    @Autowired BattleRepository battles;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private int sequence;

    @BeforeEach
    void clean() {
        for (String table :
                List.of(
                        "friend_invite",
                        "battle_disconnection",
                        "battle_participant",
                        "battle",
                        "member_consent",
                        "member",
                        "audit_record")) {
            jdbc.update("delete from " + table);
        }
    }

    private long runner() {
        String name = "초대러너" + (++sequence);
        return tx.execute(
                s ->
                        members.save(MemberFixtures.member(name, LocalDateTime.now().minusDays(30)))
                                .getId());
    }

    private static <T> List<Object> race(List<Callable<T>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<T> call : calls) {
            futures.add(
                    pool.submit(
                            () -> {
                                go.await();
                                try {
                                    return (Object) call.call();
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

    @Test
    @DisplayName("TC-9803 같은 토큰을 둘이 동시에 수락하면 한 명만 대결 ID를 받고 다른 쪽은 INVITE_ALREADY_JOINED, 대결 1건")
    void concurrentAccept_oneWins() throws Exception {
        long inviter = runner();
        long b = runner();
        long c = runner();
        FriendInviteIssue issue = invites.invite(inviter, StandardDistance.KM3);

        List<Object> outcomes =
                race(
                        List.of(
                                () -> invites.accept(b, issue.token()),
                                () -> invites.accept(c, issue.token())));

        assertThat(outcomes).filteredOn(Long.class::isInstance).hasSize(1);
        assertThat(outcomes).contains(ReasonCode.INVITE_ALREADY_JOINED);
        assertThat(jdbc.queryForObject("select count(*) from battle", Integer.class)).isEqualTo(1);
        FriendInvite stored = inviteRepository.findById(issue.inviteId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(FriendInviteStatus.ACCEPTED);
        assertThat(stored.getBattleId()).isPresent();
        Battle battle = battles.findById(stored.getBattleId().orElseThrow()).orElseThrow();
        assertThat(battle.getType()).isEqualTo(BattleType.FRIEND);
    }

    @Test
    @DisplayName("TC-9804 같은 회원이 동시에 초대를 두 번 만들면 하나만 남고 다른 쪽은 INVITE_ALREADY_PENDING")
    void concurrentInvite_onePending() throws Exception {
        long inviter = runner();
        List<Object> outcomes =
                race(
                        List.of(
                                () -> invites.invite(inviter, StandardDistance.KM5),
                                () -> invites.invite(inviter, StandardDistance.KM5)));
        assertThat(outcomes).filteredOn(FriendInviteIssue.class::isInstance).hasSize(1);
        assertThat(outcomes).contains(ReasonCode.INVITE_ALREADY_PENDING);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from friend_invite where status = 'PENDING'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "TC-9315 리워드 보정 조회(findMemberWinsJudgedBetween)는 친구 대결 승리를 뺀다, 만료 조회는 기한 지난 PENDING만")
    void repositoryQueries() {
        long a = runner();
        long b = runner();
        FriendInviteIssue issue = invites.invite(a, StandardDistance.KM1);
        long battleId = invites.accept(b, issue.token());
        LocalDateTime judgedAt = LocalDateTime.now().withNano(0);
        jdbc.update(
                "update battle set status = 'FINISHED', judged_at = ?, winner_member_id = ? where"
                        + " id = ?",
                judgedAt,
                a,
                battleId);
        jdbc.update(
                "update battle_participant set active_member_id = null where battle_id = ?",
                battleId);
        List<BattleWin> wins =
                battles.findMemberWinsJudgedBetween(
                        judgedAt.minusMinutes(1), judgedAt.plusMinutes(1));
        assertThat(wins).isEmpty();

        long c = runner();
        FriendInviteIssue open = invites.invite(c, StandardDistance.KM5);
        assertThat(inviteRepository.findDuePending(open.expiresAt().minusSeconds(1), 10)).isEmpty();
        assertThat(inviteRepository.findDuePending(open.expiresAt(), 10))
                .extracting(FriendInvite::getId)
                .containsExactly(open.inviteId());
        assertThat(inviteRepository.existsOpen(c, open.expiresAt().minusSeconds(1))).isTrue();
        assertThat(inviteRepository.existsOpen(c, open.expiresAt())).isFalse();
        assertThat(invites.expireDue(open.expiresAt())).isEqualTo(1);
        assertThat(inviteRepository.findById(open.inviteId()).orElseThrow().getStatus())
                .isEqualTo(FriendInviteStatus.EXPIRED);
    }
}
