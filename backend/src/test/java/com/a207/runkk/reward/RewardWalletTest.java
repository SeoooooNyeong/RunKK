package com.a207.runkk.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.RewardRule;
import com.a207.runkk.policy.ShopItem;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 14장 순서 1 엔티티: RewardWallet 하루 상한·이중 적립(6.2 A-17, FR-159)과 구매(FR-160). */
@DisplayName("RewardWallet 적립·구매")
class RewardWalletTest {

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 21, 12, 0);
    private static final RewardRule RULE = new RewardRule(10, 20, 200);
    private static final ShopItem CAP = new ShopItem(7, "러닝 모자", ItemSlot.HAT, 50, true);

    private static AccrualRequest run(String recordId, int amount, LocalDateTime at) {
        return new AccrualRequest(AccrualReason.VALID_RUN, recordId, amount, at);
    }

    @Test
    @DisplayName("적립하면 잔액이 늘고 근거 하나의 적립량을 다시 읽을 수 있다")
    void accrue_addsBalance() {
        RewardWallet wallet = RewardWallet.open(1);

        assertThat(wallet.accrue(run("r1", 50, NOON), RULE)).isEqualTo(50);

        assertThat(wallet.balance()).isEqualTo(50);
        assertThat(wallet.accruedFor(AccrualReason.VALID_RUN, "r1")).isEqualTo(50);
        assertThat(wallet.accruedFor(AccrualReason.BATTLE_WIN, "r1")).isZero();
    }

    @Test
    @DisplayName("같은 (사유, 근거)는 두 번 적립하지 않는다")
    void accrue_sameSourceTwice_isZero() {
        RewardWallet wallet = RewardWallet.open(1);
        wallet.accrue(run("r1", 50, NOON), RULE);

        assertThat(wallet.accrue(run("r1", 50, NOON.plusHours(1)), RULE)).isZero();
        assertThat(wallet.balance()).isEqualTo(50);
        assertThat(
                        wallet.accrue(
                                new AccrualRequest(AccrualReason.BATTLE_WIN, "r1", 20, NOON), RULE))
                .as("사유가 다르면 다른 근거")
                .isEqualTo(20);
    }

    @Test
    @DisplayName("하루 상한 200: 남은 만큼만 적립하고 넘치면 기록하지 않는다, 다음 날은 다시 200")
    void accrue_dailyCap() {
        RewardWallet wallet = RewardWallet.open(1);
        assertThat(wallet.accrue(run("a", 150, NOON), RULE)).isEqualTo(150);
        assertThat(wallet.accrue(run("b", 100, NOON.plusMinutes(1)), RULE)).isEqualTo(50);
        assertThat(wallet.accrue(run("c", 30, NOON.plusMinutes(2)), RULE)).isZero();
        assertThat(wallet.accruedFor(AccrualReason.VALID_RUN, "c")).isZero();

        assertThat(wallet.accrue(run("c", 30, NOON.plusDays(1)), RULE))
                .as("상한에 걸려 기록하지 않은 근거는 다음 날 다시 적립할 수 있다")
                .isEqualTo(30);
        assertThat(wallet.balance()).isEqualTo(230);
    }

    @Test
    @DisplayName("적립량이 0 이하면 기록하지 않는다")
    void accrue_zeroBase() {
        RewardWallet wallet = RewardWallet.open(1);
        assertThat(wallet.accrue(run("short", 0, NOON), RULE)).isZero();
        assertThat(wallet.accrue(run("short", 10, NOON), RULE)).isEqualTo(10);
    }

    @Test
    @DisplayName("구매: 잔액 차감·보유 추가, 이미 보유·잔액 부족 거부")
    void purchase() {
        RewardWallet wallet = RewardWallet.open(1);
        assertThatThrownBy(() -> wallet.purchase(CAP, NOON))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.INSUFFICIENT_REWARD);

        wallet.accrue(run("r1", 60, NOON), RULE);
        wallet.purchase(CAP, NOON);

        assertThat(wallet.balance()).isEqualTo(10);
        assertThat(wallet.owns(7)).isTrue();
        assertThat(wallet.owns(8)).isFalse();
        assertThatThrownBy(() -> wallet.purchase(CAP, NOON))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.ALREADY_OWNED);
    }
}
