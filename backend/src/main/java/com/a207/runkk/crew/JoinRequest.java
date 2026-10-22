package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

/**
 * 승인제 크루 가입 신청 한 건(설계 6.1.7, 상태도 2.4.4, 13.1 예). 같은 크루에 대기 중 신청은 회원마다 하나다(5.3 부분 고유 인덱스). PENDING이
 * 아닌 상태에서 approve·reject·dissolve를 부르면 IllegalStateException이다.
 */
@Entity
@Table(name = "join_request")
public class JoinRequest {

    /** 결정 없이 만료되기까지(Q-40). */
    static final int EXPIRY_DAYS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "crew_id", nullable = false)
    private long crewId;

    @Column(name = "member_id", nullable = false)
    private long memberId;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private JoinRequestStatus status;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** 소멸 사유(정원 초과, 다른 크루 가입). */
    @Column(name = "reason", length = 40)
    private String reason;

    /** 낙관적 잠금: 승인·거절·취소가 겹치면 늦은 쪽이 409로 끝나 처리된 신청을 덮어쓰지 않는다. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected JoinRequest() {}

    JoinRequest(long crewId, long memberId, LocalDateTime now) {
        this.crewId = crewId;
        this.memberId = memberId;
        this.requestedAt = now;
        this.status = JoinRequestStatus.PENDING;
    }

    /**
     * 7일이 지난 신청은 만료 작업이 돌기 전이라도 승인하지 않는다: BusinessRuleException(REQUEST_DECIDED)(06-04, 코드 리뷰 2차
     * L12).
     */
    public void approve(LocalDateTime now) {
        if (isDueToExpire(now)) {
            throw new BusinessRuleException(ReasonCode.REQUEST_DECIDED);
        }
        decide(JoinRequestStatus.APPROVED, now);
    }

    /** PENDING이고 requestedAt + 7일 ≤ now(만료 대상)인가. */
    public boolean isDueToExpire(LocalDateTime now) {
        return status == JoinRequestStatus.PENDING
                && !requestedAt.plusDays(EXPIRY_DAYS).isAfter(now);
    }

    public void reject(LocalDateTime now) {
        decide(JoinRequestStatus.REJECTED, now);
    }

    /** 승인 시점에 정원이 찼거나 신청자가 다른 크루에 들어갔다(FR-123). */
    public void dissolve(String reason, LocalDateTime now) {
        decide(JoinRequestStatus.DISSOLVED, now);
        this.reason = reason;
    }

    /** 신청자 본인의 대기 중 신청만. 아니면 BusinessRuleException(NOT_CANCELLABLE)(FR-143). */
    public void cancel(long by, LocalDateTime now) {
        if (status != JoinRequestStatus.PENDING || by != memberId) {
            throw new BusinessRuleException(ReasonCode.NOT_CANCELLABLE);
        }
        decide(JoinRequestStatus.CANCELLED, now);
    }

    /** PENDING이고 requestedAt + 7일 ≤ now면 EXPIRED, true. */
    public boolean expire(LocalDateTime now) {
        if (!isDueToExpire(now)) {
            return false;
        }
        decide(JoinRequestStatus.EXPIRED, now);
        return true;
    }

    public boolean isPending() {
        return status == JoinRequestStatus.PENDING;
    }

    private void decide(JoinRequestStatus next, LocalDateTime now) {
        if (status != JoinRequestStatus.PENDING) {
            throw new IllegalStateException("처리된 신청: " + status);
        }
        this.status = next;
        this.decidedAt = now;
    }

    public Long getId() {
        return id;
    }

    public long crewId() {
        return crewId;
    }

    public long memberId() {
        return memberId;
    }

    public LocalDateTime requestedAt() {
        return requestedAt;
    }

    public JoinRequestStatus status() {
        return status;
    }

    public String reason() {
        return reason;
    }
}
