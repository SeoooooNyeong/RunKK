package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 크루 한 곳의 구성원 규칙(설계 6.1.7). 소속 중 멤버십은 1~10개이고 크루장은 정확히 1명이다. 정원 검사는 서비스가 크루 행을 잠근 뒤 admit이 한다(5.3).
 * 퇴장한 멤버십 행도 쿨타임·주간 거리 재합산을 위해 함께 가진다.
 */
@Entity
@Table(name = "crew")
public class Crew {

    /** 정원(크루장 포함, FR-111). */
    public static final int CAPACITY = 10;

    /** 운영진 상한(SRS 제안 값, FR-204, D-98, D-113). */
    public static final int MAX_MANAGERS = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 20)
    private String name;

    @Column(name = "intro", length = 40)
    private String intro;

    @Column(name = "tags", nullable = false, length = 200)
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 10)
    private CrewVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_policy", nullable = false, length = 10)
    private JoinPolicy joinPolicy;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Embedded private InviteCode inviteCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "crew", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CrewMembership> memberships = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Crew() {}

    private Crew(CrewProfile profile, InviteCode code, LocalDateTime now) {
        this.name = profile.name().value();
        this.intro = profile.intro();
        this.tags = CrewTags.encode(profile.tags());
        this.visibility = profile.visibility();
        this.joinPolicy = profile.joinPolicy();
        this.logoUrl = profile.logoUrl();
        this.inviteCode = code;
        this.createdAt = now;
    }

    /** 자격 판정은 서비스가 먼저 한다(JoinEligibility). 생성자가 LEADER 멤버십. */
    public static Crew create(
            long founderId, CrewProfile profile, InviteCode code, LocalDateTime now) {
        Crew crew = new Crew(profile, code, now);
        crew.memberships.add(new CrewMembership(crew, founderId, CrewRole.LEADER, now));
        return crew;
    }

    /** 정보·태그·공개·가입 방식 변경. 대기 중 가입 신청은 그대로다(Q-36). */
    public void changeInfo(CrewInfoChange change) {
        if (change.name() != null) {
            name = change.name().value();
        }
        if (change.intro() != null) {
            intro = change.intro();
        }
        if (change.tags() != null) {
            tags = CrewTags.encode(change.tags());
        }
        if (change.visibility() != null) {
            visibility = change.visibility();
        }
        if (change.joinPolicy() != null) {
            joinPolicy = change.joinPolicy();
        }
        if (change.logoUrl() != null) {
            logoUrl = change.logoUrl();
        }
    }

    /** 이전 코드는 바로 무효(FR-114). */
    public void reissueInviteCode(InviteCode newCode) {
        inviteCode = newCode;
    }

    /** 정원 10명이면 CREW_FULL. 신청자 미소속은 서비스가 먼저 확인한다. */
    public CrewMembership admit(long memberId, LocalDateTime now) {
        if (isFull()) {
            throw new BusinessRuleException(ReasonCode.CREW_FULL);
        }
        CrewMembership membership = new CrewMembership(this, memberId, CrewRole.MEMBER, now);
        memberships.add(membership);
        return membership;
    }

    /**
     * 탈퇴(WITHDREW)·강제 퇴장(KICKED). 크루장 탈퇴는 크루원이 있으면 LEADER_MUST_TRANSFER, 혼자면
     * CREW_DELETE_REQUIRED(5.3.3). 강제 퇴장 대상이 크루장이거나 소속 크루원이 아니면 TARGET_INVALID, 탈퇴자가 소속이 아니면
     * NOT_CREW_MEMBER.
     */
    public void release(long memberId, LeaveReason reason, LocalDateTime now) {
        Optional<CrewMembership> found = membershipOf(memberId);
        if (found.isEmpty()) {
            throw new BusinessRuleException(
                    reason == LeaveReason.KICKED
                            ? ReasonCode.TARGET_INVALID
                            : ReasonCode.NOT_CREW_MEMBER);
        }
        CrewMembership membership = found.get();
        if (membership.role() == CrewRole.LEADER) {
            throw new BusinessRuleException(leaderLeaveReason(reason));
        }
        membership.leave(reason, now);
    }

    private ReasonCode leaderLeaveReason(LeaveReason reason) {
        if (reason == LeaveReason.KICKED) {
            return ReasonCode.TARGET_INVALID;
        }
        return headcount() > 1 ? ReasonCode.LEADER_MUST_TRANSFER : ReasonCode.CREW_DELETE_REQUIRED;
    }

    /**
     * 두 역할을 함께 바꾼다. to가 소속 구성원(크루원·운영진)이 아니거나 from과 같으면 TARGET_INVALID. 운영진이 넘겨받으면 그 운영진 자리는 빈다.
     */
    public void transferLeadership(long fromId, long toId) {
        CrewMembership from =
                membershipOf(fromId)
                        .filter(m -> m.role() == CrewRole.LEADER)
                        .orElseThrow(() -> new IllegalStateException("크루장이 아니다: " + fromId));
        CrewMembership to =
                membershipOf(toId)
                        .filter(m -> m.role() != CrewRole.LEADER)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.TARGET_INVALID));
        from.demote();
        to.promote();
    }

    /**
     * 운영진 지정(FR-204). crew 행 잠금 안(CrewAccess.checkForUpdate)에서 부르고 권한은 서비스가 먼저 본다. 대상이 소속 MEMBER가
     * 아니면(소속 아님·크루장 본인·이미 운영진) TARGET_INVALID, 활성 운영진이 이미 MAX_MANAGERS명이면 MANAGER_LIMIT.
     */
    public void appointManager(long targetId) {
        CrewMembership target =
                membershipOf(targetId)
                        .filter(m -> m.role() == CrewRole.MEMBER)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.TARGET_INVALID));
        if (managerCount() >= MAX_MANAGERS) {
            throw new BusinessRuleException(ReasonCode.MANAGER_LIMIT);
        }
        target.appointManager();
    }

    /** 운영진 해제(FR-204). 대상이 소속 MANAGER가 아니면 TARGET_INVALID. 대상은 MEMBER가 된다. */
    public void dismissManager(long targetId) {
        membershipOf(targetId)
                .filter(m -> m.role() == CrewRole.MANAGER)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.TARGET_INVALID))
                .demote();
    }

    /** 활성 운영진 수. */
    public int managerCount() {
        return (int) activeMembers().stream().filter(m -> m.role() == CrewRole.MANAGER).count();
    }

    public CrewRelation relationOf(long memberId) {
        return membershipOf(memberId).map(m -> relationOf(m.role())).orElse(CrewRelation.OUTSIDER);
    }

    private static CrewRelation relationOf(CrewRole role) {
        return switch (role) {
            case LEADER -> CrewRelation.LEADER;
            case MANAGER -> CrewRelation.MANAGER;
            case MEMBER -> CrewRelation.MEMBER;
        };
    }

    /**
     * srs.md 6.3 5.3.1 "가입 방식 판정": 크루 ID로 비공개 크루를 찾거나 코드가 틀리거나 만료면 INVALID_CODE, 정원이면 FULL, 초대 코드
     * 또는 자유 가입이면 IMMEDIATE, 승인제면 REQUEST.
     */
    public Admission admissionFor(JoinTarget target, LocalDateTime now) {
        boolean byCode = target.code().isPresent();
        if (byCode
                ? !acceptsInviteCode(target.inviteCode(), now)
                : visibility != CrewVisibility.PUBLIC) {
            return Admission.INVALID_CODE;
        }
        if (isFull()) {
            return Admission.FULL;
        }
        return byCode || joinPolicy == JoinPolicy.FREE ? Admission.IMMEDIATE : Admission.REQUEST;
    }

    public boolean isFull() {
        return headcount() >= CAPACITY;
    }

    public int headcount() {
        return activeMembers().size();
    }

    public long leaderId() {
        return activeMembers().stream()
                .filter(m -> m.role() == CrewRole.LEADER)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("크루장이 없는 크루: " + id))
                .memberId();
    }

    /** inviteCode.matches && isValidAt. */
    public boolean acceptsInviteCode(String code, LocalDateTime now) {
        return inviteCode.matches(code) && inviteCode.isValidAt(now);
    }

    /** 소속 중 멤버십. */
    public List<CrewMembership> activeMembers() {
        return memberships.stream().filter(CrewMembership::isActive).toList();
    }

    public List<Long> activeMemberIds() {
        return activeMembers().stream().map(CrewMembership::memberId).toList();
    }

    /** 소속 중 멤버십 하나. */
    public Optional<CrewMembership> membershipOf(long memberId) {
        return memberships.stream()
                .filter(m -> m.memberId() == memberId && m.isActive())
                .findFirst();
    }

    /** 그 회원의 이 크루 멤버십 이력(퇴장 행 포함). */
    public List<CrewMembership> membershipsOf(long memberId) {
        return memberships.stream().filter(m -> m.memberId() == memberId).toList();
    }

    /** 퇴장 행을 포함한 모든 멤버십(주간 거리 재합산의 소속 기간). */
    public List<CrewMembership> memberships() {
        return Collections.unmodifiableList(memberships);
    }

    /** 회원 탈퇴: 그 회원의 멤버십 행을 지운다(FR-021). */
    void removeMember(long memberId) {
        memberships.removeIf(m -> m.memberId() == memberId);
    }

    public Long getId() {
        return id;
    }

    public CrewName name() {
        return new CrewName(name);
    }

    public String intro() {
        return intro;
    }

    public TagSet tags() {
        return CrewTags.decode(tags, TagOwner.CREW);
    }

    public CrewVisibility visibility() {
        return visibility;
    }

    public JoinPolicy joinPolicy() {
        return joinPolicy;
    }

    public String logoUrl() {
        return logoUrl;
    }

    public InviteCode inviteCode() {
        return inviteCode;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}
