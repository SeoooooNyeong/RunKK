package com.a207.runkk.reward;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 보유 아이템 한 건(자료사전 리워드레코드의 아이템ID + 구매일시). (회원, 아이템)은 한 번만 있다(5.3 고유 제약). */
@Entity
@Table(name = "owned_item")
public class OwnedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private long itemId;

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;

    protected OwnedItem() {}

    OwnedItem(long itemId, LocalDateTime purchasedAt) {
        this.itemId = itemId;
        this.purchasedAt = purchasedAt;
    }

    long itemId() {
        return itemId;
    }
}
