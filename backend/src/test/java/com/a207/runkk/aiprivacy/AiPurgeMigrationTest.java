package com.a207.runkk.aiprivacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class AiPurgeMigrationTest {
    @Test
    void v37KeepsExistingAuditAndAllowsOverdueAction() throws Exception {
        try (var db =
                DriverManager.getConnection(
                        "jdbc:h2:mem:ai_purge_migration;MODE=PostgreSQL", "sa", "")) {
            try (var sql = db.createStatement()) {
                sql.execute(
                        "create table audit_record(action varchar(30), constraint"
                                + " ck_audit_record_action check(action in ('WITHDRAWAL')))");
                sql.execute("insert into audit_record values('WITHDRAWAL')");
            }
            ScriptUtils.executeSqlScript(
                    db, new ClassPathResource("db/migration/V37__ai_data_purge.sql"));
            try (var sql = db.createStatement()) {
                sql.execute("insert into audit_record values('AI_DATA_PURGE_OVERDUE')");
                try (var result = sql.executeQuery("select count(*) from audit_record")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(2);
                }
                sql.execute(
                        "insert into ai_purge_request(member_id,status,requested_at,next_at)"
                                + " values(1,'PENDING',current_timestamp,current_timestamp)");
                try (var result =
                        sql.executeQuery("select retention_check_done from ai_purge_request")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBoolean(1)).isFalse();
                }
            }
        }
    }
}
