package com.a207.runkk.infra.avatar;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class SelfieJobMigrationTest {
    @Test
    void v36AddsNullableReferencesWithoutChangingLegacyFace() throws Exception {
        try (var db =
                DriverManager.getConnection(
                        "jdbc:h2:mem:selfie_migration;MODE=PostgreSQL", "sa", "")) {
            try (var sql = db.createStatement()) {
                sql.execute("create table member(id bigint primary key)");
                sql.execute(
                        "create table avatar(member_id bigint primary key, default_character"
                                + " varchar(20))");
                sql.execute(
                        "create table avatar_face_part(member_id bigint, part varchar(10),"
                                + " image_key varchar(200))");
                sql.execute("insert into member values(1)");
                sql.execute("insert into avatar values(1, 'FOX')");
                sql.execute("insert into avatar_face_part values(1, 'EYES', 'face/1/old/EYES')");
            }
            ScriptUtils.executeSqlScript(
                    db, new ClassPathResource("db/migration/V36__async_selfie_jobs.sql"));
            try (var sql = db.createStatement();
                    var rows = sql.executeQuery("select * from avatar")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("default_character")).isEqualTo("FOX");
                assertThat(rows.getString("head_job_id")).isNull();
                assertThat(rows.getString("pending_face_request")).isNull();
            }
            try (var sql = db.createStatement();
                    var rows = sql.executeQuery("select image_key from avatar_face_part")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("face/1/old/EYES");
            }
        }
    }
}
