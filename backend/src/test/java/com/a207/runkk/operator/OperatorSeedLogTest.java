package com.a207.runkk.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.OperatorWhitelist;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * D-166·부록 E K-5(test.md TC-2527·TC-4274·TC-4275): 시드 값이 없으면 로그 없이 false, 빈 문자열·소문자 제공자·형식 오류는 저장
 * 0과 WARN 1줄이고 예외를 던지지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("운영자 시드 로그(D-166)")
class OperatorSeedLogTest {

    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final OperationSettingService settings = mock(OperationSettingService.class);

    private boolean seed(String value) {
        when(policy.operatorWhitelist()).thenReturn(new OperatorWhitelist(Map.of()));
        return new OperatorBootstrap(policy, settings, new ObjectMapper(), value).seedIfEmpty();
    }

    private static long count(CapturedOutput output, String text) {
        return output.getAll().lines().filter(l -> l.contains(text)).count();
    }

    @Test
    @DisplayName("null: 로그 없음")
    void missing_isSilent(CapturedOutput output) {
        assertThat(seed(null)).isFalse();
        assertThat(count(output, "operator_")).isZero();
    }

    @Test
    @DisplayName("빈 문자열(공백만): WARN operator_seed_empty 1줄")
    void blank_warnsOnce(CapturedOutput output) {
        assertThat(seed("   ")).isFalse();
        assertThat(count(output, "operator_seed_empty")).isEqualTo(1);
        assertThat(output.getAll()).contains("WARN");
    }

    @Test
    @DisplayName("소문자 제공자·형식 오류: WARN 1줄, ERROR 없음")
    void invalid_warnsOnce(CapturedOutput output) {
        assertThat(seed("kakao:k-op")).isFalse();
        assertThat(seed("KAKAO")).isFalse();
        assertThat(count(output, "operator_bootstrap_failed")).isEqualTo(2);
        assertThat(output.getAll()).doesNotContain("ERROR");
    }
}
