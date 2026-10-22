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

/** 적립 내역 한 건(자료사전 리워드레코드의 적립 내역). RewardWallet이 합성으로 가진다. (회원, 사유, 근거)는 한 번만 있다(5.3 고유 제약). */
@Entity
@Table(name = "reward_accrual")
public class RewardAccrual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    RewardAccrual(AccrualReason reason, String sourceId, int amount, LocalDateTime accruedAt) {
        this.reason = reason;
        this.sourceId = sourceId;
        this.amount = amount;
        this.accruedAt = accruedAt;
    }

    boolean isFor(AccrualReason r, String source) {
        return reason == r && sourceId.equals(source);
    }

    boolean isOn(LocalDateTime day) {
        return accruedAt.toLocalDate().equals(day.toLocalDate());
    }

    int amount() {
        return amount;
    }
}
