package com.a207.runkk.member;

import com.a207.runkk.common.Tier;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 회원 저장 포트(설계 6.1.2). 구현은 infra.member.JpaMemberRepository이고 member 밖에서 쓰지 않는다(13.1 규칙 4).
 * findAllById와 rankedMemberIds는 MemberQueryService의 묶음 조회(disclosureOf, rankedMemberIds)를 한 번의 쿼리로
 * 하려고 더했다(B2 결정).
 */
public interface MemberRepository {

    Optional<Member> findById(long id);

    Optional<Member> findByAccount(SocialAccount account);

    boolean existsByNickname(String nickname);

    /** 탈퇴 일시가 t 이전인 회원. */
    List<Member> findWithdrawnBefore(LocalDateTime t);

    Member save(Member member);

    /** 표시명·티어·공개 범위·태그를 한 번의 조회로(N+1 방지, 6.5). 없는 ID는 맵에 없다. */
    Map<Long, MemberSummary> summaries(Collection<Long> memberIds);

    /** 상태 ACTIVE, 기록 전체공개, tier ∈ tiers인 회원(요청자 제외). */
    List<GhostOwner> ghostOwners(Set<Tier> tiers, long excludeMemberId);

    /** 여러 회원을 한 번에 읽는다. 없는 ID는 빠진다. */
    List<Member> findAllById(Collection<Long> memberIds);

    /** 상태 ACTIVE이고 기록 공개 범위가 개인 랭킹에 들어가는 회원(FR-070). */
    Set<Long> rankedMemberIds();
}
