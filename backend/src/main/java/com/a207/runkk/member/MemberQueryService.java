package com.a207.runkk.member;

import com.a207.runkk.common.Tier;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 패키지가 읽을 회원 정보 투영을 내준다(설계 6.1.2, D-32). 다른 패키지는 Member 엔티티나 MemberRepository를 받지 않고 이 서비스만 부른다.
 */
@Service
@Transactional(readOnly = true)
public class MemberQueryService {

    /** AI 파기 누락 보정용 최소 투영. 탈퇴한 회원의 ID와 탈퇴 시각만 내준다. */
    public Map<Long, LocalDateTime> withdrawalsBefore(LocalDateTime cutoff) {
        Map<Long, LocalDateTime> result = new LinkedHashMap<>();
        for (Member member : memberRepository.findWithdrawnBefore(cutoff)) {
            result.put(member.getId(), member.withdrawnAt());
        }
        return result;
    }

    /** 나이대 단위(10살). */
    private static final int AGE_BAND = 10;

    private final MemberRepository memberRepository;

    public MemberQueryService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /** 표시명·티어·공개 범위. 한 번의 조회(N+1 방지). 탈퇴 회원은 DisplayName.WITHDRAWN. */
    public Map<Long, MemberSummary> summaries(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.summaries(memberIds);
    }

    /**
     * 다른 러너에게 보이는 표시 성별(ShownGender, D-327·D-336)을 한 번의 조회로 읽는다. 넘긴 ID마다 값이 있다: 성별 공개인 남·여는 그 값, 그
     * 밖(비공개·미입력·밝히지 않음·없는 회원)은 ShownGender.fallbackOf(ID). battle의 대결 상대 성별, analysis 랭킹 행, profile
     * 다른 러너 프로필이 같은 규칙을 쓴다.
     */
    public Map<Long, Gender> shownGendersOf(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Gender> result = new HashMap<>();
        for (Member m : memberRepository.findAllById(memberIds)) {
            result.put(m.getId(), ShownGender.of(m.getId(), m.gender(), m.genderVisibility()));
        }
        for (Long id : memberIds) {
            result.computeIfAbsent(id, ShownGender::fallbackOf);
        }
        return result;
    }

    /**
     * 본인에게 보이는 내 성별(입력한 남·여). 성별 공개 값과 무관하다. 입력하지 않았거나 밝히지 않음, 없는 회원이면 비어 있다. 다른 러너에게 보일 값은
     * shownGendersOf를 쓴다(S15P31A207-306: 나와의 싸움의 그날의 나는 본인만 본다).
     */
    public Optional<Gender> ownGenderOf(long memberId) {
        return memberRepository
                .findById(memberId)
                .map(Member::gender)
                .filter(g -> g == Gender.MALE || g == Gender.FEMALE);
    }

    /** analysis·profile이 엔티티 대신 읽는 설정 투영. */
    public MemberPreference preferenceOf(long memberId) {
        return MemberLoader.load(memberRepository, memberId).preference();
    }

    /** 프로필 화면의 레벨·사진·소개·나이대·성별(profile, FR-005). year 기준 나이대. */
    public MemberProfile profileOf(long memberId, int year) {
        return profileOf(MemberLoader.load(memberRepository, memberId), year);
    }

    /**
     * profileOf를 여러 명에게 한 번의 조회로(크루원 목록·가입 신청 목록의 레벨·나이대·성별, 피그마 맞춤 W7). 공개 값으로 거르지 않고 그대로 내주며 거르기는
     * 부르는 쪽이 한다. 없는 회원은 맵에 없다.
     */
    public Map<Long, MemberProfile> profilesOf(Collection<Long> memberIds, int year) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, MemberProfile> result = new HashMap<>();
        for (Member m : memberRepository.findAllById(memberIds)) {
            result.put(m.getId(), profileOf(m, year));
        }
        return result;
    }

    private static MemberProfile profileOf(Member m, int year) {
        OptionalInt age = m.ageIn(year);
        String ageBand = age.isPresent() ? (age.getAsInt() / AGE_BAND * AGE_BAND) + "대" : null;
        return new MemberProfile(
                m.getLevel(),
                m.intro(),
                ageBand,
                m.gender(),
                m.ageBandVisibility(),
                m.genderVisibility());
    }

    /** year 기준 나이. 출생연도가 없으면 비어 있음(analysis 심박 구간, design-review.md M4). */
    public OptionalInt ageOf(long memberId, int year) {
        return memberRepository
                .findById(memberId)
                .map(m -> m.ageIn(year))
                .orElse(OptionalInt.empty());
    }

    /** 출생연도. 없거나 회원이 없으면 비어 있음(runbody 분석 요청의 최대 심박 추정, D-444). */
    public OptionalInt birthYearOf(long memberId) {
        return memberRepository
                .findById(memberId)
                .map(Member::birthYear)
                .map(OptionalInt::of)
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

    public boolean hasCurrentCoachConsent(long memberId) {
        return memberRepository
                .findById(memberId)
                .map(Member::hasCurrentCoachConsent)
                .orElse(false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockCurrentCoachConsent(long memberId) {
        return memberRepository
                .findByIdForUpdate(memberId)
                .map(Member::hasCurrentCoachConsent)
                .orElse(false);
    }

    /** ACTIVE 회원의 닉네임을 한 번의 조회로. 탈퇴했거나 없는 회원은 맵에 없다(event 이벤트 응모자 명단, S15P31A207-275). */
    @Transactional(readOnly = true)
    public Map<Long, String> activeNicknames(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.activeNicknames(memberIds);
    }

    /** 회원이 있고 ACTIVE인가. web.auth.AuthFilter가 탈퇴 회원의 남은 접근 토큰을 거부할 때 쓴다(코드 리뷰 2차 L21). */
    @Transactional(readOnly = true)
    public boolean isActive(long memberId) {
        return memberRepository
                .findById(memberId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .isPresent();
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
            result.put(m.getId(), DisclosureLevel.of(viewerId, m.getId(), m.getStatus()));
        }
        return result;
    }

    /**
     * 티어에 반영한 유효 러닝 순번(TierProgress.validRunCount). running.TierReconcileService가 running의 유효 기록 수와
     * 비교해 빠진 순번을 찾는다(설계 6.1.2, 12.2 D3, D-108). 없는 회원은 0.
     */
    @Transactional(readOnly = true)
    public int appliedValidRunCount(long memberId) {
        return memberRepository.findById(memberId).map(Member::appliedValidRunCount).orElse(0);
    }
}
