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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

    private void collapse(long to, String target, String content, LocalDateTime at) {
        tx.executeWithoutResult(
                s ->
                        notifications.saveCollapsing(
                                new NotificationRequest(
                                        to,
                                        NotificationType.CREW_CHAT,
                                        content,
                                        new NavigationTarget(Screen.CREW_CHAT, target)),
                                at));
    }

    private int unreadRows(long to, String target) {
        return jdbc.queryForObject(
                "select count(*) from notification where recipient_id = ? and target_id = ?"
                        + " and is_read = false",
                Integer.class,
                to,
                target);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "saveCollapsing(D-102): 같은 수신자·종류·대상의 읽지 않은 행을 고쳐 쓰고, 대상이 다르거나 읽었으면 새 행")
    void saveCollapsing_rewritesUnreadSameTarget() {
        collapse(1, "7", "첫 메시지", T);
        collapse(1, "7", "둘째 메시지", T.plusMinutes(1));
        collapse(1, "8", "다른 크루", T);
        collapse(2, "7", "다른 회원", T);
        assertThat(unreadRows(1, "7")).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select content from notification where recipient_id = 1"
                                        + " and target_id = '7'",
                                String.class))
                .isEqualTo("둘째 메시지");
        assertThat(jdbc.queryForObject("select count(*) from notification", Integer.class))
                .isEqualTo(3);

        jdbc.update("update notification set is_read = true where target_id = '7'");
        collapse(1, "7", "읽은 뒤", T.plusMinutes(2));
        assertThat(unreadRows(1, "7")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notification", Integer.class))
                .isEqualTo(4);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "리뷰 M4: 같은 대상의 묶음 알림이 동시에 와도 읽지 않은 행은 하나(V23 부분 유일 색인 + 충돌 시 고쳐 쓰기)")
    void saveCollapsing_concurrentCalls_keepOneUnreadRow() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int n = i;
            futures.add(
                    pool.submit(
                            () -> {
                                start.await();
                                collapse(5, "7", "메시지 " + n, T.plusSeconds(n));
                                return null;
                            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertThat(unreadRows(5, "7")).isEqualTo(1);
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
