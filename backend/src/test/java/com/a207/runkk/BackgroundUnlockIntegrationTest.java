package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.a207.runkk.avatar.AvatarQuery;
import com.a207.runkk.avatar.AvatarRepository;
import com.a207.runkk.avatar.AvatarService;
import com.a207.runkk.avatar.BackgroundService;
import com.a207.runkk.avatar.BackgroundsView;
import com.a207.runkk.batch.avatar.BackgroundReconcileJob;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingKey;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunUploadCommand;
import com.a207.runkk.running.RunUploadService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * 03-통합 T-30 랜드마크 배경 해금 스레드(SQ-24, D-107·D-129·D-139·D-148)를 실 PostgreSQL 위에서 돈다. 개인 랜드마크
 * L1·L2·L3(5·10·12km)과 크루 단계 하나로 운영 기준값을 바꿔 짧은 기록으로 넘는다. Docker가 없는 PC에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("T-30 랜드마크 배경 해금(PostgreSQL)")
class BackgroundUnlockIntegrationTest {

    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final String TABLE =
            "{\"landmarks\":["
                    + landmark("L1_TEST", "DOMESTIC", "엘원", 5_000)
                    + ","
                    + landmark("L2_TEST", "EAST_ASIA", "엘투", 10_000)
                    + ","
                    + landmark("L3_TEST", "INTERCONTINENTAL", "엘쓰리", 12_000)
                    + ","
                    + landmark("CREW_TEST", "CREW_TOTAL", "크루", 20_000)
                    + "]}";

    @MockitoSpyBean RunQueryService runs;
    @MockitoSpyBean AvatarRepository avatars;
    @Autowired BackgroundService backgrounds;
    @Autowired BackgroundReconcileJob reconcileJob;
    @Autowired AvatarService avatarService;
    @Autowired AvatarQuery avatarQuery;
    @Autowired OperationSettingService settings;
    @Autowired RunUploadService uploads;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private static String landmark(String key, String stage, String name, long meters) {
        return "{\"key\":\""
                + key
                + "\",\"stage\":\""
                + stage
                + "\",\"name\":\""
                + name
                + "\",\"distance\":{\"meters\":"
                + meters
                + "},\"roundTrip\":false}";
    }

    @BeforeEach
    void table() {
        settings.change("ops@runkk", SettingKey.LANDMARKS, TABLE);
    }

    @AfterEach
    void restore() {
        reset(runs, avatars);
        settings.change("ops@runkk", SettingKey.LANDMARKS, SettingKey.LANDMARKS.defaultValue());
    }

    private long member() {
        String name = "배경러너" + NAMES.incrementAndGet();
        return tx.execute(
                s ->
                        members.save(MemberFixtures.member(name, LocalDateTime.now().minusDays(30)))
                                .getId());
    }

    private void upload(long memberId, long meters, int seconds, int hoursAgo) {
        uploads.upload(
                memberId,
                new RunUploadCommand(
                        RunFixtures.upload(
                                UUID.randomUUID().toString(),
                                LocalDateTime.now().minusHours(hoursAgo),
                                meters,
                                seconds)));
    }

    private List<String> unlocked(long memberId) {
        return jdbc.queryForList(
                "select landmark_key from avatar_background where member_id = ?"
                        + " order by distance_meters",
                String.class,
                memberId);
    }

