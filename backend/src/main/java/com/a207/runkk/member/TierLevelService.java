package com.a207.runkk.member;

import com.a207.runkk.policy.OperationPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유효 러닝 한 회를 티어·레벨 갱신에 반영한다(설계 6.1.2). running.TierUpdateListener가 유효 기록 저장 커밋 뒤 동기로 부른다(D-03 예외).
 * member는 RunRecordSaved를 받지 않는다(member ↔ running 순환 방지).
 */
@Service
public class TierLevelService {

    private final MemberRepository memberRepository;
    private final OperationPolicy policy;

    public TierLevelService(MemberRepository memberRepository, OperationPolicy policy) {
        this.memberRepository = memberRepository;
        this.policy = policy;
    }

    /**
     * Member.recordValidRun(input, policy.tierBoundary()) 뒤 저장. 같은 순번이 다시 오면 TierProgress가
     * 무시한다(멱등).
     */
    @Transactional
    public TierChange recordValidRun(long memberId, RunScoreInput input) {
        Member member = MemberLoader.load(memberRepository, memberId);
        TierChange change = member.recordValidRun(input, policy.tierBoundary());
        memberRepository.save(member);
        return change;
    }
}
