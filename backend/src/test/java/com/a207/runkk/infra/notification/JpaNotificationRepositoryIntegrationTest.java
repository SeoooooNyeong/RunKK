package com.a207.runkk.infra.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.Notification;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.PushDevice;
import com.a207.runkk.notification.Screen;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 14장 순서 2: infra.notification을 실제 PostgreSQL(Flyway V4)로 확인한다. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@org.junit.jupiter.api.DisplayName("알림·기기 토큰 저장소(PostgreSQL)")
class JpaNotificationRepositoryIntegrationTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 20, 9, 0);

    @Autowired JpaNotificationRepository notifications;
    @Autowired JpaPushDeviceRepository devices;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from notification");
        jdbc.update("delete from push_device");
    }

    private void notify(long to, LocalDateTime at) {
        tx.executeWithoutResult(
                s ->
                        notifications.save(
                                Notification.of(
                                        new NotificationRequest(
                                                to,
                                                NotificationType.JOIN_DECIDED,
                                                "승인",
                                                new NavigationTarget(Screen.CREW_HOME, "7")),
                                        at)));
    }

    @Test
    void inboxQueries_purgeAndRecipientDelete() {
        notify(1, T.minusDays(40));
        notify(1, T.minusDays(1));
        notify(1, T);
        notify(2, T);

        assertThat(notifications.findByRecipient(1, Optional.empty())).hasSize(3);
        assertThat(
                        notifications.findByRecipient(
                                1,
                                Optional.of(
                                        new DateRange(
                                                LocalDate.of(2026, 10, 19),
                                                LocalDate.of(2026, 10, 20)))))
                .hasSize(2);
        Integer purged = tx.execute(s -> notifications.deleteOlderThan(T.minusDays(30)));
        assertThat(purged).isEqualTo(1);
        tx.executeWithoutResult(s -> notifications.deleteByRecipient(1));
        assertThat(jdbc.queryForObject("select count(*) from notification", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void markRead_persists() {
        notify(1, T);
        Long stored = jdbc.queryForObject("select id from notification", Long.class);
        tx.executeWithoutResult(s -> notifications.findById(stored).orElseThrow().markRead());
        assertThat(jdbc.queryForObject("select is_read from notification", Boolean.class)).isTrue();
    }

    @Test
    void pushDevices_tokensReassignAndDelete() {
        tx.executeWithoutResult(
                s -> {
                    devices.save(PushDevice.register(1, "a", T));
                    devices.save(PushDevice.register(1, "b", T));
                    devices.save(PushDevice.register(2, "c", T));
                });
        assertThat(devices.tokensOf(1)).containsExactlyInAnyOrder("a", "b");
        tx.executeWithoutResult(s -> devices.findByToken("b").orElseThrow().reassign(2, T));
        assertThat(devices.tokensOf(2)).containsExactlyInAnyOrder("b", "c");
        tx.executeWithoutResult(s -> devices.deleteTokens(List.of("a")));
        tx.executeWithoutResult(s -> devices.deleteByMember(2));
        assertThat(jdbc.queryForObject("select count(*) from push_device", Integer.class)).isZero();
    }
}