    private List<String> notices(long memberId) {
        return jdbc.queryForList(
                "select content from notification where recipient_id = ?"
                        + " and type = 'BACKGROUND_UNLOCKED' order by id",
                String.class,
                memberId);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("비동기 해금 대기").isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    @Test
    @DisplayName("TC-6756·TC-6757 기록 저장 뒤 비동기로 해금하고, 한 기록이 둘을 넘으면 알림 한 건에 둘 다 적는다")
    void uploadUnlocksAsync() throws InterruptedException {
        long m = member();

        upload(m, 5_000, 1_500, 5);
        await(() -> unlocked(m).size() == 1 && notices(m).size() == 1);
        assertThat(unlocked(m)).containsExactly("L1_TEST");
        assertThat(notices(m)).containsExactly("새 배경 1개를 해금했어요: 엘원");
        assertThat(
                        jdbc.queryForObject(
                                "select target_screen from notification where recipient_id = ?",
                                String.class,
                                m))
                .isEqualTo("AVATAR_BACKGROUND");

        upload(m, 7_000, 2_100, 3);
        await(() -> unlocked(m).size() == 3 && notices(m).size() == 2);
        assertThat(unlocked(m)).containsExactly("L1_TEST", "L2_TEST", "L3_TEST");
        assertThat(notices(m).get(1)).isEqualTo("새 배경 2개를 해금했어요: 엘투, 엘쓰리");
    }

    @Test
    @DisplayName("TC-6759 리스너 해금이 실패하면 행 0, 0시 10분 보정이 채우고 알림 1, 두 번째 보정은 추가 0")
    void listenerFailure_isReconciled() throws InterruptedException {
        long m = member();
        doThrow(new IllegalStateException("수신자 장애")).when(avatars).findForUpdate(m);
        upload(m, 5_000, 1_500, 5);
        Thread.sleep(500);
        assertThat(unlocked(m)).isEmpty();
        reset(avatars);

        reconcileJob.run();
        assertThat(unlocked(m)).containsExactly("L1_TEST");
        assertThat(notices(m)).hasSize(1);

        assertThat(backgrounds.reconcile(LocalDateTime.now())).isGreaterThanOrEqualTo(1);
        assertThat(unlocked(m)).hasSize(1);
        assertThat(notices(m)).hasSize(1);
    }

    @Test
    @DisplayName("TC-6760·TC-6761 배포 전에 넘은 배경은 조회 때 알림 없이 채우고, 표에서 빠져도 해금 상태로 끝에 남는다")
    void backfill_andRemovedFromTable() {
        long m = member();
        doReturn(Distance.ofMeters(12_000)).when(runs).validDistanceBefore(eq(m), any());

        BackgroundsView view = backgrounds.backgrounds(m, LocalDateTime.now());
        assertThat(view.backgrounds()).hasSize(3).allMatch(BackgroundsView.Item::unlocked);
        assertThat(notices(m)).isEmpty();
        assertThat(avatarService.view(m).backgroundKey()).isNull();
        assertThat(unlocked(m)).hasSize(3);

        settings.change(
                "ops@runkk",
                SettingKey.LANDMARKS,
                "{\"landmarks\":["
                        + landmark("L1_TEST", "DOMESTIC", "엘원", 6_000)
                        + ","
                        + landmark("L3_TEST", "INTERCONTINENTAL", "엘쓰리", 12_000)
                        + "]}");
        BackgroundsView after = backgrounds.backgrounds(m, LocalDateTime.now());
        assertThat(after.backgrounds())
                .extracting(BackgroundsView.Item::key)
                .containsExactly("L1_TEST", "L3_TEST", "L2_TEST");
        assertThat(after.backgrounds()).allMatch(BackgroundsView.Item::unlocked);
        assertThat(unlocked(m)).hasSize(3);
    }

    @Test
    @DisplayName("TC-6762 선택: 해금 키는 아바타·프로필 배경 키가 되고, 잠긴 키는 BACKGROUND_LOCKED, null은 기본 배경")
    void choose() {
        long m = member();
        doReturn(Distance.ofMeters(5_000)).when(runs).validDistanceBefore(eq(m), any());
        backgrounds.backgrounds(m, LocalDateTime.now());

        assertThat(backgrounds.choose(m, "L1_TEST").selectedKey()).isEqualTo("L1_TEST");
        assertThat(avatarService.view(m).backgroundKey()).isEqualTo("L1_TEST");
        assertThat(avatarQuery.backgroundKeyOf(m)).contains("L1_TEST");
        assertThatThrownBy(() -> backgrounds.choose(m, "L3_TEST"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.BACKGROUND_LOCKED);
        assertThat(avatarQuery.backgroundKeyOf(m)).contains("L1_TEST");
        assertThat(backgrounds.useDefault(m).selectedKey()).isNull();
        assertThat(avatarQuery.backgroundKeyOf(m)).isEmpty();
    }

    @Test
    @DisplayName("TC-6763 기록을 저장한 직후 해금 리스너·보정과 조회 채우기가 동시에 50회: 매회 행 1, 알림 정확히 1, 예외 0")
    void concurrentUnlockAndBackfill() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 50; round++) {
                long m = member();
                upload(m, 5_000, 1_500, 5);
                CountDownLatch start = new CountDownLatch(1);
                List<Callable<Object>> both = new ArrayList<>();
                both.add(
                        () -> {
                            start.await();
                            return backgrounds.unlockReached(m, LocalDateTime.now());
                        });
                both.add(
                        () -> {
                            start.await();
                            return backgrounds.backgrounds(m, LocalDateTime.now());
                        });
                List<Future<Object>> futures = new ArrayList<>();
                for (Callable<Object> c : both) {
                    futures.add(pool.submit(c));
                }
                start.countDown();
                for (Future<Object> f : futures) {
                    f.get(10, TimeUnit.SECONDS);
                }
                final int r = round;
                await(() -> !notices(m).isEmpty());
                assertThat(unlocked(m)).as("회차 " + r).containsExactly("L1_TEST");
                assertThat(notices(m)).as("회차 " + r).hasSize(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
