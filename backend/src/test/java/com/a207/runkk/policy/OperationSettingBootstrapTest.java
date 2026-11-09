package com.a207.runkk.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 운영 기준값 시드(S15P31A207-222): RUNKK_BOOTSTRAP_SETTINGS("KEY=값,KEY=값")를 시작 때 행이 없는 키에만 한 번 넣는다. 관리툴로
 * 이미 바꾼 값(행 있음)은 덮지 않고, 모르는 키·형식 오류는 WARN만 남기고 건너뛰며 예외를 던지지 않는다. 변수가 없으면 아무것도 하지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("운영 기준값 시드(RUNKK_BOOTSTRAP_SETTINGS)")
class OperationSettingBootstrapTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 10, 0);

    private final InMemoryOperationSettingRepository repository =
            new InMemoryOperationSettingRepository();
    private final OperationPolicy policy = new OperationPolicy(repository);
    private final AuditService auditService = mock(AuditService.class);
    private final OperationSettingService service =
            new OperationSettingService(
                    repository,
                    policy,
                    auditService,
                    e -> {},
                    Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));

    private OperationSettingBootstrap bootstrap(String settings) {
        return new OperationSettingBootstrap(repository, service, settings);
    }

    private static long count(CapturedOutput output, String text) {
        return output.getAll().lines().filter(l -> l.contains(text)).count();
    }

    @Test
    @DisplayName("세 공개 스위치를 넣고 캐시가 새 값을 읽는다, 감사는 SETTING_CHANGE·시드 행위자")
    void seedsMissingKeys(CapturedOutput output) {
        assertThat(policy.features().crewReleased()).isFalse();

        assertThat(
                        bootstrap(
                                        " CREW_RELEASED=true, AVATAR_RELEASED = true"
                                                + " ,RUNBODY_ENABLED=true")
                                .seedMissing())
                .containsExactly(
                        SettingKey.CREW_RELEASED,
                        SettingKey.AVATAR_RELEASED,
                        SettingKey.RUNBODY_ENABLED);

        assertThat(repository.find(SettingKey.CREW_RELEASED))
                .map(OperationSetting::value)
                .contains("true");
        assertThat(policy.features().crewReleased()).isTrue();
        assertThat(policy.features().avatarReleased()).isTrue();
        assertThat(policy.features().runbodyEnabled()).isTrue();
        verify(auditService)
                .record(
                        new AuditEntry(
                                OperationSettingBootstrap.ACTOR_ID,
                                ActorRole.OPERATOR,
                                AuditAction.SETTING_CHANGE,
                                "RUNBODY_ENABLED",
                                "false",
                                "true"));
        assertThat(count(output, "setting_bootstrap seeded")).isEqualTo(3);
    }

    @Test
    @DisplayName("쉼표가 든 JSON 값도 다음 키 앞에서만 나눈다")
    void jsonValueWithComma() {
        assertThat(
                        bootstrap(
                                        "TIER_BOUNDARY={\"lowerBounds\":[31,36,41,46]},CREW_RELEASED=true")
                                .seedMissing())
                .containsExactly(SettingKey.TIER_BOUNDARY, SettingKey.CREW_RELEASED);
        assertThat(policy.tierBoundary().lowerBounds()).first().isEqualTo(31.0);
    }

    @Test
    @DisplayName("행이 이미 있으면(관리툴로 바꾼 값) 덮지 않는다")
    void existingRowIsKept(CapturedOutput output) {
        service.change("KAKAO:op", SettingKey.CREW_RELEASED, "false");

        assertThat(bootstrap("CREW_RELEASED=true,AVATAR_RELEASED=true").seedMissing())
                .containsExactly(SettingKey.AVATAR_RELEASED);

        assertThat(repository.find(SettingKey.CREW_RELEASED))
                .map(OperationSetting::value)
                .contains("false");
        assertThat(policy.features().crewReleased()).isFalse();
        assertThat(repository.histories()).hasSize(2);
        assertThat(count(output, "setting_bootstrap skipped=row_present key=CREW_RELEASED"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("모르는 키·형식이 틀린 값·= 없는 항목은 WARN 뒤 건너뛰고 나머지는 넣는다")
    void invalidEntriesAreSkipped(CapturedOutput output) {
        assertThat(
                        bootstrap(
                                        "NO_SUCH_KEY=true,CREW_RELEASED=yes,AVATAR_RELEASED,RUNBODY_ENABLED=true")
                                .seedMissing())
                .containsExactly(SettingKey.RUNBODY_ENABLED);

        assertThat(repository.find(SettingKey.CREW_RELEASED)).isEmpty();
        assertThat(repository.find(SettingKey.AVATAR_RELEASED)).isEmpty();
        assertThat(count(output, "setting_bootstrap_skipped")).isEqualTo(3);
        assertThat(output.getAll().lines().filter(l -> l.contains("setting_bootstrap")))
                .noneMatch(l -> l.contains("ERROR"));
    }

    @Test
    @DisplayName("변수가 없거나 빈 문자열(compose 기본값)이면 로그 없이 무동작")
    void missingOrBlank(CapturedOutput output) {
        assertThat(bootstrap(null).seedMissing()).isEmpty();
        assertThat(bootstrap("  ").seedMissing()).isEmpty();
        assertThat(count(output, "setting_")).isZero();
        assertThat(repository.findAll()).isEmpty();
    }
}
