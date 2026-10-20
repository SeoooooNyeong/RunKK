package com.a207.runkk.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/** 감사 대상 행위 한 건을 고치지 않고 보관한다(설계 6.1.11, D14 추가 전용 테이블). 변경 메소드가 없다. */
@Entity
@Immutable
@Table(name = "audit_record")
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime at;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 100)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, updatable = false, length = 20)
    private ActorRole actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 30)
    private AuditAction action;

    @Column(name = "target_id", nullable = false, updatable = false, length = 100)
    private String targetId;

    @Column(name = "before_value", updatable = false, columnDefinition = "text")
    private String before;

    @Column(name = "after_value", updatable = false, columnDefinition = "text")
    private String after;

    protected AuditRecord() {}

    private AuditRecord(AuditEntry e, LocalDateTime at) {
        if (at == null) {
            throw new IllegalArgumentException("기록 일시가 비었다");
        }
        this.at = at;
        this.actorId = e.actorId();
        this.actorRole = e.actorRole();
        this.action = e.action();
        this.targetId = e.targetId();
        this.before = e.before();
        this.after = e.after();
    }

    public static AuditRecord of(AuditEntry e, LocalDateTime at) {
        return new AuditRecord(e, at);
    }

    AuditRecordView view() {
        return new AuditRecordView(at, actorId, actorRole, action, targetId, before, after);
    }
}
