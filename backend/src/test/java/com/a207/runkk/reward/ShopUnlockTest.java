package com.a207.runkk.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.RewardRule;
import com.a207.runkk.policy.ShopCatalog;
import com.a207.runkk.policy.ShopItem;
import com.a207.runkk.policy.ShopUnlock;
import com.a207.runkk.policy.UnlockKind;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W8 상점 잠긴 아이템·그림 키(S15P31A207-299, 사용자 결정 A1, contract-W8.md 2.4). */
@DisplayName("상점 잠긴 아이템")
class ShopUnlockTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 9, 0);
    private static final long M = 1;
    private static final ShopUnlock HALF =
            new ShopUnlock(UnlockKind.LONGEST_RUN, 21_097, "하프 완주하면 열려요");
    private static final ShopUnlock KM100 =
            new ShopUnlock(UnlockKind.TOTAL_DISTANCE, 100_000, "누적 100km 달리면 열려요");
    private static final ShopUnlock TEN_RUNS =
            new ShopUnlock(UnlockKind.RUN_COUNT, 10, "10번 달리면 열려요");

    private final InMemoryRewardWalletRepository wallets = new InMemoryRewardWalletRepository();
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final RunQueryService runs = mock(RunQueryService.class);
    private final ShopService shop =
            new ShopService(
                    wallets, policy, runs, Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));

    @BeforeEach
    void catalog() {
        when(policy.rewardRule()).thenReturn(new RewardRule(10, 20, 200));
        when(policy.shopCatalog())
                .thenReturn(
                        new ShopCatalog(
                                List.of(
                                        new ShopItem(
                                                1,
                                                "라임 싱글렛",
                                                ItemSlot.TOP,
                                                300,
                                                true,
                                                "top_lime_singlet",
                                                null),
                                        new ShopItem(
                                                6,
                                                "완주 기념 티",
                                                ItemSlot.TOP,
                                                300,
                                                true,
                                                "top_finisher_tee",
                                                HALF),
                                        new ShopItem(
                                                23, "카본", ItemSlot.SHOES, 100, true, null, KM100),
                                        new ShopItem(
                                                34,
                                                "캡",
                                                ItemSlot.HAT,
                                                100,
                                                true,
                                                null,
                                                TEN_RUNS))));
        when(runs.longestDistance(anyLong())).thenReturn(Optional.empty());
        when(runs.validDistanceOf(anyLong())).thenReturn(Distance.ofMeters(0));
        when(runs.validRunCount(anyLong())).thenReturn(0);
    }

    private void balance(int amount) {
        RewardWallet wallet = wallets.findOrCreateForUpdate(M);
        wallet.accrue(
                new AccrualRequest(AccrualReason.EVENT, "e-" + amount, amount, NOW),
                new RewardRule(10, 20, 200),
                0);
        wallets.save(wallet);
    }

    private ShopView.Entry entry(long itemId) {
        return shop.shop(M, Optional.empty()).items().stream()
                .filter(e -> e.itemId() == itemId)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("조건 없는 아이템은 잠기지 않고 조건 문구가 없으며 그림 키를 싣는다")
    void noCondition() {
        ShopView.Entry e = entry(1);

        assertThat(e.assetKey()).isEqualTo("top_lime_singlet");
        assertThat(e.locked()).isFalse();
        assertThat(e.unlockCondition()).isNull();
    }

    @Test
    @DisplayName("조건을 못 채우면 잠기고 문구를 싣는다. 경계값(=)에서 열린다")
    void conditions_lockUntilMet() {
        assertThat(entry(6).locked()).isTrue();
        assertThat(entry(6).unlockCondition()).isEqualTo("하프 완주하면 열려요");
        assertThat(entry(23).locked()).isTrue();
        assertThat(entry(34).locked()).isTrue();

        when(runs.longestDistance(M)).thenReturn(Optional.of(Distance.ofMeters(21_097)));
        when(runs.validDistanceOf(M)).thenReturn(Distance.ofMeters(99_999));
        when(runs.validRunCount(M)).thenReturn(10);

        assertThat(entry(6).locked()).isFalse();
        assertThat(entry(6).unlockCondition()).isEqualTo("하프 완주하면 열려요");
        assertThat(entry(23).locked()).isTrue();
        assertThat(entry(34).locked()).isFalse();
    }

    @Test
    @DisplayName("조건이 없는 목록이면 기록을 조회하지 않는다")
    void noConditionCatalog_skipsRunQueries() {
        when(policy.shopCatalog())
                .thenReturn(
                        new ShopCatalog(List.of(new ShopItem(1, "모자", ItemSlot.HAT, 30, true))));

        shop.shop(M, Optional.empty());

        verify(runs, never()).validDistanceOf(anyLong());
        verify(runs, never()).longestDistance(anyLong());
        verify(runs, never()).validRunCount(anyLong());
    }

    @Test
    @DisplayName("잠긴 아이템 구매는 ITEM_LOCKED이고 잔액은 그대로, 조건을 채우면 산다")
    void purchaseLocked() {
        balance(500);

        assertThatThrownBy(() -> shop.purchase(M, 6))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.ITEM_LOCKED));
        assertThat(wallets.findByMember(M).orElseThrow().balance()).isEqualTo(500);

        when(runs.longestDistance(M)).thenReturn(Optional.of(Distance.ofMeters(22_000)));
        assertThat(shop.purchase(M, 6).balance()).isEqualTo(200);
    }

    @Test
    @DisplayName("이미 보유한 아이템은 조건이 다시 안 맞아도 잠기지 않는다")
    void owned_neverLocked() {
        balance(500);
        when(runs.validRunCount(M)).thenReturn(10);
        shop.purchase(M, 34);
        when(runs.validRunCount(M)).thenReturn(0);

        assertThat(entry(34).owned()).isTrue();
        assertThat(entry(34).locked()).isFalse();
        assertThatThrownBy(() -> shop.purchase(M, 34))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.ALREADY_OWNED));
    }
}
