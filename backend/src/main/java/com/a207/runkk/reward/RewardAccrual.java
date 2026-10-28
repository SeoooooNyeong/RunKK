package com.a207.runkk.reward;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 적립 내역 한 건(자료사전 리워드레코드의 적립 내역). v0.11부터 RewardWallet이 컬렉션으로 갖지 않고 RewardAccrualRepository로만 읽고
 * 쓴다(D-109). (회원, 사유, 근거)는 한 번만 있다(5.3 고유 제약).
 */
@Entity
@Table(name = "reward_accrual")
public class RewardAccrual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    private AccrualReason reason;

    @Column(name = "source_id", nullable = false, length = 40)
    private String sourceId;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "accrued_at", nullable = false)
    private LocalDateTime accruedAt;

    protected RewardAccrual() {}

    RewardAccrual(
            long memberId,
            AccrualReason reason,
            String sourceId,
            int amount,
            LocalDateTime accruedAt) {
        this.memberId = memberId;
        this.reason = reason;
        this.sourceId = sourceId;
        this.amount = amount;
        this.accruedAt = accruedAt;
    }

    boolean isFor(AccrualReason r, String source) {
        return reason == r && sourceId.equals(source);
    }

    public long memberId() {
        return memberId;
    }

    public AccrualReason reason() {
        return reason;
    }

    public String sourceId() {
        return sourceId;
    }

    public int amount() {
        return amount;
    }

    /** 사건 발생 시각(하루 상한의 "그날"). */
    public LocalDateTime accruedAt() {
        return accruedAt;
    }
}
