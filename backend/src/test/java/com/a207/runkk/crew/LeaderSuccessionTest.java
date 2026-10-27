package com.a207.runkk.crew;

import static com.a207.runkk.crew.CrewFixtures.LEADER;
import static com.a207.runkk.crew.CrewFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 미이용 게스트 파기의 크루장 자동 양도 후임(test.md 2.1.10.5 TC-6049·TC-6050, 03 TC-6663·TC-6874, D-118, D-165):
 * 운영진(없으면 크루원) 중 가입일시가 가장 이른 사람, 같으면 회원 ID가 작은 쪽. 운영진도 크루장을 넘겨받을 수 있다.
 */
@DisplayName("크루장 자동 양도 후임")
class LeaderSuccessionTest {

    private static Crew crewWith(long... members) {
        Crew crew = CrewFixtures.crew("양도크루", members);
        return crew;
    }

    private static void set(Crew crew, long memberId, String field, Object value) {
        CrewMembership m = crew.membershipOf(memberId).orElseThrow();
        try {
            Field f = CrewMembership.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(m, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("TC-6049 운영진이 있으면 운영진 중 가입일 최소(더 이른 크루원보다 우선)")
    void managerFirst() {
        Crew crew = crewWith(2, 3, 4, 6);
        set(crew, 2, "joinedAt", LocalDateTime.of(2026, 2, 1, 0, 0));
        set(crew, 3, "joinedAt", LocalDateTime.of(2026, 1, 15, 0, 0));
        set(crew, 4, "joinedAt", LocalDateTime.of(2026, 3, 1, 0, 0));
        set(crew, 6, "joinedAt", LocalDateTime.of(2026, 4, 1, 0, 0));
        set(crew, 2, "role", CrewRole.MANAGER);
        set(crew, 6, "role", CrewRole.MANAGER);

        long successor = CrewWithdrawalListener.successorOf(crew, LEADER);
        assertThat(successor).isEqualTo(2);
        crew.transferLeadership(LEADER, successor);
        assertThat(crew.relationOf(2)).isEqualTo(CrewRelation.LEADER);
        assertThat(crew.relationOf(LEADER)).isEqualTo(CrewRelation.MEMBER);
    }

    @Test
    @DisplayName("TC-6050 운영진이 없으면 크루원 중 가입일 최소, 같으면 회원 ID가 작은 쪽")
    void earliestMemberThenSmallerId() {
        Crew crew = crewWith(4, 3, 2);
        set(crew, 2, "joinedAt", NOW.plusDays(1));
        set(crew, 3, "joinedAt", LocalDateTime.of(2026, 1, 15, 9, 0));
        set(crew, 4, "joinedAt", LocalDateTime.of(2026, 1, 15, 9, 0));
        assertThat(CrewWithdrawalListener.successorOf(crew, LEADER)).isEqualTo(3);

        Crew managers = crewWith(9, 8);
        set(managers, 8, "role", CrewRole.MANAGER);
        set(managers, 9, "role", CrewRole.MANAGER);
        assertThat(CrewWithdrawalListener.successorOf(managers, LEADER)).isEqualTo(8);
    }
}
