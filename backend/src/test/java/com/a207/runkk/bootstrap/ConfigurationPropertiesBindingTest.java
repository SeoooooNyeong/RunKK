package com.a207.runkk.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.analysis.AiCommentGenerator;
import com.a207.runkk.batch.battle.BattleSchedulingConfig;
import com.a207.runkk.batch.crew.CrewSchedulingConfig;
import com.a207.runkk.batch.member.MemberSchedulingConfig;
import com.a207.runkk.batch.reward.RewardSchedulingConfig;
import com.a207.runkk.infra.integration.auth.AuthSecrets;
import com.a207.runkk.member.LegalDocumentCatalog;
import com.a207.runkk.operator.OperatorBootstrap;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 코드 리뷰 2차 L1: 값 주입(@Value)을 옮긴 기능별 설정 record가 application.yml·test 프로필 값과 기본값을 예전과 같게 받는다. 크루런 시계는
 * test 프로필에서 꺼지고, 비운 키는 빈 문자열, 기본값이 있던 간격은 그 값이다.
 */
@SpringBootTest(classes = RunkkApplication.class)
@ActiveProfiles("test")
@DisplayName("기능별 설정 record 바인딩(L1)")
class ConfigurationPropertiesBindingTest {

    @Autowired CrewSchedulingConfig.Schedule crew;
    @Autowired MemberSchedulingConfig.Schedule member;
    @Autowired RewardSchedulingConfig.Schedule reward;
    @Autowired BattleSchedulingConfig.Schedule battle;
    @Autowired AiCommentGenerator.Ai ai;
    @Autowired AuthSecrets secrets;
    @Autowired LegalDocumentCatalog.Links links;
    @Autowired OperatorBootstrap.Seed seed;

    @Test
    @DisplayName("시각 작업: test 프로필 크루런 시계 끔, cron·간격은 application.yml 값")
    void batchSchedules() {
        assertThat(crew.crewTickEnabled()).isFalse();
        assertThat(crew.crewTickInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(crew.weeklyRaceReconcileCron()).isEqualTo("0 30 0 * * *");
        assertThat(crew.crewRunLinkReconcileCron()).isEqualTo("0 10 0 * * *");
        assertThat(member.revocationPurgeCron()).isEqualTo("0 0 3 * * *");
        assertThat(member.idleGuestWithdrawalCron()).isEqualTo("0 0 3 * * *");
        assertThat(reward.rewardReconcileCron()).isEqualTo("0 10 0 * * *");
        assertThat(battle.battleAbandonInterval()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("연동·문서: LLM 대기 3초, 비운 서명 키·시드는 빈 문자열, 법정 고지 주소")
    void integrationsAndDocuments() {
        assertThat(ai.commentTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(secrets.jwtSecret()).isEmpty();
        assertThat(secrets.toString()).doesNotContain("jwt");
        assertThat(seed.operators()).as("변수가 없으면 null(D-166)").isNull();
        assertThat(links.termsUrl()).isEqualTo("https://k15a207.p.ssafy.io/legal/terms");
    }
}
