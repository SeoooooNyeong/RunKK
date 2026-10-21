package com.a207.runkk.member;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 프로필 편집 유스케이스를 조정한다(설계 6.1.2, FR-007, srs.md 6.3 1.3). 닉네임 변경 횟수 제한은 없다. */
@Service
public class ProfileService {

    private final MemberRepository memberRepository;
    private final List<ReservedNicknameChecker> reservedNicknameCheckers;

    public ProfileService(
            MemberRepository memberRepository,
            List<ReservedNicknameChecker> reservedNicknameCheckers) {
        this.memberRepository = memberRepository;
        this.reservedNicknameCheckers = List.copyOf(reservedNicknameCheckers);
    }

    /** 닉네임 중복(회원·가상) 검사 → Member.editProfile. 겹치면 BusinessRuleException(NICKNAME_INVALID). */
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
                    throw new BusinessRuleException(ReasonCode.NICKNAME_INVALID);
                }
                newNickname = requested;
            }
        }
        member.editProfile(edit, newNickname);
        memberRepository.save(member);
        return new ProfileEditResult(
                member.getId(),
                member.nickname().value(),
                member.birthYear(),
                member.gender(),
                member.photoUrl(),
                member.intro(),
                member.getTags().tags());
    }
}
