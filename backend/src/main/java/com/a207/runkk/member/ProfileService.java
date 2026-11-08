package com.a207.runkk.member;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DomainEventPublisher;
import com.a207.runkk.common.ReasonCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 프로필 편집 유스케이스를 조정한다(설계 6.1.2, FR-007, srs.md 6.3 1.3). 닉네임 변경 횟수 제한은 없다. */
@Service
public class ProfileService {

    private final MemberRepository memberRepository;
    private final List<ReservedNicknameChecker> reservedNicknameCheckers;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public ProfileService(
            MemberRepository memberRepository,
            List<ReservedNicknameChecker> reservedNicknameCheckers,
            DomainEventPublisher eventPublisher,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.reservedNicknameCheckers = List.copyOf(reservedNicknameCheckers);
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 닉네임 중복(회원·가상) 검사 → Member.editProfile(출생연도 만 14세 판정의 기준 연도는 오늘). 겹치면
     * BusinessRuleException(NICKNAME_TAKEN). 태그 항목은 없다(D-91). 출생연도를 넣었으면 BirthYearSet을 낸다(런바디가 출생연도
     * 없어 멈춘 분석을 다시 낸다, MR !91 e2e 1).
     */
    @Transactional
    public ProfileEditResult editProfile(long memberId, ProfileEdit edit) {
        Member member = MemberLoader.load(memberRepository, memberId);
        Nickname newNickname = null;
        if (edit.nickname() != null) {
            Nickname requested = Nickname.of(edit.nickname());
            if (!requested.equals(member.nickname())) {
                if (memberRepository.existsByNickname(requested.value())
                        || reservedNicknameCheckers.stream()
                                .anyMatch(c -> c.isReserved(requested.value()))) {
                    throw new BusinessRuleException(ReasonCode.NICKNAME_TAKEN);
                }
                newNickname = requested;
            }
        }
        member.editProfile(edit, newNickname, LocalDate.now(clock).getYear());
        memberRepository.save(member);
        if (edit.birthYear() != null) {
            eventPublisher.publish(new BirthYearSet(member.getId(), LocalDateTime.now(clock)));
        }
        return new ProfileEditResult(
                member.getId(),
                member.nickname().value(),
                member.birthYear(),
                member.gender(),
                null, // 프로필 사진은 없앴다(D-242). 응답 형식은 옛 앱을 위해 남긴다
                member.intro());
    }

    /** 첫 사용 가이드를 봤다고 표시한다(FR-231, v0.14 D-244). 여러 번 불러도 같다. */
    @Transactional
    public void markGuideSeen(long memberId) {
        Member member = MemberLoader.load(memberRepository, memberId);
        member.markGuideSeen();
        memberRepository.save(member);
    }

    /** 첫 사용 가이드를 봤는가(FR-231). */
    @Transactional(readOnly = true)
    public boolean guideSeen(long memberId) {
        return MemberLoader.load(memberRepository, memberId).isGuideSeen();
    }
}
