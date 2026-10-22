package com.a207.runkk.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DomainEvent;
import com.a207.runkk.common.DomainEventPublisher;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** OperationSettingService.change(FR-174): 값 검사 → 이력 → 감사 → 캐시 무효화 → OperationSettingChanged. */
@org.junit.jupiter.api.DisplayName("운영 기준 변경 서비스")
class OperationSettingServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 15, 0);

    private final InMemoryOperationSettingRepository repository =
            new InMemoryOperationSettingRepository();
    private final OperationPolicy policy = new OperationPolicy(repository);
    private final AuditService auditService = mock(AuditService.class);
    private final List<DomainEvent> published = new ArrayList<>();
    private final DomainEventPublisher publisher = published::add;
    private final OperationSettingService service =
            new OperationSettingService(
                    repository,
                    policy,
                    auditService,
                    publisher,
                    Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));

    @Test
    void change_savesValueHistoryAuditAndPublishes() {
        String before = SettingKey.TIER_BOUNDARY.defaultValue();
        String after = "{\"lowerBounds\":[31,36,41,46]}";
        assertThat(policy.tierBoundary().lowerBounds()).first().isEqualTo(30.0);

        SettingChangeResult result = service.change("KAKAO:123", SettingKey.TIER_BOUNDARY, after);

        assertThat(result)
                .isEqualTo(new SettingChangeResult(SettingKey.TIER_BOUNDARY, before, after));
        assertThat(repository.find(SettingKey.TIER_BOUNDARY))
                .map(OperationSetting::value)
                .contains(after);
        assertThat(repository.histories())
                .singleElement()
                .satisfies(
                        h -> {
                            assertThat(h.operatorId()).isEqualTo("KAKAO:123");
                            assertThat(h.changedAt()).isEqualTo(NOW);
                        });
        verify(auditService)
                .record(
                        new AuditEntry(
                                "KAKAO:123",
                                ActorRole.OPERATOR,
                                AuditAction.SETTING_CHANGE,
                                "TIER_BOUNDARY",
                                before,
                                after));
        assertThat(published)
                .containsExactly(new OperationSettingChanged(SettingKey.TIER_BOUNDARY, NOW));
        assertThat(policy.tierBoundary().lowerBounds()).first().isEqualTo(31.0);
    }

    @Test
    void change_twice_usesStoredValueAsBefore() {
        service.change("KAKAO:1", SettingKey.SELFIE_LIMIT, "{\"perDay\":2}");
        SettingChangeResult second =
                service.change("KAKAO:1", SettingKey.SELFIE_LIMIT, "{\"perDay\":3}");

        assertThat(second.before()).isEqualTo("{\"perDay\":2}");
        assertThat(repository.histories()).hasSize(2);
    }

    @Test
    void change_invalidValue_leavesEverythingUntouched() {
        assertThatThrownBy(
                        () ->
                                service.change(
                                        "KAKAO:1",
                                        SettingKey.LLM_DAILY_LIMIT,
                                        "{\"perMember\":1,\"global\":201}"))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(repository.find(SettingKey.LLM_DAILY_LIMIT)).isEmpty();
        assertThat(repository.histories()).isEmpty();
        verify(auditService, never()).record(any());
        assertThat(published).isEmpty();
        assertThat(policy.llmCallLimit().global()).isEqualTo(200);
    }

    @Test
    void operationSettingChanged_isDomainEventWithOccurredAt() {
        OperationSettingChanged event = new OperationSettingChanged(SettingKey.LANDMARKS, NOW);
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.key()).isEqualTo(SettingKey.LANDMARKS);
    }
}
