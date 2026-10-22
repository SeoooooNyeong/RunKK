package com.a207.runkk.running;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 7일 기한의 원자료 파기 지시 하나(설계 6.1.3, 상태도 2.4.10). 동의를 다시 해도 이미 만든 지시는 그대로 실행한다. */
@Entity
@Table(name = "disposal_order")
public class DisposalOrder {

    private static final int DUE_DAYS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 12)
    private DisposalKind kind;

    @Column(name = "due_at", nullable = false, updatable = false)
    private LocalDateTime dueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DisposalStatus status;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "device_confirmed_at")
    private LocalDateTime deviceConfirmedAt;

    protected DisposalOrder() {}

    private DisposalOrder(long memberId, DisposalKind kind, LocalDateTime withdrawnAt) {
        this.memberId = memberId;
        this.kind = kind;
        this.dueAt = withdrawnAt.plusDays(DUE_DAYS);
        this.status = DisposalStatus.SCHEDULED;
    }

    /** 동의 철회 시각 + 7일 기한의 지시(SCHEDULED). */
    public static DisposalOrder schedule(
            long memberId, DisposalKind kind, LocalDateTime withdrawnAt) {
        if (kind == null || withdrawnAt == null) {
            throw new IllegalArgumentException("파기 지시 항목이 비었다");
        }
        return new DisposalOrder(memberId, kind, withdrawnAt);
    }

    /** SCHEDULED이면 실행 대상(기한 전이라도 매일 실행해 7일 안에 끝낸다). */
    public boolean isDue(LocalDateTime now) {
        return status == DisposalStatus.SCHEDULED;
    }

    /**
     * 서버 파기 완료. 기기 확인이 먼저 있었으면 DEVICE_CONFIRMED, 아니면 EXECUTED. SCHEDULED가 아니면
     * IllegalStateException.
     */
    public void execute(LocalDateTime now) {
        if (status != DisposalStatus.SCHEDULED) {
            throw new IllegalStateException("이미 실행한 파기 지시");
        }
        this.executedAt = now;
        this.status =
                deviceConfirmedAt != null
                        ? DisposalStatus.DEVICE_CONFIRMED
                        : DisposalStatus.EXECUTED;
    }

    /**
     * 기기 파기 확인. EXECUTED면 DEVICE_CONFIRMED, SCHEDULED면 확인 시각만 기록. 이미 끝났으면 IllegalStateException.
     */
    public void confirmDevice(LocalDateTime now) {
        if (status == DisposalStatus.DEVICE_CONFIRMED) {
            throw new IllegalStateException("이미 기기 파기를 확인한 지시");
        }
        this.deviceConfirmedAt = now;
        if (status == DisposalStatus.EXECUTED) {
            this.status = DisposalStatus.DEVICE_CONFIRMED;
        }
    }

    /** 기한(7일)을 넘겼는데 서버 파기가 안 끝났다(운영 로그 경보, NFR-036). */
    public boolean isOverdue(LocalDateTime now) {
        return status == DisposalStatus.SCHEDULED && now.isAfter(dueAt);
    }

    Long id() {
        return id;
    }

    long memberId() {
        return memberId;
    }

    DisposalKind kind() {
        return kind;
    }

    DisposalStatus status() {
        return status;
    }

    LocalDateTime deviceConfirmedAt() {
        return deviceConfirmedAt;
    }

    DisposalOrderView view() {
        return new DisposalOrderView(id, kind, dueAt, status);
    }
}
