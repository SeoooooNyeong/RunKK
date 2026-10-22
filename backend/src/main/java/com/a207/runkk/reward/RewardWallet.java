package com.a207.runkk.reward;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.RewardRule;
import com.a207.runkk.policy.ShopItem;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 회원 한 명의 리워드 지갑(설계 6.1.8, 6.2 A-17). 잔액과 보유 아이템은 구매가 잔액을 아이템으로 바꾸는 한 자료다(design-review.md 9.3
 * V21). 동시 적립·구매는 응용 서비스가 지갑 행을 잠근 뒤 부른다(5.3).
 */
@Entity
@Table(name = "reward_wallet")
public class RewardWallet {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "balance", nullable = false)
    private int balance;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private List<RewardAccrual> accruals = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private List<OwnedItem> ownedItems = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected RewardWallet() {}

    private RewardWallet(long memberId) {
        this.memberId = memberId;
    }

    /** 잔액 0의 새 지갑. */
    public static RewardWallet open(long memberId) {
        return new RewardWallet(memberId);
    }

    /**
     * 같은 (사유, 근거)가 있으면 0. 적립량 = min(base, dailyCap − 오늘 합). 0 이하면 기록하지 않는다. 실제 적립량을 돌려준다(FR-159).
     */
    public int accrue(AccrualRequest req, RewardRule rule) {
        if (accruedFor(req.reason(), req.sourceId()) > 0) {
            return 0;
        }
        int todaySum =
                accruals.stream()
                        .filter(a -> a.isOn(req.at()))
                        .mapToInt(RewardAccrual::amount)
                        .sum();
        int amount = Math.min(req.baseAmount(), rule.dailyCap() - todaySum);
        if (amount <= 0) {
            return 0;
        }
        balance += amount;
        accruals.add(new RewardAccrual(req.reason(), req.sourceId(), amount, req.at()));
        return amount;
    }

    /** 보유면 ALREADY_OWNED, 잔액 &lt; 가격이면 INSUFFICIENT_REWARD. 잔액을 빼고 보유에 더한다(FR-160). */
    public void purchase(ShopItem item, LocalDateTime now) {
        if (owns(item.id())) {
            throw new BusinessRuleException(ReasonCode.ALREADY_OWNED);
        }
        if (balance < item.price()) {
            throw new BusinessRuleException(ReasonCode.INSUFFICIENT_REWARD);
        }
        balance -= item.price();
        ownedItems.add(new OwnedItem(item.id(), now));
    }

    public boolean owns(long itemId) {
        return ownedItems.stream().anyMatch(o -> o.itemId() == itemId);
    }

    /** 보유 아이템 ID(상점 보유 여부). */
    public Set<Long> ownedItemIds() {
        return ownedItems.stream().map(OwnedItem::itemId).collect(Collectors.toUnmodifiableSet());
    }

    /** 근거 하나의 적립량. 없으면 0(FR-078, FR-093). */
    public int accruedFor(AccrualReason reason, String sourceId) {
        return accruals.stream()
                .filter(a -> a.isFor(reason, sourceId))
                .mapToInt(RewardAccrual::amount)
                .sum();
    }

    public int balance() {
        return balance;
    }

    public long memberId() {
        return memberId;
    }
}
