package com.a207.runkk;

import static com.a207.runkk.running.RunFixtures.START;
import static com.a207.runkk.running.RunFixtures.id;
import static com.a207.runkk.running.RunFixtures.upload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.TierLevelService;
import com.a207.runkk.running.RunUploadCommand;
import com.a207.runkk.running.RunUploadService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 14장 T-10(B2 범위): 3회째 저장 뒤 TierUpdateListener 처리가 실패해도 4회째 저장에서 건너뛴 판정 지점 3을 놓치지 않고 티어를
 * 배치한다(D-40). Runimal 쪽 임시 유형은 B3(runimal)에서 이어서 확인한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
class TierJudgementRecoveryIntegrationTest {

    @MockitoSpyBean TierLevelService tierLevelService;
    @Autowired RunUploadService uploads;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        for (String table :
                List.of(
                        "personal_best",
                        "personal_record_book",
                        "run_record",
                        "member_consent",
                        "member",
                        "audit_record")) {
            jdbc.update("delete from " + table);
        }
    }

    @Test
    void t10_failedThirdJudgement_isRecoveredByFourthRun() {
        long m =
                tx.execute(
                        s ->
                                members.save(
                                                MemberFixtures.member(
                                                        "티어곰", LocalDateTime.now().minusDays(30)))
                                        .getId());
        doThrow(new IllegalStateException("수신자 장애"))
                .when(tierLevelService)
                .recordValidRun(anyLong(), argThat(input -> input.validRunOrdinal() == 3));

        for (int i = 1; i <= 3; i++) {
            uploads.upload(
                    m, new RunUploadCommand(upload(id(i), START.plusHours(i), 5_000, 1_500)));
        }
        assertThat(members.findById(m).orElseThrow().getTier()).isEqualTo(Tier.UNRANKED);

        uploads.upload(m, new RunUploadCommand(upload(id(4), START.plusHours(4), 5_000, 1_500)));
        assertThat(members.findById(m).orElseThrow().getTier()).isEqualTo(Tier.GOLD);
    }
}
