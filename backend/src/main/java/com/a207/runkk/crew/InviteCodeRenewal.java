package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료된 초대 코드를 읽을 때 새로 발급한다(피그마 맞춤 W7, 결정 C5: 재발급 화면 삭제, contract-W7.md 1장). 크루 조회는 읽기 전용 트랜잭션이라 새 쓰기
 * 트랜잭션에서 크루 행을 잠그고 다시 확인한 뒤 바꾼다. 둘이 함께 읽어도 잠금 뒤 두 번째는 이미 바뀐 코드를 받는다.
 */
@Component
public class InviteCodeRenewal {

    private final CrewRepository crewRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final Clock clock;

    InviteCodeRenewal(
            CrewRepository crewRepository, InviteCodeGenerator inviteCodeGenerator, Clock clock) {
        this.crewRepository = crewRepository;
        this.inviteCodeGenerator = inviteCodeGenerator;
        this.clock = clock;
    }

    /** 지금 유효한 코드면 그대로, 만료됐으면 새로 발급해 저장한 코드. 크루가 없으면 CREW_NOT_FOUND. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InviteCode currentOf(long crewId) {
        Crew crew =
                crewRepository
                        .findByIdForUpdate(crewId)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.CREW_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        if (!crew.inviteCode().isValidAt(now)) {
            crew.reissueInviteCode(inviteCodeGenerator.issue(now));
            crewRepository.save(crew);
        }
        return crew.inviteCode();
    }
}
