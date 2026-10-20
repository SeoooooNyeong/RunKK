package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;

import archfixture.ArchFixtureEntityWithSetter;
import com.a207.runkk.audit.ArchFixtureAuditUsesWeb;
import com.a207.runkk.battle.live.ArchFixtureLiveMessage;
import com.a207.runkk.common.ArchFixtureCommonPort;
import com.a207.runkk.infra.audit.ArchFixtureInfraAuditUsesPolicy;
import com.a207.runkk.policy.ArchFixturePolicyRepository;
import com.a207.runkk.realtime.ArchFixtureRealtimeUsesLive;
import com.a207.runkk.web.admin.setting.ArchFixtureSettingUsesRepository;
import com.a207.runkk.web.error.ArchFixtureWebValue;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/** 음성 시험: 시험 소스에만 있는 위반 클래스로 ArchitectureTest의 규칙이 실제로 위반을 잡는지 확인한다. */
class ArchitectureRuleNegativeTest {

    private static boolean violates(ArchRule rule, Class<?>... fixtures) {
        JavaClasses imported = new ClassFileImporter().importClasses(fixtures);
        return rule.evaluate(imported).hasViolation();
    }

    @Test
    void rule01_catchesDependencyMissingFrom12_2() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_1_ONLY_LISTED_DEPENDENCIES,
                                ArchFixtureInfraAuditUsesPolicy.class,
                                ArchFixturePolicyRepository.class))
                .isTrue();
    }

    @Test
    void rule01_allowsDependencyListedIn12_2() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_1_ONLY_LISTED_DEPENDENCIES,
                                ArchFixtureSettingUsesRepository.class,
                                ArchFixturePolicyRepository.class))
                .isFalse();
    }

    @Test
    void rule03_catchesDomainImportingWeb() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_3_CORE_DOES_NOT_KNOW_OUTER_LAYERS,
                                ArchFixtureAuditUsesWeb.class,
                                ArchFixtureWebValue.class))
                .isTrue();
    }

    @Test
    void rule04_catchesRepositoryUsedOutsideOwnerAndItsInfra() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_4_STORAGE_PORTS_STAY_INSIDE,
                                ArchFixtureSettingUsesRepository.class,
                                ArchFixturePolicyRepository.class))
                .isTrue();
    }

    @Test
    void rule06_catchesPortInterfaceInCommon() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_6_COMMON_STAYS_PLAIN,
                                ArchFixtureCommonPort.class))
                .isTrue();
    }

    @Test
    void rule07_catchesRealtimeImportingLiveMessage() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_7_LIVE_MESSAGES_STAY_INSIDE,
                                ArchFixtureRealtimeUsesLive.class,
                                ArchFixtureLiveMessage.class))
                .isTrue();
    }

    @Test
    void rule09_catchesPublicSetterOnEntity() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_9_ENTITIES_HAVE_NO_PUBLIC_SETTERS,
                                ArchFixtureEntityWithSetter.class))
                .isTrue();
    }

    @Test
    void rule10_catchesPublicFieldOnEntity() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_10_NO_PUBLIC_FIELDS,
                                ArchFixtureEntityWithSetter.class))
                .isTrue();
    }
}
