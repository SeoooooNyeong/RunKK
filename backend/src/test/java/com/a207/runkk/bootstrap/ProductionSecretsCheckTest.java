package com.a207.runkk.bootstrap;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("prod 필수 비밀값 확인")
class ProductionSecretsCheckTest {

    private static final String MEMBER_KEY = "m".repeat(32);
    private static final String ADMIN_KEY = "a".repeat(32);

    private static MockEnvironment prod() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        return env;
    }

    @Test
    @DisplayName("prod에서 JWT_SECRET·ADMIN_JWT_SECRET·RUNKK_INTERNAL_TOKEN이 비면 기동을 멈추고 빈 이름을 알린다")
    void prod_missingSecrets_fails() {
        assertThatThrownBy(() -> new ProductionSecretsCheck(prod()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET 비어 있음")
                .hasMessageContaining("ADMIN_JWT_SECRET 비어 있음")
                .hasMessageContaining("RUNKK_INTERNAL_TOKEN 비어 있음");
    }

    @Test
    @DisplayName("prod에서 짧은 키·같은 키는 거부, 모두 있으면 통과")
    void prod_weakOrSameKeys() {
        MockEnvironment weak =
                prod().withProperty(ProductionSecretsCheck.JWT_SECRET, "short")
                        .withProperty(ProductionSecretsCheck.ADMIN_JWT_SECRET, MEMBER_KEY)
                        .withProperty(ProductionSecretsCheck.INTERNAL_TOKEN, "t");
        assertThatThrownBy(() -> new ProductionSecretsCheck(weak))
                .hasMessageContaining("JWT_SECRET 32바이트 미만");
        MockEnvironment same =
                prod().withProperty(ProductionSecretsCheck.JWT_SECRET, MEMBER_KEY)
                        .withProperty(ProductionSecretsCheck.ADMIN_JWT_SECRET, MEMBER_KEY)
                        .withProperty(ProductionSecretsCheck.INTERNAL_TOKEN, "t");
        assertThatThrownBy(() -> new ProductionSecretsCheck(same)).hasMessageContaining("같음");
        MockEnvironment ok =
                prod().withProperty(ProductionSecretsCheck.JWT_SECRET, MEMBER_KEY)
                        .withProperty(ProductionSecretsCheck.ADMIN_JWT_SECRET, ADMIN_KEY)
                        .withProperty(ProductionSecretsCheck.INTERNAL_TOKEN, "internal");
        assertThatCode(() -> new ProductionSecretsCheck(ok)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("local·test·develop은 비어도 뜬다")
    void otherProfiles_allowEmpty() {
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("local");
        assertThatCode(() -> new ProductionSecretsCheck(local)).doesNotThrowAnyException();
        MockEnvironment develop = new MockEnvironment();
        develop.setActiveProfiles("develop");
        assertThatCode(() -> new ProductionSecretsCheck(develop)).doesNotThrowAnyException();
    }
}
