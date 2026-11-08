package com.a207.runkk.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * 이벤트 포인트 지급 이력 한 건(S15P31A207-275, V35_2). (회원, 이벤트, 대상)마다 한 번만 있다(uq_event_point_grant). 대상은
 * "celebrity-11"처럼 응모 항목 키다. 적립 자체는 reward_accrual(사유 EVENT, 근거 "이벤트 ID:대상")에 남는다.
 */
@Entity
@Table(
        name = "event_point_grant",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_event_point_grant",
                        columnNames = {"member_id", "event_id", "target_key"}))
public class EventPointGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private long memberId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private long eventId;

    @Column(name = "target_key", nullable = false, updatable = false, length = 40)
    private String targetKey;

    @Column(name = "amount", nullable = false, updatable = false)
    private int amount;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt;

    protected EventPointGrant() {}

    EventPointGrant(
            long memberId, long eventId, String targetKey, int amount, LocalDateTime grantedAt) {
        this.memberId = memberId;
        this.eventId = eventId;
        this.targetKey = targetKey;
        this.amount = amount;
        this.grantedAt = grantedAt;
    }
}
