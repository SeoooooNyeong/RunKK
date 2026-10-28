package com.a207.runkk.member;

import com.a207.runkk.common.RecordVisibility;
import com.a207.runkk.common.Tier;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 시험용 인메모리 MemberRepository(설계 13.1). 저장할 때 ID를 붙인다. */
public class InMemoryMemberRepository implements MemberRepository {

    private final Map<Long, Member> members = new LinkedHashMap<>();
    private long sequence = 0;

    @Override
    public Optional<Member> findById(long id) {
        return Optional.ofNullable(members.get(id));
    }

    @Override
    public Optional<Member> findByAccount(SocialAccount account) {
        return members.values().stream().filter(m -> account.equals(m.account())).findFirst();
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return members.values().stream()
                .anyMatch(m -> m.nickname() != null && m.nickname().value().equals(nickname));
    }

    @Override
    public List<Member> findWithdrawnBefore(LocalDateTime t) {
        return members.values().stream()
                .filter(m -> m.getStatus() == MemberStatus.WITHDRAWN)
                .filter(m -> m.withdrawnAt().isBefore(t))
                .toList();
    }

    @Override
    public List<Member> findIdleGuests(LocalDateTime before) {
        return members.values().stream()
                .filter(m -> m.getAccountType() == AccountType.GUEST)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .filter(m -> !m.lastAccessAt().isAfter(before))
                .toList();
    }

    @Override
    public Member save(Member member) {
        if (member.getId() == null) {
            assignId(member, ++sequence);
        }
        members.put(member.getId(), member);
        return member;
    }

    @Override
    public Map<Long, MemberSummary> summaries(Collection<Long> memberIds) {
        return memberIds.stream()
                .map(members::get)
                .filter(Objects::nonNull)
                .collect(
                        Collectors.toMap(
                                Member::getId,
                                m ->
                                        new MemberSummary(
                                                m.getId(),
                                                m.displayName(),
                                                m.getTier(),
                                                m.profileVisibility())));
    }

    @Override
    public List<GhostOwner> ghostOwners(Set<Tier> tiers, long excludeMemberId) {
        return members.values().stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .filter(m -> m.getSettings().recordVisibility() == RecordVisibility.PUBLIC)
                .filter(m -> tiers.contains(m.getTier()))
                .filter(m -> m.getId() != excludeMemberId)
                .map(m -> new GhostOwner(m.getId(), m.displayName(), m.getTier()))
                .toList();
    }

    @Override
    public List<Member> findAllById(Collection<Long> memberIds) {
        return memberIds.stream().map(members::get).filter(Objects::nonNull).toList();
    }

    @Override
    public Set<Long> rankedMemberIds() {
        return members.values().stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .filter(m -> m.getSettings().recordVisibility().includedInPersonalRanking())
                .map(Member::getId)
                .collect(Collectors.toSet());
    }

    public int size() {
        return members.size();
    }

    private static void assignId(Member member, long id) {
        try {
            Field field = Member.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(member, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
