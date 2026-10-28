package com.a207.runkk.crew;

import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.WithdrawalGuard;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 크루원이 1명 이상 남은 크루의 크루장이면 탈퇴를 막는다(설계 6.1.7, member.WithdrawalGuard 구현, FR-024). */
@Component
public class CrewLeaderGuard implements WithdrawalGuard {

    private final CrewRepository crewRepository;

    public CrewLeaderGuard(CrewRepository crewRepository) {
        this.crewRepository = crewRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReasonCode> check(long memberId) {
        boolean leadsOthers =
                crewRepository.findMembershipHistory(memberId).stream()
                        .filter(m -> m.isActive() && m.role() == CrewRole.LEADER)
                        .map(m -> crewRepository.findById(m.crewId()))
                        .flatMap(Optional::stream)
                        .anyMatch(crew -> crew.headcount() > 1);
        return leadsOthers ? Optional.of(ReasonCode.LEADER_MUST_TRANSFER) : Optional.empty();
    }
}
