package com.a207.runkk;

import static com.a207.runkk.running.RunFixtures.START;
import static com.a207.runkk.running.RunFixtures.id;
import static com.a207.runkk.running.RunFixtures.upload;
import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.Member;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.MemberSettingService;
import com.a207.runkk.member.WithdrawalService;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.running.DisposalOrderView;
import com.a207.runkk.running.DisposalService;
import com.a207.runkk.running.DisposalStatus;
import com.a207.runkk.running.HeartRateSample;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.running.RunRecordSaved;
import com.a207.runkk.running.RunUploadCommand;
import com.a207.runkk.running.RunUploadService;
import com.a207.runkk.running.UploadResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 14장 통합 — 스레드 기반(B2 범위): T-1 러닝 저장(같은 회원 동시 업로드의 순번 중복 0, 티어 수신자), T-6 탈퇴(수신자가 기록·장부·파기 지시·알림·기기
 * 토큰까지 지움, 7일 뒤 purgeWithdrawn), T-7 동의 철회(서버 파기·기기 확인). 실제 PostgreSQL에서 커밋 뒤 수신자까지 돈다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import({TestcontainersConfig.class, MemberRunningThreadIntegrationTest.Events.class})
@ActiveProfiles("integration")
@org.junit.jupiter.api.DisplayName("스레드 기반 통합 T-1·T-6·T-7")
class MemberRunningThreadIntegrationTest {

    /** 커밋 뒤 RunRecordSaved를 모은다(다음 단계 수신자 자리). */
    static class SavedEvents {
        final List<RunRecordSaved> seen = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(RunRecordSaved e) {
            seen.add(e);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Events {
        @Bean
        SavedEvents savedEvents() {
            return new SavedEvents();
        }
    }

    private static final AtomicInteger NAMES = new AtomicInteger();

    @Autowired MemberRepository members;
    @Autowired RunUploadService uploads;
    @Autowired WithdrawalService withdrawals;
    @Autowired MemberSettingService settings;
    @Autowired DisposalService disposals;
    @Autowired NotificationService notifications;
    @Autowired SavedEvents events;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        for (String table :
                List.of(
                        "personal_best",
                        "personal_record_book",
                        "run_record",
                        "disposal_order",
                        "notification",
                        "push_device",
                        "member_consent",
                        "member",
                        "audit_record")) {
            jdbc.update("delete from " + table);
        }
        events.seen.clear();
    }

