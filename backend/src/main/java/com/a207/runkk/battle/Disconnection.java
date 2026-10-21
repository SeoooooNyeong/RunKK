package com.a207.runkk.battle;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;

/** 참가자 연결 끊김 한 번(자료사전 연결끊김기록, 상태도 2.4.3). 복구되면 reconnectedAt이 채워진다. */
@Embeddable
public class Disconnection {

    @Column(name = "disconnected_at", nullable = false)
    private LocalDateTime disconnectedAt;

    @Column(name = "reconnected_at")
    private LocalDateTime reconnectedAt;

    protected Disconnection() {}

    Disconnection(LocalDateTime disconnectedAt) {
        this.disconnectedAt = disconnectedAt;
    }

    boolean isOpen() {
        return reconnectedAt == null;
    }

    void close(LocalDateTime at) {
        this.reconnectedAt = at;
    }

    public LocalDateTime getDisconnectedAt() {
        return disconnectedAt;
    }

    public LocalDateTime getReconnectedAt() {
        return reconnectedAt;
    }
}
