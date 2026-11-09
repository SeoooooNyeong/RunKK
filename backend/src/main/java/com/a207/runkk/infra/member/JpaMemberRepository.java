package com.a207.runkk.infra.member;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.RecordVisibility;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.AccountType;
import com.a207.runkk.member.GhostOwner;
import com.a207.runkk.member.Member;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.MemberStatus;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.member.ProfileVisibility;
import com.a207.runkk.member.SocialAccount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * member.MemberRepository의 JPA 구현(설계 5.1.1 infra.member). member·member_consent 테이블만 읽고 쓴다(D-32).
 * 묶음 조회는 필요한 열만 고르는 한 번의 쿼리로 한다(N+1 방지, 6.5).
 */
@Repository
public class JpaMemberRepository implements MemberRepository {

    private final EntityManager entityManager;

    public JpaMemberRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Member> findById(long id) {
        return Optional.ofNullable(entityManager.find(Member.class, id));
    }

    @Override
    public Optional<Member> findByIdForUpdate(long id) {
        return Optional.ofNullable(
                entityManager.find(Member.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    @Override
    public Optional<Member> findByAccount(SocialAccount account) {
        return entityManager
                .createQuery(
                        "select m from Member m where m.account.provider = :provider"
                                + " and m.account.providerAccountId = :accountId",
                        Member.class)
                .setParameter("provider", account.provider())
                .setParameter("accountId", account.providerAccountId())
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return entityManager
                        .createQuery(
                                "select count(m) from Member m where m.nickname.value = :nickname",
                                Long.class)
                        .setParameter("nickname", nickname)
                        .getSingleResult()
                > 0;
    }

    @Override
    public List<Member> findWithdrawnBefore(LocalDateTime t) {
        return entityManager
                .createQuery(
                        "select m from Member m where m.status = :status and m.withdrawnAt < :t",
                        Member.class)
                .setParameter("status", MemberStatus.WITHDRAWN)
                .setParameter("t", t)
                .getResultList();
    }

    @Override
    public List<Member> findIdleGuests(LocalDateTime before) {
        return entityManager
                .createQuery(
                        "select m from Member m where m.accountType = :guest and m.status = :status"
                                + " and m.lastAccessAt <= :before order by m.id",
                        Member.class)
                .setParameter("guest", AccountType.GUEST)
                .setParameter("status", MemberStatus.ACTIVE)
                .setParameter("before", before)
                .getResultList();
    }

    @Override
    public Member save(Member member) {
        if (member.getId() == null) {
            entityManager.persist(member);
            return member;
        }
        return entityManager.merge(member);
    }

    @Override
    public void refresh(Member member) {
        entityManager.refresh(member);
    }

    @Override
    public Map<Long, MemberSummary> summaries(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows =
                entityManager
                        .createQuery(
                                "select m.id, m.nickname.value, m.status, m.tier,"
                                        + " m.settings.profileVisibility"
                                        + " from Member m where m.id in :ids",
                                Object[].class)
                        .setParameter("ids", Set.copyOf(memberIds))
                        .getResultList();
        Map<Long, MemberSummary> result = new HashMap<>();
        for (Object[] row : rows) {
            long id = (Long) row[0];
            MemberStatus status = (MemberStatus) row[2];
            String nickname = status == MemberStatus.WITHDRAWN ? null : (String) row[1];
            result.put(
                    id,
                    new MemberSummary(
                            id,
                            DisplayName.ofNullable(nickname),
                            (Tier) row[3],
                            (ProfileVisibility) row[4]));
        }
        return result;
    }

    @Override
    public Map<Long, String> activeNicknames(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        entityManager
                .createQuery(
                        "select m.id, m.nickname.value from Member m"
                                + " where m.id in :ids and m.status = :active",
                        Object[].class)
                .setParameter("ids", Set.copyOf(memberIds))
                .setParameter("active", MemberStatus.ACTIVE)
                .getResultList()
                .forEach(row -> result.put((Long) row[0], (String) row[1]));
        return result;
    }

    @Override
    public List<GhostOwner> ghostOwners(Set<Tier> tiers, long excludeMemberId) {
        if (tiers.isEmpty()) {
            return List.of();
        }
        return entityManager
                .createQuery(
                        "select m.id, m.nickname.value, m.tier from Member m where m.status ="
                            + " :status and m.settings.recordVisibility = :visibility and m.tier in"
                            + " :tiers and m.id <> :exclude",
                        Object[].class)
                .setParameter("status", MemberStatus.ACTIVE)
                .setParameter("visibility", RecordVisibility.PUBLIC)
                .setParameter("tiers", tiers)
                .setParameter("exclude", excludeMemberId)
                .getResultList()
                .stream()
                .map(
                        row ->
                                new GhostOwner(
                                        (Long) row[0],
                                        DisplayName.ofNullable((String) row[1]),
                                        (Tier) row[2]))
                .toList();
    }

    @Override
    public List<Member> findAllById(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return entityManager
                .createQuery("select m from Member m where m.id in :ids", Member.class)
                .setParameter("ids", Set.copyOf(memberIds))
                .getResultList();
    }

    @Override
    public Set<Long> rankedMemberIds() {
        Set<RecordVisibility> ranked =
                Arrays.stream(RecordVisibility.values())
                        .filter(RecordVisibility::includedInPersonalRanking)
                        .collect(Collectors.toSet());
        return new HashSet<>(
                entityManager
                        .createQuery(
                                "select m.id from Member m where m.status = :status"
                                        + " and m.settings.recordVisibility in :visibilities",
                                Long.class)
                        .setParameter("status", MemberStatus.ACTIVE)
                        .setParameter("visibilities", ranked)
                        .getResultList());
    }
}
