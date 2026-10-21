package com.a207.runkk.reward;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.ShopItem;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상점 조회·구매(설계 6.1.8, srs.md 6.3 6.5). 상점 목록은 운영 기준값(ShopCatalog)이다. */
@Service
public class ShopService {

    private final RewardWalletRepository rewardWalletRepository;
    private final OperationPolicy operationPolicy;
    private final Clock clock;

    public ShopService(
            RewardWalletRepository rewardWalletRepository,
            OperationPolicy operationPolicy,
            Clock clock) {
        this.rewardWalletRepository = rewardWalletRepository;
        this.operationPolicy = operationPolicy;
        this.clock = clock;
    }

    /** 판매 중인 아이템과 이미 보유한 아이템 + 보유 여부. 부위가 비면 전체. */
    @Transactional(readOnly = true)
    public ShopView shop(long memberId, Optional<ItemSlot> slot) {
        Optional<RewardWallet> wallet = rewardWalletRepository.findByMember(memberId);
        Set<Long> owned = wallet.map(RewardWallet::ownedItemIds).orElse(Set.of());
        return new ShopView(
                operationPolicy.shopCatalog().bySlot(slot).stream()
                        .filter(item -> item.onSale() || owned.contains(item.id()))
                        .map(item -> ShopView.Entry.of(item, owned.contains(item.id())))
                        .toList(),
                wallet.map(RewardWallet::balance).orElse(0));
    }

    /** 목록에 없으면 ITEM_NOT_FOUND, 판매 중지면 ITEM_NOT_ON_SALE → RewardWallet.purchase(FR-160). */
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
        wallet.purchase(item, LocalDateTime.now(clock));
        rewardWalletRepository.save(wallet);
        return new PurchaseResult(itemId, wallet.balance());
    }
}
