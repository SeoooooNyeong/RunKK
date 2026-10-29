package com.a207.runkk.battle;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * 친구 대결 초대 한 건(설계 5.6.2 battle.FriendInvite, 상태도 2.4.11, D-218~D-223). 상태는 PENDING에서만 바뀌고, 만료는 조회 시
 * 판정(statusAt)과 정리 작업(expireIfDue) 두 겹이다. 동시 수락은 version 낙관적 잠금으로 막는다.
 */
@Entity
@Table(name = "friend_invite")
public class FriendInvite {

    /** 초대 유효 시간(생성 + 5분, FR-228). 다시 보내기로 늘리지 않는다(D-221). */
    public static final Duration VALID_FOR = Duration.ofMinutes(5);

    /** 토큰 바이트 수. base64url(패딩 없음)로 22자(D-219). */
    private static final int TOKEN_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inviter_member_id", nullable = false, updatable = false)
    private long inviterId;

    @Column(name = "distance_meters", nullable = false, updatable = false)
    private long distanceMeters;

    @Column(name = "token", nullable = false, updatable = false, unique = true, length = 32)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private FriendInviteStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "joiner_member_id")
    private Long joinerId;

    @Column(name = "battle_id")
    private Long battleId;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected FriendInvite() {}

    private FriendInvite(long inviterId, StandardDistance distance, LocalDateTime now) {
        this.inviterId = inviterId;
        this.distanceMeters = distance.distance().meters();
        this.token = newToken();
        this.status = FriendInviteStatus.PENDING;
        // DB 시각 정밀도(TIMESTAMP(6))에 맞춰 마이크로초로 자른다. 저장 뒤 다시 읽은 만료 시각과 비교가 어긋나지 않게 한다
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        this.expiresAt = createdAt.plus(VALID_FOR);
    }

    /** PENDING 초대를 만든다. 랜덤 대결 거리(1·3·5·10km)가 아니면 IllegalArgumentException. */
    public static FriendInvite open(long inviterId, StandardDistance distance, LocalDateTime now) {
        if (distance == null || !distance.isRandomBattle()) {
            throw new IllegalArgumentException("친구 대결 거리는 1·3·5·10km다: " + distance);
        }
        return new FriendInvite(inviterId, distance, now);
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** now 시각의 상태(D-221): PENDING이어도 now ≥ expiresAt이면 EXPIRED(정각은 만료). 저장된 상태는 바꾸지 않는다. */
    public FriendInviteStatus statusAt(LocalDateTime now) {
        if (status == FriendInviteStatus.PENDING && !now.isBefore(expiresAt)) {
            return FriendInviteStatus.EXPIRED;
        }
        return status;
    }

    /** 참가·거절 거부 사유: 본인 초대면 INVITE_OWN(만료보다 먼저), 아니면 statusAt(now)의 거부 사유. */
    public Optional<ReasonCode> refusalFor(long memberId, LocalDateTime now) {
        if (memberId == inviterId) {
            return Optional.of(ReasonCode.INVITE_OWN);
        }
        return statusAt(now).refusal();
    }

    /** 참가. 거부 사유가 있으면 BusinessRuleException(그 코드). ACCEPTED. */
    public void accept(long joinerId, LocalDateTime now) {
        refuseIfBlocked(joinerId, now);
        this.status = FriendInviteStatus.ACCEPTED;
        this.joinerId = joinerId;
        this.closedAt = now;
    }

    /** 참가로 만든 대결을 잇는다. ACCEPTED만, 아니면 IllegalStateException. */
    public void linkBattle(long battleId) {
        if (status != FriendInviteStatus.ACCEPTED) {
            throw new IllegalStateException("참가된 초대만 대결을 잇는다: " + status);
        }
        this.battleId = battleId;
    }

    /** 거절. 거부 사유가 있으면 BusinessRuleException. DECLINED. */
    public void decline(long memberId, LocalDateTime now) {
        refuseIfBlocked(memberId, now);
        this.status = FriendInviteStatus.DECLINED;
        this.joinerId = memberId;
        this.closedAt = now;
    }

    /**
     * 취소. PENDING(만료 시각이 지났어도)이면 CANCELLED이고 true. 이미 끝났으면(DECLINED·CANCELLED·EXPIRED) 그대로 두고
     * false(멱등, D-223). ACCEPTED면 BusinessRuleException(INVITE_ALREADY_JOINED).
     */
    public boolean cancel(LocalDateTime now) {
        if (status == FriendInviteStatus.ACCEPTED) {
            throw new BusinessRuleException(ReasonCode.INVITE_ALREADY_JOINED);
        }
        if (status != FriendInviteStatus.PENDING) {
            return false;
        }
        this.status = FriendInviteStatus.CANCELLED;
        this.closedAt = now;
        return true;
    }

    /** 정리 작업의 만료(D-221). PENDING이고 now ≥ expiresAt이면 EXPIRED로 바꾸고 true. */
    public boolean expireIfDue(LocalDateTime now) {
        if (status != FriendInviteStatus.PENDING || now.isBefore(expiresAt)) {
            return false;
        }
        this.status = FriendInviteStatus.EXPIRED;
        this.closedAt = now;
        return true;
    }

    /** 열린 대기 초대인가(PENDING이고 기한 전). */
    public boolean isOpen(LocalDateTime now) {
        return statusAt(now) == FriendInviteStatus.PENDING;
    }

    private void refuseIfBlocked(long memberId, LocalDateTime now) {
        Optional<ReasonCode> refusal = refusalFor(memberId, now);
        if (refusal.isPresent()) {
            throw new BusinessRuleException(refusal.get());
        }
    }

    public Long getId() {
        return id;
    }

    public long getInviterId() {
        return inviterId;
    }

    public StandardDistance getDistance() {
        return Arrays.stream(StandardDistance.values())
                .filter(d -> d.distance().meters() == distanceMeters)
                .findFirst()
                .orElseThrow();
    }

    public String getToken() {
        return token;
    }

    /** 저장된 상태(만료 판정 전). 화면·판정은 statusAt(now)을 쓴다. */
    public FriendInviteStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public Optional<Long> getJoinerId() {
        return Optional.ofNullable(joinerId);
    }

    public Optional<Long> getBattleId() {
        return Optional.ofNullable(battleId);
    }

    public Optional<LocalDateTime> getClosedAt() {
        return Optional.ofNullable(closedAt);
    }

    public long getVersion() {
        return version;
    }
}
