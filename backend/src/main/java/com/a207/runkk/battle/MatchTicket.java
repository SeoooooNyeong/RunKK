package com.a207.runkk.battle;

import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.policy.MatchCondition;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 매칭 대기자 한 명의 대기 상태(설계 6.1.6, 상태도 2.4.1). 대기열 키는 memberId다. 저장은 MatchQueue 구현(Redis 해시 또는
 * PostgreSQL 행)이 한다.
 */
public final class MatchTicket {

    private final long memberId;
    private final Tier tier;
    private final StandardDistance distance;
    private final LocalDateTime waitingSince;
    private MatchStatus status;

    private MatchTicket(
            long memberId,
            Tier tier,
            StandardDistance distance,
            LocalDateTime waitingSince,
            MatchStatus status) {
        this.memberId = memberId;
        this.tier = Objects.requireNonNull(tier, "tier");
        this.distance = Objects.requireNonNull(distance, "distance");
        this.waitingSince = Objects.requireNonNull(waitingSince, "waitingSince");
        this.status = Objects.requireNonNull(status, "status");
    }

    /** 랭크 티어·랜덤 대결 거리만. 위반이면 IllegalArgumentException. */
    public static MatchTicket open(
            long memberId, Tier tier, StandardDistance d, LocalDateTime now) {
        if (!tier.isRanked()) {
            throw new IllegalArgumentException("랭크 티어만 대기할 수 있다: " + tier);
        }
        if (!d.isRandomBattle()) {
            throw new IllegalArgumentException("랜덤 대결 거리가 아니다: " + d);
        }
        return new MatchTicket(memberId, tier, d, now, MatchStatus.WAITING);
    }

    /** 저장소에서 다시 만든다(infra.battle). */
    public static MatchTicket restore(
            long memberId, Tier tier, StandardDistance d, LocalDateTime since, MatchStatus s) {
        return new MatchTicket(memberId, tier, d, since, s);
    }

    /** now − waitingSince > ghostAfter(기본 15초). */
    public boolean isExpired(LocalDateTime now, Duration ghostAfter) {
        return Duration.between(waitingSince, now).compareTo(ghostAfter) > 0;
    }

    /** 다른 회원, 둘 다 WAITING, 같은 거리, 허용 티어(srs.md 6.3 4.2 후보 판정 표). */
    public boolean canPairWith(MatchTicket other, MatchCondition cond) {
        return other.memberId != memberId
                && status == MatchStatus.WAITING
                && other.status == MatchStatus.WAITING
                && other.distance == distance
                && cond.accepts(tier, other.tier);
    }

    /** WAITING → ASSIGNED. 아니면 IllegalStateException. */
    public void assign() {
        if (status != MatchStatus.WAITING) {
            throw new IllegalStateException("대기 중인 표만 배정한다: " + status);
        }
        status = MatchStatus.ASSIGNED;
    }

    public long getMemberId() {
        return memberId;
    }

    public Tier getTier() {
        return tier;
    }

    public StandardDistance getDistance() {
        return distance;
    }

    public LocalDateTime getWaitingSince() {
        return waitingSince;
    }

    public MatchStatus getStatus() {
        return status;
    }
}
