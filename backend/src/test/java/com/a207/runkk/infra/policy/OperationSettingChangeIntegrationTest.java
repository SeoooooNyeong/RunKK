package com.a207.runkk.infra.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.OperationSettingChanged;
import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingKey;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 운영 기준 변경 흐름(FR-174, 14장 T-11 일부): 실제 트랜잭션에서 값·이력·감사가 함께 커밋되고, 커밋 뒤 캐시가 무효화된 다음 사건
 * 수신자(AFTER_COMMIT)가 새 값을 읽는다. 거부되면 아무것도 남지 않는다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import({TestcontainersConfig.class, OperationSettingChangeIntegrationTest.ListenerConfig.class})
@ActiveProfiles("integration")
@org.junit.jupiter.api.DisplayName("운영 기준 변경 흐름(T-11 일부)")
class OperationSettingChangeIntegrationTest {

    /** battle.VirtualRecordService 같은 커밋 뒤 수신자를 흉내 낸다. */
    static class RecordingListener {
        final List<Object> seen = new CopyOnWriteArrayList<>();
        private final OperationPolicy policy;

        RecordingListener(OperationPolicy policy) {
            this.policy = policy;
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(OperationSettingChanged e) {
            seen.add(e.key());
            seen.add(policy.tierBoundary().lowerBounds().get(0));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ListenerConfig {
        @Bean
        RecordingListener recordingListener(OperationPolicy policy) {
            return new RecordingListener(policy);
        }
    }

    @Autowired OperationSettingService service;
    @Autowired OperationPolicy policy;
    @Autowired RecordingListener listener;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from operation_setting_history");
        jdbc.update("delete from operation_setting");
        jdbc.update("delete from audit_record");
        for (SettingKey key : SettingKey.values()) {
            policy.invalidate(key);
        }
        listener.seen.clear();
    }

    @Test
    void change_commitsValueHistoryAudit_thenListenerReadsNewValue() {
        service.change("KAKAO:42", SettingKey.TIER_BOUNDARY, "{\"lowerBounds\":[31,36,41,46]}");

        assertThat(count("operation_setting")).isEqualTo(1);
        assertThat(count("operation_setting_history")).isEqualTo(1);
        assertThat(
                        jdbc.queryForMap(
                                "select actor_id, actor_role, action, target_id from audit_record"))
                .containsEntry("actor_id", "KAKAO:42")
                .containsEntry("actor_role", "OPERATOR")
                .containsEntry("action", "SETTING_CHANGE")
                .containsEntry("target_id", "TIER_BOUNDARY");
        assertThat(listener.seen).containsExactly(SettingKey.TIER_BOUNDARY, 31.0);
        assertThat(policy.tierBoundary().lowerBounds()).containsExactly(31.0, 36.0, 41.0, 46.0);
    }

    @Test
    void rejectedChange_leavesNoRowsAndNoEvent() {
        assertThatThrownBy(
                        () ->
                                service.change(
                                        "KAKAO:42",
                                        SettingKey.LLM_DAILY_LIMIT,
                                        "{\"perMember\":1,\"global\":201}"))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(count("operation_setting")).isZero();
        assertThat(count("operation_setting_history")).isZero();
        assertThat(count("audit_record")).isZero();
        assertThat(listener.seen).isEmpty();
    }

    @Test
    void shopItems_deletingExistingId_isRejected() {
        service.change(
                "KAKAO:42",
                SettingKey.SHOP_ITEMS,
                "{\"items\":[{\"id\":1,\"name\":\"모자\",\"slot\":\"HAT\",\"price\":100,\"onSale\":true}]}");

        assertThatThrownBy(
                        () -> service.change("KAKAO:42", SettingKey.SHOP_ITEMS, "{\"items\":[]}"))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(policy.shopCatalog().find(1)).isPresent();
    }

    @Test
    void unreadableStoredValue_fallsBackToDefault() {
        jdbc.update(
                "insert into operation_setting (setting_key, setting_value, version, updated_at)"
                        + " values ('SELFIE_LIMIT', '{broken', 0, now())");
        policy.invalidate(SettingKey.SELFIE_LIMIT);

        assertThat(policy.selfieLimit().perDay()).isEqualTo(1);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
