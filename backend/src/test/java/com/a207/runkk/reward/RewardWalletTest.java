package com.a207.runkk.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.RewardRule;
import com.a207.runkk.policy.ShopItem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 14장 순서 1 엔티티: RewardWallet 집계 칼럼(6.2 A-17, D-109, D-123, FR-159)과 구매(FR-160). 02-01 2.1.10.14
 * TC-6150~TC-6154.
 */
@DisplayName("RewardWallet 집계 칼럼·적립·구매")
class RewardWalletTest {

    private static final LocalDate D20 = LocalDate.of(2026, 10, 20);
    private static final LocalDate D21 = LocalDate.of(2026, 10, 21);
    private static final LocalDate D22 = LocalDate.of(2026, 10, 22);
    private static final RewardRule RULE = new RewardRule(10, 20, 200);
    private static final RewardRule NO_CAP = new RewardRule(10, 20, 100_000);
    private static final ShopItem CAP = new ShopItem(7, "러닝 모자", ItemSlot.HAT, 50, true);

    private static AccrualRequest run(String recordId, int amount, LocalDate day) {
        return new AccrualRequest(AccrualReason.VALID_RUN, recordId, amount, day.atTime(12, 0));
    }

    /** 집계 칼럼(last, lastAmount)과 잔액을 맞춘 지갑. 잔액 차이는 더 옛날 적립으로 채운다. */
    private static RewardWallet walletWith(LocalDate last, int lastAmount, int balance) {
        RewardWallet wallet = RewardWallet.open(1);
        if (balance > lastAmount) {
            wallet.accrue(run("seed-old", balance - lastAmount, last.minusDays(5)), NO_CAP, 0);
        }
        wallet.accrue(run("seed", lastAmount, last), NO_CAP, 0);
        return wallet;
    }

    @Test
    @DisplayName("TC-6150 accruedOn: 이력 없음 0 / 더 새 날 0 / 같은 날 칼럼 값 / 더 옛날 비어 있음")
    void accruedOn_paths() {
        assertThat(RewardWallet.open(1).accruedOn(D22)).hasValue(0);

        assertThat(walletWith(D21, 120, 120).accruedOn(D22)).hasValue(0);

        RewardWallet sameDay = walletWith(D22, 120, 120);
        assertThat(sameDay.accruedOn(D22)).hasValue(120);
        assertThat(sameDay.accruedOn(D21)).isEqualTo(OptionalInt.empty());
    }

    @Test
    @DisplayName("TC-6151 accrue: 상한 200에서 이미 200 → 빈 값·잔액·칼럼 그대로 / 199 → 1 / 150 → 50")
    void accrue_dailyCapBoundary() {
        RewardWallet full = walletWith(D22, 200, 200);
        assertThat(full.accrue(run("x", 50, D22), RULE, 200)).isEmpty();
        assertThat(full.balance()).isEqualTo(200);
        assertThat(full.accruedOn(D22)).hasValue(200);

        RewardWallet almost = walletWith(D22, 199, 199);
        assertThat(almost.accrue(run("x", 50, D22), RULE, 199))
                .map(RewardAccrual::amount)
                .contains(1);

        RewardWallet half = walletWith(D22, 150, 150);
        assertThat(half.accrue(run("x", 50, D22), RULE, 150))
                .map(RewardAccrual::amount)
                .contains(50);
        assertThat(half.accruedOn(D22)).hasValue(200);
    }

    @Test
    @DisplayName("TC-6152 accrue: 첫 적립은 칼럼을 그날로, 더 새 날은 칼럼을 바꾼다")
    void accrue_firstAndNewerDay() {
        RewardWallet first = RewardWallet.open(1);
        Optional<RewardAccrual> a = first.accrue(run("r1", 30, D22), RULE, 0);
        assertThat(a).map(RewardAccrual::amount).contains(30);
        assertThat(first.accruedOn(D22)).hasValue(30);
        assertThat(first.balance()).isEqualTo(30);

        RewardWallet older = walletWith(D21, 200, 500);
        int already = older.accruedOn(D22).orElseThrow();
        assertThat(already).isZero();
        assertThat(older.accrue(run("r2", 30, D22), RULE, already))
                .map(RewardAccrual::amount)
                .contains(30);
        assertThat(older.balance()).isEqualTo(530);
        assertThat(older.accruedOn(D22)).hasValue(30);
        assertThat(older.accruedOn(D21)).isEmpty();
    }

    @Test
    @DisplayName("TC-6153 accrue: 같은 날이면 칼럼 합계에 더한다(120 + 50 = 170)")
    void accrue_sameDay() {
        RewardWallet wallet = walletWith(D22, 120, 120);
        assertThat(wallet.accrue(run("r", 50, D22), RULE, 120))
                .map(RewardAccrual::amount)
                .contains(50);
        assertThat(wallet.accruedOn(D22)).hasValue(170);
    }

    @Test
    @DisplayName("TC-6154 accrue: 보정 배치의 지난 날짜는 sumOn 합계로 상한을 세고 칼럼을 고치지 않는다")
    void accrue_olderDay_keepsColumns() {
        RewardWallet wallet = walletWith(D22, 100, 100);
        Optional<RewardAccrual> a = wallet.accrue(run("r-old", 50, D20), RULE, 180);

        assertThat(a).map(RewardAccrual::amount).contains(20);
        assertThat(wallet.balance()).isEqualTo(120);
        assertThat(wallet.accruedOn(D22)).hasValue(100);
    }

    @Test
    @DisplayName("적립 행은 회원·사유·근거·사건 시각을 싣고 0 이하 적립량은 기록하지 않는다")
    void accrue_recordFields_andZeroBase() {
        RewardWallet wallet = RewardWallet.open(9);
        RewardAccrual a = wallet.accrue(run("r1", 40, D22), RULE, 0).orElseThrow();
        assertThat(a.memberId()).isEqualTo(9);
        assertThat(a.isFor(AccrualReason.VALID_RUN, "r1")).isTrue();
        assertThat(a.accruedAt()).isEqualTo(D22.atTime(12, 0));

        assertThat(wallet.accrue(run("short", 0, D22), RULE, 40)).isEmpty();
        assertThat(wallet.accruedOn(D22)).hasValue(40);
    }

    @Test
    @DisplayName("구매: 잔액 차감·보유 추가, 이미 보유·잔액 부족 거부")
    void purchase() {
        RewardWallet wallet = RewardWallet.open(1);
        LocalDateTime noon = D21.atTime(12, 0);
        assertThatThrownBy(() -> wallet.purchase(CAP, noon))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.INSUFFICIENT_REWARD);

        wallet.accrue(run("r1", 60, D21), RULE, 0);
        wallet.purchase(CAP, noon);

        assertThat(wallet.balance()).isEqualTo(10);
        assertThat(wallet.owns(7)).isTrue();
        assertThat(wallet.owns(8)).isFalse();
        assertThatThrownBy(() -> wallet.purchase(CAP, noon))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.ALREADY_OWNED);
    }
}
