package com.a207.runkk.crew;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 러너 한 명의 크루 소속 한 건(설계 6.1.7, 상태도 2.4.13). 퇴장한 행은 쿨타임 판정을 위해 남는다. 소속 중 행은 회원마다 하나다(5.3 부분 고유 인덱스).
 */
@Entity
@Table(name = "crew_membership")
public class CrewMembership {

    /** 탈퇴·강제 퇴장 뒤 가입 금지 시간(FR-124). */
    static final Duration COOLDOWN = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crew_id", nullable = false)
    private Crew crew;

    @Column(name = "member_id", nullable = false)
    private long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private CrewRole role;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_reason", length = 10)
    private LeaveReason leaveReason;

    @Column(name = "crew_intro", length = 40)
    private String crewIntro;

    protected CrewMembership() {}

    CrewMembership(Crew crew, long memberId, CrewRole role, LocalDateTime now) {
        this.crew = crew;
        this.memberId = memberId;
        this.role = role;
        this.joinedAt = now;
    }

    /** leftAt == null. */
    public boolean isActive() {
        return leftAt == null;
    }

    /** 이미 퇴장이면 IllegalStateException. */
    public void leave(LeaveReason reason, LocalDateTime now) {
        if (!isActive()) {
            throw new IllegalStateException("이미 퇴장한 소속");
        }
        this.leftAt = now;
        this.leaveReason = reason;
    }

    /** 크루장 양도의 새 크루장: LEADER. */
    public void promote() {
        role = CrewRole.LEADER;
    }

    /** 이전 크루장·운영진 해제: MEMBER. */
    public void demote() {
        role = CrewRole.MEMBER;
    }

    /** MEMBER → MANAGER(Crew.appointManager만 부른다, D-98). MEMBER가 아니면 IllegalStateException. */
    void appointManager() {
        if (role != CrewRole.MEMBER) {
            throw new IllegalStateException("크루원만 운영진으로 지정한다: " + role);
        }
        role = CrewRole.MANAGER;
    }

    /** 크루 내 표시 정보(FR-144). 40자 초과 INTRO_TOO_LONG. 러너 성향 태그는 지웠다(D-91). */
    public void editCrewProfile(String intro) {
        CrewProfile.requireIntro(intro);
        this.crewIntro = intro;
    }

    /** WITHDREW·KICKED면 leftAt + 24시간. */
    public Optional<LocalDateTime> cooldownUntil() {
        return leaveReason == null ? Optional.empty() : Optional.of(leftAt.plus(COOLDOWN));
    }

    /** 소속 기간에 at이 들어가는가(기록 시작일시에 소속 중이던 크루, FR-139). */
    public boolean coversAt(LocalDateTime at) {
        return !at.isBefore(joinedAt) && (leftAt == null || at.isBefore(leftAt));
    }

    LocalDateTime leftAtOrJoinedAt() {
        return leftAt != null ? leftAt : joinedAt;
    }

    public long crewId() {
        return crew.getId();
    }

    public long memberId() {
        return memberId;
    }

    public CrewRole role() {
        return role;
    }

    public LocalDateTime joinedAt() {
        return joinedAt;
    }

    public Optional<LocalDateTime> leftAt() {
        return Optional.ofNullable(leftAt);
    }

    public String crewIntro() {
        return crewIntro;
    }
}
