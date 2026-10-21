package com.a207.runkk.notification;

import com.a207.runkk.policy.NotificationRetention;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 회원에게 보낸 알림 한 건(설계 6.1.10, 상태도 2.4.14 UNREAD → READ). 알림함에 30일 보관한다(FR-175). */
@Entity
@Table(name = "notification")
public class Notification {

    private static final int CONTENT_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private long recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 30)
    private NotificationType type;

    @Column(name = "content", nullable = false, updatable = false, length = CONTENT_MAX)
    private String content;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_screen", length = 30, updatable = false)
    private Screen targetScreen;

    @Column(name = "target_id", length = 100, updatable = false)
    private String targetId;

    protected Notification() {}

    private Notification(NotificationRequest req, LocalDateTime occurredAt) {
        this.recipientId = req.recipientId();
        this.type = req.type();
        this.content =
                req.content().length() > CONTENT_MAX
                        ? req.content().substring(0, CONTENT_MAX)
                        : req.content();
        this.occurredAt = occurredAt;
        this.read = false;
        if (req.target() != null) {
            this.targetScreen = req.target().screen();
            this.targetId = req.target().targetId();
        }
    }

    /** 알림 사건을 알림함 한 건으로 만든다(UNREAD). */
    public static Notification of(NotificationRequest req, LocalDateTime occurredAt) {
        if (occurredAt == null) {
            throw new IllegalArgumentException("발생 일시가 비었다");
        }
        return new Notification(req, occurredAt);
    }

    /** 읽음. 이미 읽었으면 그대로다. */
    public void markRead() {
        this.read = true;
    }

    /** 보관 기한이 지났는가(기본 30일). */
    public boolean isExpired(LocalDateTime now, NotificationRetention retention) {
        return retention.isExpired(occurredAt, now);
    }

    Long id() {
        return id;
    }

    long recipientId() {
        return recipientId;
    }

    NotificationType type() {
        return type;
    }

    String content() {
        return content;
    }

    LocalDateTime occurredAt() {
        return occurredAt;
    }

    boolean isRead() {
        return read;
    }

    /** 이동 대상. 정해 두지 않았으면 종류의 기본 화면. */
    NavigationTarget target() {
        return targetScreen == null
                ? new NavigationTarget(type.defaultScreen(), null)
                : new NavigationTarget(targetScreen, targetId);
    }
}
