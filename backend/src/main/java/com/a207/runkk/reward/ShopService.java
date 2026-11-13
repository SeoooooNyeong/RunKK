package com.a207.runkk.reward;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.ShopItem;
import com.a207.runkk.policy.ShopUnlock;
import com.a207.runkk.policy.UnlockKind;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상점 조회·구매(설계 6.1.8, srs.md 6.3 6.5). 상점 목록은 운영 기준값(ShopCatalog)이다. 해금 조건이 있는 아이템은 조회·구매 때마다 회원
 * 기록으로 판정해 잠근다(W8, S15P31A207-299, 사용자 결정 A1). 판정 결과는 저장하지 않는다.
 */
@Service
public class ShopService {

    private final RewardWalletRepository rewardWalletRepository;
    private final OperationPolicy operationPolicy;
    private final RunQueryService runQueryService;
    private final Clock clock;

    public ShopService(
            RewardWalletRepository rewardWalletRepository,
            OperationPolicy operationPolicy,
            RunQueryService runQueryService,
            Clock clock) {
        this.rewardWalletRepository = rewardWalletRepository;
        this.operationPolicy = operationPolicy;
        this.runQueryService = runQueryService;
        this.clock = clock;
    }

    /** 판매 중인 아이템과 이미 보유한 아이템 + 보유·잠김 여부. 부위가 비면 전체. */
    @Transactional(readOnly = true)
    public ShopView shop(long memberId, Optional<ItemSlot> slot) {
        Optional<RewardWallet> wallet = rewardWalletRepository.findByMember(memberId);
        Set<Long> owned = wallet.map(RewardWallet::ownedItemIds).orElse(Set.of());
        Progress progress = new Progress(memberId);
        return new ShopView(
                operationPolicy.shopCatalog().bySlot(slot).stream()
                        .filter(item -> item.onSale() || owned.contains(item.id()))
                        .map(
                                item -> {
                                    boolean has = owned.contains(item.id());
                                    return ShopView.Entry.of(
                                            item, has, !has && progress.locks(item));
                                })
                        .toList(),
                wallet.map(RewardWallet::balance).orElse(0));
    }

    /**
     * 목록에 없으면 ITEM_NOT_FOUND, 판매 중지면 ITEM_NOT_ON_SALE, 보유하지 않았고 해금 조건을 못 채웠으면 ITEM_LOCKED →
     * RewardWallet.purchase(FR-160).
     */
    @Transactional
    public PurchaseResult purchase(long memberId, long itemId) {
        ShopItem item =
                operationPolicy
                        .shopCatalog()
                        .find(itemId)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.ITEM_NOT_FOUND));
        if (!item.onSale()) {
            throw new BusinessRuleException(ReasonCode.ITEM_NOT_ON_SALE);
        }
        RewardWallet wallet = rewardWalletRepository.findOrCreateForUpdate(memberId);
        if (!wallet.owns(itemId) && new Progress(memberId).locks(item)) {
            throw new BusinessRuleException(ReasonCode.ITEM_LOCKED);
        }
        wallet.purchase(item, LocalDateTime.now(clock));
        rewardWalletRepository.save(wallet);
        return new PurchaseResult(itemId, wallet.balance());
    }

    /** 한 요청 안에서 조건 종류마다 기록을 한 번만 읽는다. 조건이 없는 목록이면 읽지 않는다. */
    private final class Progress {

        private final long memberId;
        private final Map<UnlockKind, Long> values = new EnumMap<>(UnlockKind.class);

        Progress(long memberId) {
            this.memberId = memberId;
        }

        boolean locks(ShopItem item) {
            return item.unlockCondition().map(u -> !met(u)).orElse(false);
        }

        private boolean met(ShopUnlock unlock) {
            return values.computeIfAbsent(unlock.kind(), this::load) >= unlock.threshold();
        }

        private long load(UnlockKind kind) {
            return switch (kind) {
                case TOTAL_DISTANCE -> runQueryService.validDistanceOf(memberId).meters();
                case LONGEST_RUN ->
                        runQueryService.longestDistance(memberId).map(Distance::meters).orElse(0L);
                case RUN_COUNT -> runQueryService.validRunCount(memberId);
            };
        }
    }
}
