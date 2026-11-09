package com.a207.runkk.aicoach;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "coach_daily_usage")
public class CoachDailyUsage {
    @Id private long memberId;

    @Column(nullable = false)
    private LocalDate usageDay;

    @Column(nullable = false)
    private int answeredCount;

    @Column(length = 36)
    private String leaseToken;

    private LocalDateTime leaseUntil;

    protected CoachDailyUsage() {}

    public boolean reservedAt(LocalDateTime now) {
        return leaseUntil != null && leaseUntil.isAfter(now);
    }

    public boolean owns(CoachStore.Lease lease) {
        return lease.token().equals(leaseToken);
    }

    public boolean hasRoom(LocalDate day, int limit) {
        return !usageDay.equals(day) || answeredCount < limit;
    }

    public void reserve(CoachStore.Lease lease, LocalDateTime now) {
        if (!usageDay.equals(now.toLocalDate())) {
            usageDay = now.toLocalDate();
            answeredCount = 0;
        }
        leaseToken = lease.token();
        // SSE 60초와 헤더 대기 5초보다 길어야 정상 중계 도중 임대가 풀리지 않는다.
        leaseUntil = now.plusSeconds(75);
    }

    public void answered() {
        answeredCount++;
        release();
    }

    public void release() {
        leaseToken = null;
        leaseUntil = null;
    }
}
