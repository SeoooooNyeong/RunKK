package com.a207.runkk.web.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 초대 코드 실패 제한(D-132): 회원별 10분 5회, 동시 요청도 상한을 넘지 못한다. */
@DisplayName("CrewJoinAttemptGuard")
class CrewJoinAttemptGuardTest {

    private final CrewJoinAttemptGuard guard =
            new CrewJoinAttemptGuard(
                    Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneId.of("Asia/Seoul")));

    private static String invalid() {
        throw new BusinessRuleException(ReasonCode.INVALID_CODE);
    }

    private static ReasonCode reasonOf(Throwable e) {
        return ((BusinessRuleException) e).reason();
    }

    @Test
    @DisplayName("INVALID_CODE 5번 뒤에는 가입을 부르지 않고 TOO_MANY_ATTEMPTS, 다른 거부·크루 ID 가입은 세지 않는다")
    void blocksAfterFiveInvalidCodes() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(
                            () ->
                                    guard.guard(
                                            1,
                                            true,
                                            () -> {
                                                throw new BusinessRuleException(
                                                        ReasonCode.CREW_FULL);
                                            }))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.CREW_FULL));
        }
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> guard.guard(1, true, CrewJoinAttemptGuardTest::invalid))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.INVALID_CODE));
        }
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> guard.guard(1, true, calls::incrementAndGet))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.TOO_MANY_ATTEMPTS));
        assertThat(calls).hasValue(0);
        assertThat(guard.guard(1, false, () -> "byCrew")).isEqualTo("byCrew");
        assertThat(guard.guard(2, true, () -> "other member")).isEqualTo("other member");
    }

    @Test
    @DisplayName("동시에 들어온 틀린 코드 20건 중 가입까지 가는 것은 5건뿐이다(보안 리뷰 MEDIUM)")
    void concurrentAttemptsCannotExceedLimit() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch inside = new CountDownLatch(1);
        AtomicInteger reached = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(
                    pool.submit(
                            () -> {
                                start.await();
                                return guard.guard(
                                        7,
                                        true,
                                        () -> {
                                            reached.incrementAndGet();
                                            try {
                                                inside.await(200, TimeUnit.MILLISECONDS);
                                            } catch (InterruptedException e) {
                                                Thread.currentThread().interrupt();
                                            }
                                            return invalid();
                                        });
                            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (java.util.concurrent.ExecutionException ignored) {
                // 모두 INVALID_CODE 또는 TOO_MANY_ATTEMPTS
            }
        }
        pool.shutdown();
        assertThat(reached).hasValue(5);
    }

    @Test
    @DisplayName("초대 코드 미리보기도 틀린 코드를 실패로 세고 가입과 횟수를 함께 쓴다(S15P31A207-295)")
    void previewSharesFailuresWithJoin() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> guard.preview(7, CrewJoinAttemptGuardTest::invalid))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.INVALID_CODE));
        }
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> guard.guard(7, true, CrewJoinAttemptGuardTest::invalid))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.INVALID_CODE));
        }
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> guard.preview(7, calls::incrementAndGet))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.TOO_MANY_ATTEMPTS));
        assertThat(calls).hasValue(0);
    }

    @Test
    @DisplayName("맞는 코드 미리보기는 실패 횟수를 지우지 않는다(아는 코드로 횟수를 지우는 우회 차단)")
    void previewSuccess_doesNotResetFailures() {
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> guard.preview(8, CrewJoinAttemptGuardTest::invalid))
                    .isInstanceOf(BusinessRuleException.class);
            assertThat(guard.preview(8, () -> "known")).isEqualTo("known");
        }
        assertThatThrownBy(() -> guard.preview(8, CrewJoinAttemptGuardTest::invalid))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.INVALID_CODE));
        assertThatThrownBy(() -> guard.preview(8, () -> "known"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.TOO_MANY_ATTEMPTS));
    }
}