    private long newMember() {
        String name = "스레드" + NAMES.incrementAndGet();
        return tx.execute(
                s ->
                        members.save(MemberFixtures.member(name, LocalDateTime.now().minusDays(30)))
                                .getId());
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void t1_concurrentUploadsOfOneMember_getDistinctOrdinals_andTierStaysUnranked()
            throws Exception {
        long m = newMember();
        int runs = 8;
        ExecutorService pool = Executors.newFixedThreadPool(runs);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<UploadResult>> futures = new ArrayList<>();
        for (int i = 1; i <= runs; i++) {
            int n = i;
            Callable<UploadResult> task =
                    () -> {
                        go.await();
                        return uploads.upload(
                                m,
                                new RunUploadCommand(
                                        upload(id(n), START.minusDays(n), 5_000, 1_500)));
                    };
            futures.add(pool.submit(task));
        }
        go.countDown();
        for (Future<UploadResult> f : futures) {
            assertThat(f.get().alreadyStored()).isFalse();
        }
        pool.shutdown();

        assertThat(events.seen)
                .extracting(RunRecordSaved::validRunOrdinal)
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(count("select count(*) from run_record where member_id = ?", m)).isEqualTo(runs);
        Member member = members.findById(m).orElseThrow();
        // 일반 러닝은 첫 배치를 하지 않는다(배치고사 3판으로만, J24-6·D-484)
        assertThat(member.getTier()).isEqualTo(Tier.UNRANKED);
        assertThat(member.getLevel()).isEqualTo(1);
    }

    @Test
    void t1_sameRecordSentTwiceAtOnce_isStoredOnce() throws Exception {
        long m = newMember();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<UploadResult> task =
                () -> {
                    go.await();
                    return uploads.upload(m, new RunUploadCommand(upload(id(1), 5_000, 1_500)));
                };
        Future<UploadResult> a = pool.submit(task);
        Future<UploadResult> b = pool.submit(task);
        go.countDown();
        List<Boolean> already = List.of(a.get().alreadyStored(), b.get().alreadyStored());
        pool.shutdown();

        assertThat(already).containsExactlyInAnyOrder(true, false);
        assertThat(events.seen).hasSize(1);
        assertThat(count("select count(*) from run_record")).isEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "탈퇴하면 기록의 모습도 사라진다: 저장 때 붙은 모습 열(V66)은 기록 행과 함께 지워진다(설계 5.31 [임시:D-성-249])")
    void 탈퇴하면_기록의_모습도_사라진다() {
        long m = newMember();
        uploads.upload(m, new RunUploadCommand(upload(id(41), 5_000, 1_500)));
        assertThat(
                        count(
                                "select count(*) from run_record where member_id = ? and"
                                    + " look_gender is not null and look_body_variant is not null"
                                    + " and look_run_amount is not null",
                                m))
                .as("실 PostgreSQL에서 저장 시점 모습이 붙는다")
                .isEqualTo(1);

        assertThat(withdrawals.withdraw(m).withdrawn()).isTrue();

        assertThat(count("select count(*) from run_record where member_id = ?", m)).isZero();
    }

    @Test
    void t6_withdrawal_listenersWipeDomainData_thenPurgeAfterSevenDays() {
        long m = newMember();
        long other = newMember();
        uploads.upload(m, new RunUploadCommand(upload(id(1), 5_000, 1_500)));
        uploads.upload(other, new RunUploadCommand(upload(id(2), 5_000, 1_500)));
        settings.withdrawConsent(m, ConsentItem.LOCATION);
        notifications.registerDevice(m, "device-m");
        notifications.registerDevice(other, "device-o");
        notifications.notify(new NotificationRequest(m, NotificationType.KICKED, "퇴장", null));
        notifications.notify(new NotificationRequest(other, NotificationType.KICKED, "퇴장", null));

        assertThat(withdrawals.withdraw(m).withdrawn()).isTrue();

        assertThat(count("select count(*) from run_record where member_id = ?", m)).isZero();
        assertThat(count("select count(*) from personal_record_book where member_id = ?", m))
                .isZero();
        assertThat(count("select count(*) from disposal_order where member_id = ?", m)).isZero();
        assertThat(count("select count(*) from notification where recipient_id = ?", m)).isZero();
        assertThat(count("select count(*) from push_device where member_id = ?", m)).isZero();
        assertThat(count("select count(*) from run_record where member_id = ?", other))
                .isEqualTo(1);
        assertThat(count("select count(*) from push_device where member_id = ?", other))
                .isEqualTo(1);
        assertThat(
                        count(
                                "select count(*) from member where id = ? and nickname is null and"
                                        + " provider is null",
                                m))
                .isEqualTo(1);
        assertThat(count("select count(*) from member_consent where member_id = ?", m)).isZero();
        assertThat(
                        count(
                                "select count(*) from audit_record where action = 'WITHDRAWAL' and"
                                        + " target_id = ?",
                                String.valueOf(m)))
                .isEqualTo(1);

        assertThat(withdrawals.purgeWithdrawn(LocalDateTime.now().plusDays(6))).isZero();
        assertThat(withdrawals.purgeWithdrawn(LocalDateTime.now().plusDays(8))).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select record_visibility from member where id = ?",
                                String.class,
                                m))
                .isEqualTo("PRIVATE");
    }

    @Test
    void t7_consentWithdrawal_disposesServerDataAndTracksDevice() {
        long m = newMember();
        settings.giveConsent(m, ConsentItem.HEART_RATE);
        uploads.upload(
                m,
                new RunUploadCommand(
                        RunFixtures.withHeartRates(
                                upload(id(1), 5_000, 1_500),
                                List.of(new HeartRateSample(START.plusSeconds(1), 150)))));

        settings.withdrawConsent(m, ConsentItem.LOCATION);
        settings.withdrawConsent(m, ConsentItem.HEART_RATE);
        List<DisposalOrderView> pending = disposals.pendingFor(m);
        assertThat(pending)
                .hasSize(2)
                .allSatisfy(o -> assertThat(o.status()).isEqualTo(DisposalStatus.SCHEDULED));

        disposals.confirmDevice(m, pending.get(0).id());
        assertThat(disposals.executeDue(LocalDateTime.now())).isEqualTo(2);

        assertThat(
                        jdbc.queryForObject(
                                "select route_data is null and route_purged and heart_rate_data is"
                                    + " null and heart_rate_purged and distance_meters = 5000 from"
                                    + " run_record",
                                Boolean.class))
                .isTrue();
        assertThat(count("select count(*) from audit_record where action = 'ROUTE_DISPOSAL'"))
                .isEqualTo(2);
        assertThat(count("select count(*) from audit_record where action = 'CONSENT_CHANGE'"))
                .isEqualTo(3);
        assertThat(disposals.pendingFor(m))
                .singleElement()
                .satisfies(o -> assertThat(o.status()).isEqualTo(DisposalStatus.EXECUTED));
        disposals.confirmDevice(m, disposals.pendingFor(m).get(0).id());
        assertThat(disposals.pendingFor(m)).isEmpty();
        assertThat(jdbc.queryForList("select status from disposal_order", String.class))
                .containsOnly("DEVICE_CONFIRMED");
        assertThat(Optional.of(m)).isPresent();
    }
}
