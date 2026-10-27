package com.a207.runkk.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 설계 v0.11 감사 행위·행위자 역할(F.5.1, 06-05). TC-6202. */
@DisplayName("v0.11 감사 행위·행위자 역할")
class AuditEnumsV011Test {

    @Test
    @DisplayName("감사 행위는 19개(운영진 지정·해제, 채팅 강제 삭제, 신고 숨김·기각 추가)")
    void auditActions() {
        assertThat(AuditAction.values())
                .hasSize(19)
                .contains(
                        AuditAction.MANAGER_APPOINT,
                        AuditAction.MANAGER_DISMISS,
                        AuditAction.CHAT_FORCE_DELETE,
                        AuditAction.REPORT_HIDE,
                        AuditAction.REPORT_DISMISS);
        assertThat(AuditAction.values()).allMatch(a -> a.name().length() <= 30);
    }

    @Test
    @DisplayName("행위자 역할은 RUNNER, CREW_LEADER, CREW_MANAGER, OPERATOR, SYSTEM")
    void actorRoles() {
        assertThat(ActorRole.values())
                .containsExactly(
                        ActorRole.RUNNER,
                        ActorRole.CREW_LEADER,
                        ActorRole.CREW_MANAGER,
                        ActorRole.OPERATOR,
                        ActorRole.SYSTEM);
    }
}
