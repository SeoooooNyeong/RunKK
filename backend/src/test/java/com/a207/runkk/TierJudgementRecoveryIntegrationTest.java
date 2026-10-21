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
import com.a207.runkk.runimal.RunimalService;
import com.a207.runkk.running.RunUploadCommand;
import com.a207.runkk.running.RunUploadService;
import java.time.Duration;
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
 * 14장 T-10: 3회째 저장 뒤 TierUpdateListener·RunimalService 처리가 실패해도 4회째 저장에서 건너뛴 판정 지점 3을 놓치지 않고 티어를
 * 배치하고 임시 Runimal 유형을 준다(D-40). RunimalService는 비동기 수신자라 결과를 기다린다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@org.junit.jupiter.api.DisplayName("건너뛴 티어 판정 지점 복구(T-10)")
class TierJudgementRecoveryIntegrationTest {

    private static final Duration ASYNC_WAIT = Duration.ofSeconds(15);
    private static final long POLL_MILLIS = 100;

    @MockitoSpyBean TierLevelService tierLevelService;
    @MockitoSpyBean RunimalService runimalService;
    @Autowired RunUploadService uploads;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        for (String table :
                List.of(
                        "runimal_profile",
                        "course_verdict",
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
    void t10_failedThirdJudgement_isRecoveredByFourthRun() throws InterruptedException {
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
        doThrow(new IllegalStateException("Runimal 수신자 장애"))
                .when(runimalService)
                .onRunSaved(argThat(e -> e.validRunOrdinal() == 3));

        for (int i = 1; i <= 3; i++) {
            uploads.upload(
                    m, new RunUploadCommand(upload(id(i), START.plusHours(i), 5_000, 1_500)));
        }
        assertThat(members.findById(m).orElseThrow().getTier()).isEqualTo(Tier.UNRANKED);

        uploads.upload(m, new RunUploadCommand(upload(id(4), START.plusHours(4), 5_000, 1_500)));
        assertThat(members.findById(m).orElseThrow().getTier()).isEqualTo(Tier.GOLD);
        assertThat(awaitProvisionalType(m)).isNotNull();
    }

    /** 비동기 RunimalService가 4회째 사건을 처리할 때까지 기다린다. */
    private String awaitProvisionalType(long memberId) throws InterruptedException {
        long deadline = System.nanoTime() + ASYNC_WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            List<String> types =
                    jdbc.queryForList(
                            "select provisional_type from runimal_profile where member_id = ?",
                            String.class,
                            memberId);
            if (!types.isEmpty() && types.get(0) != null) {
                return types.get(0);
            }
            Thread.sleep(POLL_MILLIS);
        }
        return null;
    }
}
