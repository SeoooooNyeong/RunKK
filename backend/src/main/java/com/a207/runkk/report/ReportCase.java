package com.a207.runkk.report;

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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 신고 대상 하나의 처리 상태(설계 6.1.12a, 상태도 02 ReportCase, FR-215, FR-216, D-104). 대상 하나에 한 행이고 (targetKind,
 * targetId)가 고유하다. 자동 숨김은 마지막 기각 뒤 접수된 서로 다른 신고자 수(openReporterCount)로 판정한다(D-149). 상태 전이: RECEIVED
 * → AUTO_HIDDEN → HIDDEN·DISMISSED, RECEIVED → HIDDEN·DISMISSED, DISMISSED → RECEIVED(reopen).
 */
@Entity
@Table(
        name = "report_case",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_report_case_target",
                        columnNames = {"target_kind", "target_id"}))
public class ReportCase {

    /** 자동 숨김 기준: 서로 다른 열린 신고자 수(D-113. 운영 기준값이 아니다). */
    public static final int AUTO_HIDE_REPORTERS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_kind", nullable = false, updatable = false, length = 15)
    private ReportTargetKind targetKind;

    @Column(name = "target_id", nullable = false, updatable = false)
    private long targetId;

    @Column(name = "crew_id", nullable = false, updatable = false)
    private long crewId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ReportStatus status;

    @Column(name = "open_reporter_count", nullable = false)
    private int openReporterCount;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolver_id", length = 100)
    private String resolverId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ReportCase() {}

    private ReportCase(
            ReportTargetKind targetKind, long targetId, long crewId, LocalDateTime openedAt) {
        this.targetKind = targetKind;
        this.targetId = targetId;
        this.crewId = crewId;
        this.status = ReportStatus.RECEIVED;
        this.openedAt = openedAt;
    }

    /**
     * 대상의 첫 신고로 사례를 연다(RECEIVED, 열린 신고자 0). 신고 한 건은 이어서 addReport로 센다.
     *
     * @param now 사례를 연 시각
     */
    public static ReportCase open(
            ReportTargetKind targetKind, long targetId, long crewId, LocalDateTime now) {
        if (targetKind == null || now == null) {
            throw new IllegalArgumentException("대상 종류·연 시각이 비었다");
        }
        return new ReportCase(targetKind, targetId, crewId, now);
    }

    /**
     * 신고 한 건 반영(FR-215). 같은 신고자 중복은 호출 전에 걸렀다. 열린 신고자 수를 늘리고, RECEIVED에서 AUTO_HIDE_REPORTERS에 닿으면
     * AUTO_HIDDEN으로 바꾸고 참(호출자가 같은 트랜잭션에서 콘텐츠를 숨긴다). AUTO_HIDDEN·HIDDEN이면 수만 센다.
     */
    public boolean addReport() {
        openReporterCount++;
        if (status == ReportStatus.RECEIVED && openReporterCount >= AUTO_HIDE_REPORTERS) {
            status = ReportStatus.AUTO_HIDDEN;
            return true;
        }
        return false;
    }

    /** 운영자 숨김(FR-216, D-138). 이미 HIDDEN·DISMISSED면 REPORT_DECIDED. */
    public void hide(String operatorId, LocalDateTime now) {
        resolve(ReportStatus.HIDDEN, operatorId, now);
    }

    /** 운영자 기각(FR-216, D-138). 이미 HIDDEN·DISMISSED면 REPORT_DECIDED. */
    public void dismiss(String operatorId, LocalDateTime now) {
        resolve(ReportStatus.DISMISSED, operatorId, now);
    }

    private void resolve(ReportStatus decided, String operatorId, LocalDateTime now) {
        if (operatorId == null || operatorId.isBlank() || now == null) {
            throw new IllegalArgumentException("운영자 계정·처리 시각이 비었다");
        }
        if (isDecided()) {
            throw new BusinessRuleException(ReasonCode.REPORT_DECIDED);
        }
        this.status = decided;
        this.resolvedAt = now;
        this.resolverId = operatorId;
        this.openReporterCount = 0;
    }

    /**
     * 기각 뒤 다른 신고자의 새 신고로 사례를 다시 연다(D-104, D-149): RECEIVED, 열린 신고자 0부터, openedAt = now, 처리 기록을 비운다.
     * DISMISSED가 아니면 IllegalStateException. 같은 신고자의 재신고는 호출자가 먼저 ALREADY_REPORTED로 거부했다.
     *
     * @param now 다시 연 시각
     */
    public void reopen(LocalDateTime now) {
        if (status != ReportStatus.DISMISSED) {
            throw new IllegalStateException("기각한 사례만 다시 연다: " + status);
        }
        this.status = ReportStatus.RECEIVED;
        this.openReporterCount = 0;
        this.openedAt = Objects.requireNonNull(now, "다시 연 시각");
        this.resolvedAt = null;
        this.resolverId = null;
    }

    private boolean isDecided() {
        return status == ReportStatus.HIDDEN || status == ReportStatus.DISMISSED;
    }

    public Long getId() {
        return id;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public ReportTargetKind getTargetKind() {
        return targetKind;
    }

    public long getTargetId() {
        return targetId;
    }

    public long getCrewId() {
        return crewId;
    }

    public int getOpenReporterCount() {
        return openReporterCount;
    }

    /** 사례를 연(다시 연) 시각. 관리툴 목록 순서((status, opened_at) 색인)다. */
    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    /** 처리 시각, 열린 사례면 null. */
    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    /** 처리한 운영자 계정, 열린 사례면 null. */
    public String getResolverId() {
        return resolverId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ReportCase c && id != null && Objects.equals(id, c.id);
    }

    @Override
    public int hashCode() {
        return ReportCase.class.hashCode();
    }
}
