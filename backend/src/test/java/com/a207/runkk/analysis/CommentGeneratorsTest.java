package com.a207.runkk.analysis;

import static com.a207.runkk.analysis.AnalysisFixtures.summary;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.policy.LlmCallLimit;
import com.a207.runkk.policy.OperationPolicy;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** srs.md 6.3 3.4.2 의사결정표 4규칙 + 예외·한도(FR-065, FR-066, D-58, D-62). */
@DisplayName("코멘트 생성기·LLM 한도")
class CommentGeneratorsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    private final LlmCommentGateway gateway = mock(LlmCommentGateway.class);
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final AnalysisFixtures.Counter counter = new AnalysisFixtures.Counter();
    private final LlmCallQuota quota = new LlmCallQuota(counter);
    private final AiCommentGenerator ai =
            new AiCommentGenerator(gateway, quota, policy, Duration.ofSeconds(3));
    private final FallbackCommentGenerator fallback =
            new FallbackCommentGenerator(ai, new RuleCommentGenerator(new RuleCommentCatalog()));

    @BeforeEach
    void limits() {
        when(policy.llmCallLimit()).thenReturn(new LlmCallLimit(1, 200));
    }

    private static CommentContext context(long memberId, boolean agreed, boolean enabled) {
        return new CommentContext(agreed, enabled, memberId, TODAY);
    }

    @Test
    @DisplayName("규칙 1: LLM 동의가 없으면 ai를 부르지 않고 규칙 문구")
    void notAgreed_usesRule() {
        Optional<Comment> c = fallback.generate(summary(5, 0, 0), context(1, false, true));
        assertThat(c).get().extracting(Comment::source).isEqualTo(CommentSource.RULE);
        verify(gateway, never()).requestComment(any(), any());
    }

    @Test
    @DisplayName("규칙 2: 축소 운영(AI 코멘트 꺼짐) 또는 회원 하루 한도 초과면 규칙 문구")
    void disabledOrQuotaExceeded_usesRule() {
        assertThat(fallback.generate(summary(5, 0, 0), context(1, true, false)))
                .get()
                .extracting(Comment::source)
                .isEqualTo(CommentSource.RULE);
        verify(gateway, never()).requestComment(any(), any());
        when(gateway.requestComment(any(), any()))
                .thenReturn(Optional.of(new Comment("멋져요", CommentSource.AI, 0)));
        assertThat(fallback.generate(summary(5, 0, 0), context(1, true, true)))
                .get()
                .extracting(Comment::source)
                .isEqualTo(CommentSource.AI);
        assertThat(fallback.generate(summary(6, 0, 0), context(1, true, true)))
                .get()
                .extracting(Comment::source)
                .isEqualTo(CommentSource.RULE);
    }

    @Test
    @DisplayName("규칙 3: 실패·시간 초과(빈 값)·예외면 규칙 문구, 차감은 되돌리지 않는다")
    void gatewayFailure_usesRuleAndKeepsQuota() {
        when(gateway.requestComment(any(), any())).thenReturn(Optional.empty());
        assertThat(fallback.generate(summary(5, 0, 0), context(1, true, true)))
                .get()
                .extracting(Comment::source)
                .isEqualTo(CommentSource.RULE);
        when(gateway.requestComment(any(), any())).thenThrow(new IllegalStateException("boom"));
        assertThat(ai.generate(summary(5, 0, 0), context(2, true, true))).isEmpty();
        assertThat(counter.valueOf(":global")).isEqualTo(2);
    }

    @Test
    @DisplayName("규칙 4: 받은 코멘트의 forRun은 요약 순번으로 채운다")
    void aiComment_forRunIsSummaryToRun() {
        when(gateway.requestComment(any(), any()))
                .thenReturn(Optional.of(new Comment("멋져요", CommentSource.AI, 0)));
        assertThat(ai.generate(summary(17, 0, 0), context(3, true, true)))
                .get()
                .extracting(Comment::forRun, Comment::text)
                .containsExactly(17, "멋져요");
    }

    @Test
    @DisplayName("한도 카운터(Redis) 장애면 ai를 부르지 않고 규칙 문구로 대체한다")
    void quotaStoreFailure_fallsBackToRule() {
        QuotaCounter broken = mock(QuotaCounter.class);
        when(broken.tryIncrementAll(any(), any()))
                .thenThrow(new IllegalStateException("redis down"));
        AiCommentGenerator failing =
                new AiCommentGenerator(
                        gateway, new LlmCallQuota(broken), policy, Duration.ofSeconds(3));
        FallbackCommentGenerator chain =
                new FallbackCommentGenerator(
                        failing, new RuleCommentGenerator(new RuleCommentCatalog()));
        assertThat(chain.generate(summary(5, 0, 0), context(1, true, true)))
                .get()
                .extracting(Comment::source)
                .isEqualTo(CommentSource.RULE);
        verify(gateway, never()).requestComment(any(), any());
    }

    @Test
    @DisplayName("전체 한도에 걸리면 회원·전체 키를 모두 되돌린다")
    void globalLimit_rollsBackBoth() {
        LlmCallLimit limit = new LlmCallLimit(1, 2);
        assertThat(quota.tryAcquire(1, TODAY, limit)).isTrue();
        assertThat(quota.tryAcquire(2, TODAY, limit)).isTrue();
        assertThat(quota.tryAcquire(3, TODAY, limit)).isFalse();
        assertThat(counter.valueOf(":global")).isEqualTo(2);
        assertThat(counter.valueOf(":member:3")).isZero();
        assertThat(quota.tryAcquire(1, TODAY.plusDays(1), limit)).isTrue();
    }

    @Test
    @DisplayName("T-16: 회원·전체 한도 동시 요청 경합에서 받아들인 수가 한도를 넘지 않는다")
    void concurrentRequests_neverExceedLimits() throws Exception {
        LlmCallLimit limit = new LlmCallLimit(1, 5);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            long member = i % 10;
            results.add(
                    pool.submit(
                            () -> {
                                go.await();
                                return quota.tryAcquire(member, TODAY, limit);
                            }));
        }
        go.countDown();
        int accepted = 0;
        for (Future<Boolean> f : results) {
            accepted += f.get() ? 1 : 0;
        }
        pool.shutdown();
        assertThat(accepted).isEqualTo(5);
        assertThat(counter.valueOf(":global")).isEqualTo(5);
    }
}
