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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 회원 한 명의 리워드 지갑(설계 6.1.8, 6.2 A-17). 잔액과 보유 아이템은 구매가 잔액을 아이템으로 바꾸는 한 자료다(design-review.md 9.3
 * V21). 동시 적립·구매는 응용 서비스가 지갑 행을 잠근 뒤 부른다(5.3). 적립 내역은 컬렉션으로 갖지 않고(지갑을 읽을 때 전체 내역을 읽지 않는다) 가장 최근
 * 적립일과 그날 합계만 집계 칼럼 두 개로 둔다(D-109). 지난 날짜 합계와 중복 확인은 RewardService가 RewardAccrualRepository로 한다.
 */
@Entity
@Table(name = "reward_wallet")
public class RewardWallet {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "balance", nullable = false)
    private int balance;

    /** 가장 최근 적립일(사건 발생일). 적립이 없으면 null. */
    @Column(name = "last_accrual_date")
    private LocalDate lastAccrualDate;

    /** lastAccrualDate 날의 적립 합계. */
    @Column(name = "last_accrual_date_amount", nullable = false)
    private int lastAccrualDateAmount;

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
     * 적립량 = min(base, dailyCap − alreadyOnThatDay). 0 이하면 비어 있다(기록하지 않는다). 아니면 잔액을 늘리고, 사건 발생일이
     * lastAccrualDate 이상이면 집계 칼럼을 고친 뒤(같은 날 더하기, 더 새 날 바꾸기) 저장할 적립 행을 돌려준다(FR-159, A-17). 지난 날짜(보정
     * 배치)는 칼럼을 고치지 않는다. 중복 (사유, 근거) 확인은 호출자가 먼저 한다(D-109).
     */
    public Optional<RewardAccrual> accrue(
            AccrualRequest req, RewardRule rule, int alreadyOnThatDay) {
        int amount = Math.min(req.baseAmount(), rule.dailyCap() - alreadyOnThatDay);
        if (amount <= 0) {
            return Optional.empty();
        }
        balance += amount;
        LocalDate day = req.at().toLocalDate();
        if (lastAccrualDate == null || day.isAfter(lastAccrualDate)) {
            lastAccrualDate = day;
            lastAccrualDateAmount = alreadyOnThatDay + amount;
        } else if (day.equals(lastAccrualDate)) {
            lastAccrualDateAmount += amount;
        }
        return Optional.of(
                new RewardAccrual(memberId, req.reason(), req.sourceId(), amount, req.at()));
    }

    /**
     * 그날 적립 합계. 집계 칼럼의 날이면 그 합계, 더 새 날(또는 적립 없음)이면 0, 더 옛날이면 비어 있다(호출자가
     * RewardAccrualRepository.sumOn으로 읽는다, D-109, D-123).
     */
    public OptionalInt accruedOn(LocalDate day) {
        if (lastAccrualDate == null || day.isAfter(lastAccrualDate)) {
            return OptionalInt.of(0);
        }
        if (day.equals(lastAccrualDate)) {
            return OptionalInt.of(lastAccrualDateAmount);
        }
        return OptionalInt.empty();
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

    public int balance() {
        return balance;
    }

    public long memberId() {
        return memberId;
    }
}
