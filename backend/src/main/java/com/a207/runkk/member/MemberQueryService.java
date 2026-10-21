package com.a207.runkk.member;

import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;
import com.a207.runkk.common.Tier;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 패키지가 읽을 회원 정보 투영을 내준다(설계 6.1.2, D-32). 다른 패키지는 Member 엔티티나 MemberRepository를 받지 않고 이 서비스만 부른다.
 */
@Service
@Transactional(readOnly = true)
public class MemberQueryService {

    /** 나이대 단위(10살). */
    private static final int AGE_BAND = 10;

    private final MemberRepository memberRepository;

    public MemberQueryService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /** 표시명·티어·공개 범위·태그. 한 번의 조회(N+1 방지). 탈퇴 회원은 DisplayName.WITHDRAWN. */
    public Map<Long, MemberSummary> summaries(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.summaries(memberIds);
    }

    /** analysis·profile이 엔티티 대신 읽는 설정 투영. */
    public MemberPreference preferenceOf(long memberId) {
        return MemberLoader.load(memberRepository, memberId).preference();
    }

    /** 프로필 화면의 레벨·사진·소개·나이대·성별(profile, FR-005). year 기준 나이대. */
    public MemberProfile profileOf(long memberId, int year) {
        Member m = MemberLoader.load(memberRepository, memberId);
        OptionalInt age = m.ageIn(year);
        String ageBand = age.isPresent() ? (age.getAsInt() / AGE_BAND * AGE_BAND) + "대" : null;
        return new MemberProfile(m.getLevel(), m.photoUrl(), m.intro(), ageBand, m.gender());
    }

    /** 성향 태그. 탈퇴했거나 태그가 없으면 TagSet.empty(RUNNER)(runimal, design-review.md R3). */
    public TagSet tagsOf(long memberId) {
        return memberRepository
                .findById(memberId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .map(Member::getTags)
                .orElseGet(() -> TagSet.empty(TagOwner.RUNNER));
    }

    /** year 기준 나이. 출생연도가 없으면 비어 있음(analysis 심박 구간, design-review.md M4). */
    public OptionalInt ageOf(long memberId, int year) {
        return memberRepository
                .findById(memberId)
                .map(m -> m.ageIn(year))
                .orElse(OptionalInt.empty());
    }

    /** 회원 닉네임이 이미 있는가(battle.VirtualRecordService의 nicknameTaken, H-11'). */
    public boolean isNicknameTaken(String nickname) {
        return memberRepository.existsByNickname(nickname);
    }

    /** 동의 여부만 묻는다(battle 위치, avatar 얼굴 변환, analysis LLM 전송). 없는 회원은 false. */
    public boolean hasConsent(long memberId, ConsentItem item) {
        return memberRepository.findById(memberId).map(m -> m.hasConsent(item)).orElse(false);
    }

    /**
     * WITHDRAWN(탈퇴 또는 없는 회원) / MUTED(그 종류 푸시를 껐음) / ALLOWED(SQ-14). 알림 종류는
     * notification.NotificationType의 이름이다(B2 결정).
     */
    public PushDecision pushDecision(long memberId, String notificationType) {
        return memberRepository
                .findById(memberId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .map(
                        m ->
                                m.allowsPush(notificationType)
                                        ? PushDecision.ALLOWED
                                        : PushDecision.MUTED)
                .orElse(PushDecision.WITHDRAWN);
    }

    /** 상태 ACTIVE, 기록 전체공개, tier ∈ tiers인 회원(요청자 제외). battle.GhostMatcher가 쓴다(A-07). */
    public Map<Long, GhostOwner> ghostEligibleOwners(Set<Tier> tiers, long excludeMemberId) {
        if (tiers.isEmpty()) {
            return Map.of();
        }
        List<GhostOwner> owners = memberRepository.ghostOwners(tiers, excludeMemberId);
        Map<Long, GhostOwner> result = new LinkedHashMap<>();
        owners.forEach(o -> result.put(o.memberId(), o));
        return result;
    }

    /** 개인 랭킹 대상(FR-070). */
    public Set<Long> rankedMemberIds() {
        return memberRepository.rankedMemberIds();
    }

    /**
     * 열람자에게 대상 회원의 프로필을 어디까지 보이는가. DisclosureLevel.of로 정하고 한 번의 조회로 읽는다(D-52, H-18). 없는 대상 ID는 맵에
     * 없다.
     */
    public Map<Long, DisclosureLevel> disclosureOf(long viewerId, Collection<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, DisclosureLevel> result = new HashMap<>();
        for (Member m : memberRepository.findAllById(targetIds)) {
            result.put(
                    m.getId(),
                    DisclosureLevel.of(viewerId, m.getId(), m.getStatus(), m.profileVisibility()));
        }
        return result;
    }
}
