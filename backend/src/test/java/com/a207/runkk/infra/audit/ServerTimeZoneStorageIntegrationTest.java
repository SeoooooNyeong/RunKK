package com.a207.runkk.infra.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditRecord;
import com.a207.runkk.audit.AuditRecordView;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.DateRange;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 서버 시각 저장 계약(DF-073, srs [임시:NFR-성-1]): 서버가 JPA로 쓴 LocalDateTime은 DB에 한국 시각 벽시계 값 그대로 남고, SQL로 넣은
 * 한국 시각은 서버가 같은 값으로 읽는다. hibernate.jdbc.time_zone을 두지 않았으므로 JVM 기본 시간대가 UTC여도(옛 운영 컨테이너) 값이 옮겨지지
 * 않는다. 감사 저장소를 대표로 쓴다. Docker가 없으면 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("서버 시각 저장 계약(PostgreSQL)")
class ServerTimeZoneStorageIntegrationTest {

    private static final LocalDateTime KST = LocalDateTime.of(2026, 11, 9, 14, 29, 0);

    @Autowired JpaAuditRepository repository;
    @Autowired AuditService auditService;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from audit_record");
    }

    private void append(LocalDateTime at) {
        tx.executeWithoutResult(
                s ->
                        repository.append(
                                AuditRecord.of(
                                        new AuditEntry(
                                                "7",
                                                ActorRole.RUNNER,
                                                AuditAction.CONSENT_CHANGE,
                                                "7",
                                                null,
                                                "x"),
                                        at)));
    }

    private String storedText() {
        return jdbc.queryForObject("select recorded_at::text from audit_record", String.class);
    }

    private LocalDateTime readByServer() {
        return auditService
                .query(new DateRange(KST.toLocalDate(), KST.toLocalDate()), Optional.empty())
                .stream()
                .map(AuditRecordView::at)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("서버가 쓴 한국 시각은 DB에 그대로 저장되고 같은 값으로 읽힌다")
    void serverWrite_isStoredAsKoreanWallClock() {
        append(KST);

        assertThat(storedText()).isEqualTo("2026-11-09 14:29:00");
        assertThat(readByServer()).isEqualTo(KST);
    }

    @Test
    @DisplayName("JVM 기본 시간대가 UTC여도 서버가 쓴 값은 옮겨지지 않는다(jdbc.time_zone 없음)")
    void serverWrite_doesNotShift_evenWhenJvmIsUtc() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            append(KST);
            assertThat(storedText()).isEqualTo("2026-11-09 14:29:00");
            assertThat(readByServer()).isEqualTo(KST);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("SQL로 넣은 한국 시각(시드, 운영자 SQL)을 서버가 같은 시각으로 읽는다")
    void sqlInsertedKoreanTime_isReadAsIs() {
        jdbc.update(
                "insert into audit_record (recorded_at, actor_id, actor_role, action, target_id)"
                        + " values (timestamp '2026-11-09 14:29:00', 'op', 'OPERATOR',"
                        + " 'SETTING_CHANGE', 'K')");

        assertThat(readByServer()).isEqualTo(KST);
    }

    @Test
    @DisplayName("서버 연결의 DB 세션 시간대는 Asia/Seoul이다(SQL now()가 한국 시각)")
    void serverSession_isKorean() {
        assertThat(jdbc.queryForObject("show timezone", String.class)).isEqualTo("Asia/Seoul");
    }
}
