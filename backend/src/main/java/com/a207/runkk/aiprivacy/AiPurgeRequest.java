package com.a207.runkk.aiprivacy;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** 탈퇴 트랜잭션과 함께 기록하고, 외부 파기는 짧은 실행 임대로 직렬화한다. */
@Entity
@Table(name = "ai_purge_request")
public class AiPurgeRequest {
    @Id
    @Column(name = "member_id")
    private long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PurgeStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @Column(nullable = false)
    private LocalDateTime nextAt;

    @Column(nullable = false)
    private boolean retentionCheckDone;

    @Column(length = 36)
    private String leaseToken;

    private LocalDateTime leaseUntil;
    @Version private Long version;

    protected AiPurgeRequest() {}

    public static AiPurgeRequest of(long memberId, LocalDateTime now) {
        if (memberId <= 0 || now == null) throw new IllegalArgumentException("AI 파기 요청 오류");
        AiPurgeRequest request = new AiPurgeRequest();
        request.memberId = memberId;
        request.requestedAt = now.truncatedTo(ChronoUnit.MICROS);
        request.nextAt = request.requestedAt;
        request.status = PurgeStatus.PENDING;
        return request;
    }

    String claim(LocalDateTime now, boolean force) {
        if (leaseUntil != null && leaseUntil.isAfter(now)) return null;
        if (status == PurgeStatus.DONE && retentionCheckDone) return null;
        if (!force && nextAt.isAfter(now)) return null;
        if (status == PurgeStatus.DONE) status = PurgeStatus.PENDING;
        leaseToken = UUID.randomUUID().toString();
        leaseUntil = now.plusMinutes(1);
        attempts++;
        return leaseToken;
    }

    boolean owns(String token) {
        return token.equals(leaseToken);
    }

    void succeeded(LocalDateTime now) {
        status = PurgeStatus.DONE;
        retentionCheckDone = !now.isBefore(requestedAt.plusDays(7));
        if (!retentionCheckDone) nextAt = requestedAt.plusDays(7);
        release();
    }

    boolean failed(LocalDateTime now) {
        boolean alert = !now.isBefore(requestedAt.plusDays(7)) && status != PurgeStatus.ALERTED;
        if (alert) status = PurgeStatus.ALERTED;
        else if (status != PurgeStatus.ALERTED) status = PurgeStatus.PENDING;
        nextAt = now.toLocalDate().plusDays(1).atStartOfDay();
        release();
        return alert;
    }

    private void release() {
        leaseToken = null;
        leaseUntil = null;
    }

    public long memberId() {
        return memberId;
    }

    public PurgeStatus status() {
        return status;
    }

    public int attempts() {
        return attempts;
    }
}
