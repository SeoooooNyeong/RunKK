package com.a207.runkk.battle;

import com.a207.runkk.member.ReservedNicknameChecker;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 가상 닉네임과 같은 회원 닉네임을 막는다(설계 6.1.6, member.ReservedNicknameChecker 구현, H-11'). */
@Component
public class VirtualNicknameChecker implements ReservedNicknameChecker {

    private final VirtualRecordRepository virtualRecordRepository;

    public VirtualNicknameChecker(VirtualRecordRepository virtualRecordRepository) {
        this.virtualRecordRepository = virtualRecordRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isReserved(String nickname) {
        return virtualRecordRepository.existsByNickname(nickname);
    }
}
