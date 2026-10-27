package com.a207.runkk.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 2.1.10.6 Crew.appointManager·dismissManager와 운영진 상한(FR-204, D-98). 크루 7: 1 LEADER, 2 MANAGER, 3·4
 * MEMBER, 9 크루 밖.
 */
@DisplayName("크루 운영진 지정·해제(TC-6052~TC-6057)")
class CrewManagerTest {

    private static final LocalDateTime N = LocalDateTime.of(2026, 10, 22, 19, 0);

    /** 2를 운영진으로 둔 크루 7. */
    static Crew crew7() {
        Crew crew = Crew.create(1, CrewFixtures.profile("크루칠"), CrewFixtures.code("ABC777"), N);
        crew.admit(2, N);
        crew.admit(3, N);
        crew.admit(4, N);
        crew.appointManager(2);
        return crew;
    }

    private static ReasonCode reasonOf(Throwable e) {
        return ((BusinessRuleException) e).reason();
    }

    @Test
    @DisplayName("TC-6052: 소속 아님·크루장 본인·이미 운영진은 TARGET_INVALID, 운영진 수 그대로")
    void appoint_invalidTargets() {
        Crew crew = crew7();
        for (long target : new long[] {9, 1, 2}) {
            assertThatThrownBy(() -> crew.appointManager(target))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.TARGET_INVALID));
        }
        assertThat(crew.managerCount()).isEqualTo(1);
        assertThat(crew.relationOf(2)).isEqualTo(CrewRelation.MANAGER);
    }

    @Test
    @DisplayName("TC-6053: 운영진 0·1·2명 경계 — 셋째 지정은 MANAGER_LIMIT")
    void appoint_limit() {
        Crew crew = crew7();
        crew.admit(6, N);
        crew.dismissManager(2);
        assertThat(crew.managerCount()).isZero();

        crew.appointManager(3);
        assertThat(crew.managerCount()).isEqualTo(1);
        crew.appointManager(4);
        assertThat(crew.managerCount()).isEqualTo(2);
        assertThatThrownBy(() -> crew.appointManager(6))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.MANAGER_LIMIT));
        assertThat(crew.managerCount()).isEqualTo(2);
        assertThat(crew.relationOf(6)).isEqualTo(CrewRelation.MEMBER);
    }

    @Test
    @DisplayName("TC-6054: 해제는 소속 MANAGER만, 해제 뒤 상한 자리가 빈다")
    void dismiss() {
        Crew crew = crew7();
        assertThatThrownBy(() -> crew.dismissManager(9))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.TARGET_INVALID));
        assertThatThrownBy(() -> crew.dismissManager(3))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.TARGET_INVALID));
        crew.appointManager(3);
        crew.dismissManager(2);
        assertThat(crew.relationOf(2)).isEqualTo(CrewRelation.MEMBER);
        crew.appointManager(4);
        assertThat(crew.managerCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-6055: 운영진에게 양도하면 운영진 자리가 비고 이전 크루장은 MEMBER, 크루 밖이면 TARGET_INVALID")
    void transferToManager() {
        Crew crew = crew7();
        assertThatThrownBy(() -> crew.transferLeadership(1, 9))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.TARGET_INVALID));
        crew.transferLeadership(1, 2);
        assertThat(crew.relationOf(2)).isEqualTo(CrewRelation.LEADER);
        assertThat(crew.relationOf(1)).isEqualTo(CrewRelation.MEMBER);
        assertThat(crew.managerCount()).isZero();
        assertThat(crew.leaderId()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-6056: 운영진이 떠나면 역할도 사라진다")
    void managerLeaves() {
        Crew crew = crew7();
        assertThat(crew.relationOf(2)).isEqualTo(CrewRelation.MANAGER);
        crew.release(2, LeaveReason.WITHDREW, N);
        assertThat(crew.managerCount()).isZero();
        assertThat(crew.relationOf(1)).isEqualTo(CrewRelation.LEADER);
        assertThat(crew.relationOf(2)).isEqualTo(CrewRelation.OUTSIDER);
        assertThat(crew.relationOf(3)).isEqualTo(CrewRelation.MEMBER);
        assertThat(crew.relationOf(9)).isEqualTo(CrewRelation.OUTSIDER);
    }

    @Test
    @DisplayName(
            "TC-6057: CrewMembership.appointManager는 MEMBER만, promote는 LEADER로, demote는 MEMBER로")
    void membershipTransitions() {
        Crew crew = crew7();
        CrewMembership manager = crew.membershipOf(2).orElseThrow();
        CrewMembership leader = crew.membershipOf(1).orElseThrow();
        assertThatThrownBy(manager::appointManager).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(leader::appointManager).isInstanceOf(IllegalStateException.class);
        CrewMembership member = crew.membershipOf(3).orElseThrow();
        member.promote();
        assertThat(member.role()).isEqualTo(CrewRole.LEADER);
        manager.demote();
        assertThat(manager.role()).isEqualTo(CrewRole.MEMBER);
    }
}
