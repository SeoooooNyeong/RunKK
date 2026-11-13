package com.a207.runkk.infra.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.policy.OperationPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 운영 기준값 시드(S15P31A207-222) 통합: 실 PostgreSQL에서 runkk.bootstrap.settings(환경 변수
 * RUNKK_BOOTSTRAP_SETTINGS)를 주고 서버를 띄우면 시작 뒤 세 공개 스위치 행·감사 기록이 남고 캐시(OperationPolicy)가 참을 읽는다. 모르는
 * 키가 섞여도 기동은 계속된다. 속성이 달라 이 시험만의 컨텍스트(컨테이너)를 써서 다른 통합 시험의 기본값에 번지지 않는다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = RunkkApplication.class,
        properties =
                "runkk.bootstrap.settings=CREW_RELEASED=true,AVATAR_RELEASED=true,"
                        + "RUNBODY_ENABLED=true,NO_SUCH_KEY=1")
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("운영 기준값 시드 통합(RUNKK_BOOTSTRAP_SETTINGS)")
class OperationSettingBootstrapIntegrationTest {

    @Autowired OperationPolicy policy;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("시작 뒤 세 공개 스위치가 켜지고 시드 행위자 감사가 남는다")
    void seededOnStartup() {
        assertThat(policy.features().crewReleased()).isTrue();
        assertThat(policy.features().avatarReleased()).isTrue();
        assertThat(policy.features().runbodyEnabled()).isTrue();
        assertThat(
                        jdbc.queryForList(
                                "select setting_key from operation_setting where setting_value ="
                                        + " 'true'",
                                String.class))
                .containsExactlyInAnyOrder("CREW_RELEASED", "AVATAR_RELEASED", "RUNBODY_ENABLED");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from audit_record where actor_id = ?"
                                        + " and action = 'SETTING_CHANGE'",
                                Integer.class,
                                "bootstrap:RUNKK_BOOTSTRAP_SETTINGS"))
                .isEqualTo(3);
    }
}
