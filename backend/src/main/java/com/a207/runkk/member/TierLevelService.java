package com.a207.runkk.member;

import com.a207.runkk.policy.OperationPolicy;
import java.util.OptionalInt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유효 러닝 한 회를 티어·레벨 갱신에 반영한다(설계 6.1.2). running.TierUpdateListener가 유효 기록 저장 커밋 뒤 동기로 부른다(D-03 예외).
 * member는 RunRecordSaved를 받지 않는다(member ↔ running 순환 방지).
 */
@Service
public class TierLevelService {

    private final MemberRepository memberRepository;
    private final PlacementProgressRepository placementProgressRepository;
    private final OperationPolicy policy;

    public TierLevelService(
            MemberRepository memberRepository,
            PlacementProgressRepository placementProgressRepository,
            OperationPolicy policy) {
        this.memberRepository = memberRepository;
        this.placementProgressRepository = placementProgressRepository;
        this.policy = policy;
    }

    /**
     * Member.recordValidRun(input, policy.tierBoundary(), 확정 판 순번) 뒤 저장. 같은 순번이 다시 오면 TierProgress가
     * 무시한다(멱등). 읽는 순서는 Member(@Version) 먼저, 배치 진행 행 그다음이다(D-517): 판 셈이 두 읽기 사이에 커밋돼도 Member는 아직
     * 언랭이라 재평가가 없고, 저장이 낙관적 잠금 충돌로 다시 돌아 두 값이 같은 시점이 된다.
     */
    @Transactional
    public TierChange recordValidRun(long memberId, RunScoreInput input) {
        Member member = MemberLoader.load(memberRepository, memberId);
        int placementOrdinal =
                placementProgressRepository
                        .findById(memberId)
                        .map(PlacementProgress::confirmedRunOrdinal)
                        .filter(OptionalInt::isPresent)
                        .map(OptionalInt::getAsInt)
                        .orElse(0);
        TierChange change = member.recordValidRun(input, policy.tierBoundary(), placementOrdinal);
        memberRepository.save(member);
        return change;
    }

    /**
     * 뒤 순번이 먼저 반영되어 구멍이 된 유효 러닝을 늦게 반영한다(총괄 결정 7-3,
     * running.TierReconcileService·TierUpdateListener). 판정 없이 점수·거리만 더하고 저장한다.
     */
    @Transactional
    public TierChange recordLateValidRun(long memberId, RunScoreInput input) {
        Member member = MemberLoader.load(memberRepository, memberId);
        TierChange change = member.recordLateValidRun(input);
        memberRepository.save(member);
        return change;
    }
}
