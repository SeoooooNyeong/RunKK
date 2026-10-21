package com.a207.runkk.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.battle.BattleJudged;
import com.a207.runkk.battle.BattleType;
import com.a207.runkk.battle.Judgement;
import com.a207.runkk.battle.Side;
import com.a207.runkk.battle.Verdict;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.RewardRule;
import com.a207.runkk.policy.ShopCatalog;
import com.a207.runkk.policy.ShopItem;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunRecordSaved;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** reward 응용 서비스(14장 단위: 인메모리 저장소·고정 시계). srs.md 6.3 6.4·6.5. */
@DisplayName("reward 응용 서비스")
class RewardServicesTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 9, 0);
    private static final long ALICE = 1;
    private static final long BOB = 2;

    private final InMemoryRewardWalletRepository wallets = new InMemoryRewardWalletRepository();
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    private final RewardService rewards =
            new RewardService(wallets, policy, clock, new NoTransactionManager());
    private final ShopService shop = new ShopService(wallets, policy, clock);

    @BeforeEach
    void rules() {
        when(policy.rewardRule()).thenReturn(new RewardRule(10, 20, 200));
        when(policy.shopCatalog())
                .thenReturn(
                        new ShopCatalog(
                                List.of(
                                        new ShopItem(1, "빨간 모자", ItemSlot.HAT, 30, true),
                                        new ShopItem(2, "단종 신발", ItemSlot.SHOES, 10, false),
                                        new ShopItem(3, "파란 상의", ItemSlot.TOP, 500, true))));
    }

    private static RunRecordSaved saved(String id, long meters, boolean valid) {
        return new RunRecordSaved(
                id,
                ALICE,
                NOW.minusHours(1),
                Distance.ofMeters(meters),
                Duration.ofMinutes(30),
                valid,
                valid ? 40.0 : null,
                RunMode.FREE,
                null,
                null,
                true,
                valid ? 1 : 0,
                NOW);
    }

    private static BattleJudged judged(long battleId, Side winner, boolean corrected) {
        return new BattleJudged(
                battleId,
                BattleType.RANDOM,
                new Judgement(
                        Map.of(Side.member(ALICE), Verdict.WIN, Side.member(BOB), Verdict.LOSE),
                        winner,
                        corrected,
                        NOW),
                NOW);
    }

    @Test
    @DisplayName("유효 러닝은 ⌊km × 10⌋ 적립, 무효는 적립하지 않고 같은 기록 재수신은 0")
    void onRunSaved() {
        rewards.onRunSaved(saved("r-1", 5_678, true));
        rewards.onRunSaved(saved("r-1", 5_678, true));
        rewards.onRunSaved(saved("r-2", 9_000, false));

        assertThat(rewards.balanceOf(ALICE)).isEqualTo(56);
        assertThat(rewards.accruedFor(AccrualReason.VALID_RUN, "r-1", ALICE)).isEqualTo(56);
        assertThat(rewards.accruedFor(AccrualReason.VALID_RUN, "r-2", ALICE)).isZero();
    }

    @Test
    @DisplayName("대결 승자에게 20, 정정으로 승자가 바뀌면 새 승자에게 적립하고 회수하지 않는다, 무승부는 없음")
    void onBattleJudged_correctionWithoutDoubleAccrual() {
        rewards.onBattleJudged(judged(9, Side.member(ALICE), false));
        rewards.onBattleJudged(judged(9, Side.member(ALICE), false));
        rewards.onBattleJudged(judged(9, Side.member(BOB), true));
        rewards.onBattleJudged(judged(10, null, false));
        rewards.onBattleJudged(judged(11, Side.OPPONENT, false));

        assertThat(rewards.accruedFor(AccrualReason.BATTLE_WIN, "9", ALICE)).isEqualTo(20);
        assertThat(rewards.accruedFor(AccrualReason.BATTLE_WIN, "9", BOB)).isEqualTo(20);
        assertThat(rewards.balanceOf(ALICE)).isEqualTo(20);
        assertThat(rewards.balanceOf(BOB)).isEqualTo(20);
        assertThat(rewards.accruedFor(AccrualReason.BATTLE_WIN, "10", ALICE)).isZero();
    }

    @Test
    @DisplayName("지갑이 없는 회원: 잔액 0, 보유 없음")
    void noWallet() {
        assertThat(rewards.balanceOf(99)).isZero();
        assertThat(rewards.owns(99, 1)).isFalse();
    }

    @Test
    @DisplayName("상점: 판매 중 + 보유 아이템만, 부위 필터, 구매 거부 사유, 탈퇴 때 지갑 삭제")
    void shopAndPurchase() {
        rewards.onRunSaved(saved("r-1", 5_000, true));

        assertThat(shop.shop(ALICE, Optional.empty()).items())
                .extracting(ShopView.Entry::itemId)
                .containsExactly(1L, 3L);
        assertThat(shop.shop(ALICE, Optional.of(ItemSlot.HAT)).items()).hasSize(1);
        assertThatThrownBy(() -> shop.purchase(ALICE, 42))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.ITEM_NOT_FOUND);
        assertThatThrownBy(() -> shop.purchase(ALICE, 2))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.ITEM_NOT_ON_SALE);
        assertThatThrownBy(() -> shop.purchase(ALICE, 3))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.INSUFFICIENT_REWARD);

        assertThat(shop.purchase(ALICE, 1)).isEqualTo(new PurchaseResult(1, 20));
        assertThat(rewards.owns(ALICE, 1)).isTrue();
        ShopView view = shop.shop(ALICE, Optional.empty());
        assertThat(view.balance()).isEqualTo(20);
        assertThat(view.items().get(0).owned()).isTrue();

        new RewardWithdrawalListener(wallets).onMemberWithdrawn(new MemberWithdrawn(ALICE, NOW));
        assertThat(rewards.balanceOf(ALICE)).isZero();
    }

    @Test
    @DisplayName("M4: 적립 중 잠금 대기 같은 일시 오류는 다시 해서 적립한다")
    void accrual_retriesTransientFailure() {
        wallets.failNext(new org.springframework.dao.CannotAcquireLockException("lock wait"));
        org.assertj.core.api.Assertions.assertThatCode(
                        () -> rewards.onRunSaved(saved("rec-retry", 3_000, true)))
                .doesNotThrowAnyException();
        assertThat(rewards.balanceOf(ALICE)).isEqualTo(30);
    }

    @Test
    @DisplayName("M4: 적립이 계속 실패해도 수신자는 예외를 던지지 않는다(뒤 수신자·업로드 응답을 막지 않음)")
    void accrual_persistentFailure_isContained() {
        wallets.failNext(
                new IllegalStateException("db down"),
                new IllegalStateException("db down"),
                new IllegalStateException("db down"));
        org.assertj.core.api.Assertions.assertThatCode(
                        () -> rewards.onRunSaved(saved("rec-fail", 3_000, true)))
                .doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThatCode(
                        () -> rewards.onBattleJudged(judged(9L, Side.member(ALICE), false)))
                .doesNotThrowAnyException();
    }

    /** 트랜잭션 없이 바로 실행한다(인메모리 저장소 단위 시험). */
    private static final class NoTransactionManager
            implements org.springframework.transaction.PlatformTransactionManager {
        @Override
        public org.springframework.transaction.TransactionStatus getTransaction(
                org.springframework.transaction.TransactionDefinition definition) {
            return new org.springframework.transaction.support.SimpleTransactionStatus();
        }

        @Override
        public void commit(org.springframework.transaction.TransactionStatus status) {}

        @Override
        public void rollback(org.springframework.transaction.TransactionStatus status) {}
    }
}
