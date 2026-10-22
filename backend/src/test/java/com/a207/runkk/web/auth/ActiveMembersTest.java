package com.a207.runkk.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberWithdrawn;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("탈퇴 회원 토큰 거부 캐시(L21)")
class ActiveMembersTest {

    @Test
    @DisplayName("DB 확인과 캐시 저장 사이에 탈퇴가 끼어도 낡은 ACTIVE가 남지 않는다")
    void withdrawalDuringLookup_isNotOverwritten() {
        MemberQueryService members = mock(MemberQueryService.class);
        ActiveMembers active = new ActiveMembers(members, Clock.systemUTC());
        when(members.isActive(7L))
                .thenAnswer(
                        inv -> {
                            active.onMemberWithdrawn(new MemberWithdrawn(7L, LocalDateTime.now()));
                            return true; // 탈퇴 커밋 직전에 읽은 값
                        });
        active.isActive(7L);
        assertThat(active.isActive(7L)).isFalse();
    }
}
