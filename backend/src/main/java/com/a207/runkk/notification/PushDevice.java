package com.a207.runkk.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 회원 기기 하나의 FCM 등록 토큰(설계 6.1.10, D-43). 회원 ID와 기기 토큰의 대응은 개인정보라 notification이 소유하고 탈퇴 때
 * 지운다(FR-021).
 */
@Entity
@Table(name = "push_device")
public class PushDevice {

    static final int TOKEN_MAX = 512;

    @Id
    @Column(name = "token", length = TOKEN_MAX)
    private String token;

    @Column(name = "member_id", nullable = false)
    private long memberId;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    protected PushDevice() {}

    private PushDevice(long memberId, String token, LocalDateTime now) {
        this.token = token;
        this.memberId = memberId;
        this.registeredAt = now;
    }

    public static PushDevice register(long memberId, String token, LocalDateTime now) {
        if (token == null || token.isBlank() || token.length() > TOKEN_MAX) {
            throw new IllegalArgumentException("기기 토큰 형식 오류");
        }
        return new PushDevice(memberId, token, now);
    }

    /** 같은 토큰이 다른 회원으로 다시 오면 주인을 바꾼다(기기를 넘겨받은 경우). */
    public void reassign(long memberId, LocalDateTime now) {
        this.memberId = memberId;
        this.registeredAt = now;
    }

    String token() {
        return token;
    }

    long memberId() {
        return memberId;
    }
}
