package com.a207.runkk.infra.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.policy.OperationSetting;
import com.a207.runkk.policy.SettingKey;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 14장 순서 2: infra.policy를 실제 PostgreSQL(Flyway V2)로 확인한다. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@org.junit.jupiter.api.DisplayName("운영 기준값 저장소(PostgreSQL)")
class JpaOperationSettingRepositoryIntegrationTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 20, 12, 0);

    @Autowired JpaOperationSettingRepository repository;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from operation_setting_history");
        jdbc.update("delete from operation_setting");
    }

    @Test
    void saveNew_thenFindAndFindAll() {
        tx.executeWithoutResult(
                s -> {
                    OperationSetting setting = OperationSetting.initial(SettingKey.SELFIE_LIMIT, T);
                    repository.saveHistory(setting.change("{\"perDay\":2}", "KAKAO:1", T));
                    repository.save(setting);
                });

        assertThat(repository.find(SettingKey.SELFIE_LIMIT)).isPresent();
        assertThat(repository.find(SettingKey.MATCH_CONDITION)).isEmpty();
        assertThat(repository.findAll()).hasSize(1);
        assertThat(
                        jdbc.queryForObject(
                                "select setting_value from operation_setting where setting_key = ?",
                                String.class,
                                "SELFIE_LIMIT"))
                .isEqualTo("{\"perDay\":2}");
        assertThat(
                        jdbc.queryForObject(
                                "select operator_id from operation_setting_history", String.class))
                .isEqualTo("KAKAO:1");
    }

    @Test
    void update_incrementsVersion() {
        tx.executeWithoutResult(
                s -> repository.save(OperationSetting.initial(SettingKey.SELFIE_LIMIT, T)));
        tx.executeWithoutResult(
                s -> {
                    OperationSetting loaded =
                            repository.find(SettingKey.SELFIE_LIMIT).orElseThrow();
                    loaded.change("{\"perDay\":5}", "KAKAO:1", T.plusMinutes(1));
                    repository.save(loaded);
                });

        assertThat(jdbc.queryForObject("select version from operation_setting", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void staleUpdate_isRejectedByOptimisticLock() {
        tx.executeWithoutResult(
                s -> repository.save(OperationSetting.initial(SettingKey.SELFIE_LIMIT, T)));
        OperationSetting stale = repository.find(SettingKey.SELFIE_LIMIT).orElseThrow();
        tx.executeWithoutResult(
                s -> {
                    OperationSetting fresh = repository.find(SettingKey.SELFIE_LIMIT).orElseThrow();
                    fresh.change("{\"perDay\":3}", "KAKAO:1", T);
                    repository.save(fresh);
                });

        stale.change("{\"perDay\":4}", "KAKAO:2", T);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> repository.save(stale)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
