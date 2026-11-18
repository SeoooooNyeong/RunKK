package com.a207.runkk.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** S15P31A207-329: 이벤트 그림 주소를 해시 이름으로 바꾸되 운영자가 바꾼 주소는 두는지, 바뀐 이름의 파일이 실제로 있는지 본다. */
class EventVersionedImageMigrationTest {
    @Test
    void v69RenamesOnlyOriginalImageUrls() throws Exception {
        try (var db =
                DriverManager.getConnection(
                        "jdbc:h2:mem:event_versioned_images;MODE=PostgreSQL", "sa", "")) {
            try (var sql = db.createStatement()) {
                sql.execute(
                        "create table event(slug varchar(40), banner_image_url varchar(300),"
                                + " thumbnail_image_url varchar(300))");
                sql.execute(
                        "insert into event values('coach-challenge',"
                                + " '/events/coach-challenge/banner.png',"
                                + " '/events/coach-challenge/thumb.png')");
                sql.execute(
                        "insert into event values('beta-1km', '/events/beta-1km/custom.png',"
                                + " '/events/beta-1km/thumb.png')");
            }
            ScriptUtils.executeSqlScript(
                    db, new ClassPathResource("db/migration/V69__event_versioned_images.sql"));
            try (var sql = db.createStatement();
                    var rows =
                            sql.executeQuery(
                                    "select slug, banner_image_url, thumbnail_image_url from event"
                                            + " order by slug")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(2)).isEqualTo("/events/beta-1km/custom.png");
                assertThat(rows.getString(3)).isEqualTo("/events/beta-1km/thumb.ecb76844.png");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(2))
                        .isEqualTo("/events/coach-challenge/banner.57f8f71a.png");
                assertThat(rows.getString(3))
                        .isEqualTo("/events/coach-challenge/thumb.daab8bb1.png");
            }
        }
    }

    @Test
    void renamedImageFilesExistBesideOriginals() {
        Path stat = Path.of("..", "infra", "nginx", "static", "events");
        for (String name :
                new String[] {
                    "coach-challenge/banner.57f8f71a.png",
                    "coach-challenge/thumb.daab8bb1.png",
                    "beta-1km/banner.59e500fa.png",
                    "beta-1km/thumb.ecb76844.png",
                    "coach-challenge/banner.png",
                    "beta-1km/banner.png"
                }) {
            assertThat(Files.exists(stat.resolve(name))).as(name).isTrue();
        }
    }
}
