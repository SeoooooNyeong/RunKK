package com.a207.runkk.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.ProviderType;
import com.a207.runkk.member.ProviderUnavailableException;
import com.a207.runkk.member.SocialAccount;
import com.a207.runkk.member.SocialLoginGateway;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.OperatorWhitelist;
import com.a207.runkk.policy.SettingKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 운영자 확인(FR-173, D-42, D-50)과 첫 운영자 시드(code-review U10). */
@DisplayName("operator")
class OperatorTest {

    private final SocialLoginGateway kakao = mock(SocialLoginGateway.class);
    private final OperatorTokenIssuer tokens = mock(OperatorTokenIssuer.class);
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final OperatorAuthorizer authorizer =
            new OperatorAuthorizer(List.of(kakao), tokens, policy);
    private final OperatorId operator =
            new OperatorId(new SocialAccount(ProviderType.KAKAO, "op-1"));

    @BeforeEach
    void whitelist() {
        when(kakao.provider()).thenReturn(ProviderType.KAKAO);
        when(policy.operatorWhitelist())
                .thenReturn(new OperatorWhitelist(Map.of("KAKAO", Set.of("op-1"))));
        when(tokens.issue(operator)).thenReturn("admin-token");
    }

    @Test
    @DisplayName("authenticate: 화이트리스트 계정이면 관리툴 토큰과 actorId, 밖이면 403")
    void authenticate() {
        when(kakao.authenticate("good")).thenReturn(operator.account());
        OperatorSession session = authorizer.authenticate("kakao", "good");
        assertThat(session).isEqualTo(new OperatorSession("admin-token", "KAKAO:op-1"));

        when(kakao.authenticate("stranger"))
                .thenReturn(new SocialAccount(ProviderType.KAKAO, "someone"));
        assertThatThrownBy(() -> authorizer.authenticate("KAKAO", "stranger"))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("authenticate: 제공자 인증 실패 403, 무응답 PROVIDER_UNAVAILABLE, 모르는·없는 제공자 400")
    void authenticateFailures() {
        when(kakao.authenticate("forged")).thenThrow(new AuthFailureException("bad"));
        assertThatThrownBy(() -> authorizer.authenticate("KAKAO", "forged"))
                .isInstanceOf(PermissionDeniedException.class);
        when(kakao.authenticate("slow")).thenThrow(new ProviderUnavailableException("down"));
        assertThatThrownBy(() -> authorizer.authenticate("KAKAO", "slow"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> authorizer.authenticate("NAVER", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> authorizer.authenticate("GOOGLE", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ensureOperator: 토큰 검증 뒤 화이트리스트에서 빠졌으면 거부")
    void ensureOperator() {
        when(tokens.verify("admin-token")).thenReturn(operator);
        assertThat(authorizer.ensureOperator("admin-token").actorId()).isEqualTo("KAKAO:op-1");
        when(policy.operatorWhitelist()).thenReturn(new OperatorWhitelist(Map.of()));
        assertThatThrownBy(() -> authorizer.ensureOperator("admin-token"))
                .isInstanceOf(PermissionDeniedException.class);
        when(tokens.verify("forged")).thenThrow(new PermissionDeniedException("forged"));
        assertThatThrownBy(() -> authorizer.ensureOperator("forged"))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("시드: 화이트리스트가 비었고 환경 변수가 있을 때만 한 번 넣는다(감사는 change 경로)")
    void bootstrap() {
        OperationSettingService settings = mock(OperationSettingService.class);
        ObjectMapper json = new ObjectMapper();
        when(policy.operatorWhitelist()).thenReturn(new OperatorWhitelist(Map.of()));

        assertThat(new OperatorBootstrap(policy, settings, json, "").seedIfEmpty()).isFalse();
        assertThat(
                        new OperatorBootstrap(policy, settings, json, "kakao:1, GOOGLE:abc,KAKAO:2")
                                .seedIfEmpty())
                .isTrue();
        verify(settings)
                .change(
                        OperatorBootstrap.ACTOR_ID,
                        SettingKey.OPERATOR_WHITELIST,
                        "{\"accounts\":{\"GOOGLE\":[\"abc\"],\"KAKAO\":[\"1\",\"2\"]}}");

        OperationSettingService untouched = mock(OperationSettingService.class);
        assertThat(new OperatorBootstrap(policy, untouched, json, "NAVER:1").seedIfEmpty())
                .isFalse();
        assertThat(new OperatorBootstrap(policy, untouched, json, "KAKAO").seedIfEmpty()).isFalse();
        when(policy.operatorWhitelist())
                .thenReturn(new OperatorWhitelist(Map.of("KAKAO", Set.of("op-1"))));
        assertThat(new OperatorBootstrap(policy, untouched, json, "KAKAO:9").seedIfEmpty())
                .isFalse();
        verify(untouched, never()).change(anyString(), any(), anyString());
        verify(settings, never()).change(eq("x"), any(), anyString());
    }
}
