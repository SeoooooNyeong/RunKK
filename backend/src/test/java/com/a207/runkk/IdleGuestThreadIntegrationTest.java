package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;

import com.a207.runkk.battle.ActiveBattleGuard;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.crew.CrewCreateCommand;
import com.a207.runkk.crew.CrewService;
import com.a207.runkk.crew.CrewVisibility;
import com.a207.runkk.crew.JoinPolicy;
import com.a207.runkk.crew.JoinTarget;
import com.a207.runkk.crew.MembershipService;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.GuestStartForm;
import com.a207.runkk.member.SignUpService;
import com.a207.runkk.member.WithdrawalService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * T-25 미이용 게스트 파기 스레드(test.md 03 TC-6661~TC-6663·TC-6874, FR-200, D-118, D-141, D-156, D-165): 실
 * PostgreSQL 위에서 WithdrawalService.withdrawIdleGuests → IdleGuestWithdrawalGuard(ActiveBattleGuard)
 * → MemberWithdrawn → CrewWithdrawalListener(크루장 자동 양도·혼자면 크루 삭제). Docker가 없는 PC에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("미이용 게스트 파기 스레드 통합(T-25)")
class IdleGuestThreadIntegrationTest {

    /** 파기 작업 시각. 회원 행은 지금 만들어지므로(탈퇴 일시 ≥ 생성 일시) 하루 뒤 3시로 둔다. */
    private static final LocalDateTime N =
            LocalDateTime.now().plusDays(1).withHour(3).withMinute(0).withSecond(0).withNano(0);

    private static final AtomicInteger NAMES = new AtomicInteger();

    @MockitoSpyBean ActiveBattleGuard activeBattleGuard;
    @Autowired SignUpService signUps;
    @Autowired WithdrawalService withdrawals;
    @Autowired CrewService crews;
    @Autowired MembershipService memberships;
    @Autowired JdbcTemplate jdbc;

    private long guest(LocalDateTime lastAccess) {
        long id =
                signUps.startAsGuest(
                                new GuestStartForm(
                                        Map.of(
                                                ConsentItem.AGE_14_PLUS, true,
                                                ConsentItem.TERMS, true,
                                                ConsentItem.PRIVACY, true,
                                                ConsentItem.LOCATION, true)))
                        .memberId();
        jdbc.update(
                "update member set last_access_at = ? where id = ?",
                Timestamp.valueOf(lastAccess),
                id);
        return id;
    }

    private void joinedAt(long crewId, long memberId, LocalDateTime at, String role) {
        jdbc.update(
                "update crew_membership set joined_at = ?, role = ? where crew_id = ? and member_id"
                        + " = ? and left_at is null",
                Timestamp.valueOf(at),
                role,
                crewId,
                memberId);
    }

    private long crew(long leader) {
        return crews.create(
                        leader,
                        new CrewCreateCommand(
                                "게스트크루" + NAMES.incrementAndGet(),
                                "함께",
                                Set.of(PersonalityTag.SOCIAL_RUN),
                                CrewVisibility.PUBLIC,
                                JoinPolicy.FREE,
                                null))
                .crewId();
    }

    private String status(long memberId) {
        return jdbc.queryForObject(
                "select status from member where id = ?", String.class, memberId);
    }

    private Optional<Long> leaderOf(long crewId) {
        return jdbc
                .queryForList(
                        "select member_id from crew_membership where crew_id = ? and role ="
                                + " 'LEADER' and left_at is null",
                        Long.class,
                        crewId)
                .stream()
                .findFirst();
    }

    private int idleGuestCount() {
        return jdbc.queryForObject(
                "select count(*) from member where account_type = 'GUEST' and status = 'ACTIVE'"
                        + " and last_access_at <= ?",
                Integer.class,
                Timestamp.valueOf(N.minusDays(365)));
    }

    @Test
    @DisplayName("TC-6661 365일 정각 포함 파기, 1초 모자란 게스트·연동 회원은 그대로, 감사 system:idle-guest")
    void boundary() {
        int before = idleGuestCount();
        long a = guest(N.minusDays(365).minusSeconds(1));
        long b = guest(N.minusDays(365));
        long c = guest(N.minusDays(365).plusSeconds(1));

        assertThat(withdrawals.withdrawIdleGuests(N)).isEqualTo(before + 2);
        assertThat(List.of(status(a), status(b), status(c)))
                .containsExactly("WITHDRAWN", "WITHDRAWN", "ACTIVE");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from audit_record where action = 'WITHDRAWAL'"
                                        + " and actor_id = 'system:idle-guest' and actor_role ="
                                        + " 'SYSTEM' and target_id in (?, ?)",
                                Integer.class,
                                String.valueOf(a),
                                String.valueOf(b)))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "select guest_credential_hash from member where id = ?",
                                String.class,
                                a))
                .isNull();
    }

    @Test
    @DisplayName("TC-6662 진행 중 대결이면 건너뛰고 다음 날 파기(가드는 IdleGuestWithdrawalGuard만)")
    void battleGuardSkipsUntilNextDay() {
        long g = guest(N.minusYears(2));
        doReturn(Optional.of(ReasonCode.BATTLE_IN_PROGRESS)).when(activeBattleGuard).check(eq(g));
        withdrawals.withdrawIdleGuests(N);
        assertThat(status(g)).isEqualTo("ACTIVE");

        doCallRealMethod().when(activeBattleGuard).check(anyLong());
        withdrawals.withdrawIdleGuests(N.plusDays(1));
        assertThat(status(g)).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("TC-6663·TC-6874 크루장 게스트: 운영진 우선·가입일 최소·동률은 ID 작은 쪽, 혼자면 크루 삭제")
    void leaderGuestCrews() {
        long g1 = guest(N.minusYears(2));
        long crewA = crew(g1);
        long m1 = guest(N);
        long m2 = guest(N);
        long r1 = guest(N);
        for (long m : List.of(m1, m2, r1)) {
            memberships.apply(m, JoinTarget.byCrew(crewA));
        }
        joinedAt(crewA, m1, LocalDateTime.of(2026, 3, 1, 9, 0), "MANAGER");
        joinedAt(crewA, m2, LocalDateTime.of(2026, 4, 1, 9, 0), "MANAGER");
        joinedAt(crewA, r1, LocalDateTime.of(2026, 1, 1, 9, 0), "MEMBER");

        long g2 = guest(N.minusYears(2));
        long crewE = crew(g2);
        long r5 = guest(N);
        long r4 = guest(N);
        memberships.apply(r5, JoinTarget.byCrew(crewE));
        memberships.apply(r4, JoinTarget.byCrew(crewE));
        LocalDateTime same = LocalDateTime.of(2026, 2, 1, 9, 0);
        joinedAt(crewE, r5, same, "MEMBER");
        joinedAt(crewE, r4, same, "MEMBER");
        long smaller = Math.min(r4, r5);

        long g3 = guest(N.minusYears(2));
        long crewC = crew(g3);

        withdrawals.withdrawIdleGuests(N);

        assertThat(leaderOf(crewA)).contains(m1);
        assertThat(leaderOf(crewE)).contains(smaller);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from crew where id = ?", Integer.class, crewC))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from audit_record where action = 'LEADER_TRANSFER'"
                                        + " and actor_role = 'SYSTEM' and target_id in (?, ?)",
                                Integer.class,
                                String.valueOf(m1),
                                String.valueOf(smaller)))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from notification where type ="
                                        + " 'LEADER_TRANSFERRED' and recipient_id in (?, ?)",
                                Integer.class,
                                m1,
                                smaller))
                .isEqualTo(2);
    }
}
