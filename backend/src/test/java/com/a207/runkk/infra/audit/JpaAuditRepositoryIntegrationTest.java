package com.a207.runkk.infra.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditRecord;
import com.a207.runkk.audit.AuditRecordView;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.DateRange;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 14장 순서 2: infra.audit을 실제 PostgreSQL(Flyway V1)로 확인한다. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@org.junit.jupiter.api.DisplayName("감사 저장소(PostgreSQL)")
class JpaAuditRepositoryIntegrationTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 20, 12, 0);

    @Autowired JpaAuditRepository repository;
    @Autowired AuditService auditService;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from audit_record");
    }

    private void append(AuditAction action, LocalDateTime at) {
        tx.executeWithoutResult(
                s ->
                        repository.append(
                                AuditRecord.of(
                                        new AuditEntry(
                                                "7", ActorRole.RUNNER, action, "crew-1", null, "x"),
                                        at)));
    }

    @Test
    void find_filtersByDateRangeAndAction_newestFirst() {
        append(AuditAction.KICK, T.minusDays(2));
        append(AuditAction.CREW_DELETE, T.minusDays(1));
        append(AuditAction.KICK, T);
        append(AuditAction.KICK, LocalDate.of(2026, 10, 21).atStartOfDay());

        DateRange range = new DateRange(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 20));
        assertThat(auditService.query(range, Optional.empty()))
                .extracting(AuditRecordView::at)
                .containsExactly(T, T.minusDays(1), T.minusDays(2));
        assertThat(auditService.query(range, Optional.of(AuditAction.KICK)))
                .extracting(AuditRecordView::at)
                .containsExactly(T, T.minusDays(2));
    }

    @Test
    void storedRecord_keepsAllFields() {
        append(AuditAction.KICK, T);

        AuditRecordView view =
                auditService
                        .query(new DateRange(T.toLocalDate(), T.toLocalDate()), Optional.empty())
                        .get(0);
        assertThat(view)
                .isEqualTo(
                        new AuditRecordView(
                                T, "7", ActorRole.RUNNER, AuditAction.KICK, "crew-1", null, "x"));
    }

    @Test
    void deleteOlderThan_removesOnlyOlderRecords() {
        append(AuditAction.KICK, T.minusMonths(7));
        append(AuditAction.KICK, T.minusMonths(5));

        int deleted = tx.execute(s -> repository.deleteOlderThan(T.minusMonths(6)));

        assertThat(deleted).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_record", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void table_isAppendOnly_updateRejectedByTrigger() {
        append(AuditAction.KICK, T);

        assertThatThrownBy(() -> jdbc.update("update audit_record set target_id = 'forged'"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void record_joinsCallerTransaction_andRollsBackWithIt() {
        assertThatThrownBy(
                        () ->
                                tx.executeWithoutResult(
                                        s -> {
                                            auditService.record(
                                                    new AuditEntry(
                                                            "op",
                                                            ActorRole.OPERATOR,
                                                            AuditAction.SETTING_CHANGE,
                                                            "K",
                                                            "a",
                                                            "b"));
                                            throw new IllegalStateException("caller failed");
                                        }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("select count(*) from audit_record", Integer.class))
                .isZero();
    }
}
