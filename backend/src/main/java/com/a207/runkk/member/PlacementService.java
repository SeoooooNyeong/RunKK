package com.a207.runkk.member;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.Tier;
import com.a207.runkk.policy.OperationPolicy;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치고사 진행의 조회·잠금·셈을 회원 티어와 함께 처리한다(05-21 2.1, D-475~D-489, D-516·D-518). 진행 행은 lockForMatch만 만들고,
 * 셈은 행을 잠근 뒤 판단해 같은 판을 여러 계기(판정 사건·기록 연결 사건·복구 작업)가 불러도 한 번만 센다.
 */
@Service
public class PlacementService {

    private static final Logger log = LoggerFactory.getLogger(PlacementService.class);

    private final MemberRepository memberRepository;
    private final PlacementProgressRepository progressRepository;
    private final OperationPolicy policy;

    public PlacementService(
            MemberRepository memberRepository,
            PlacementProgressRepository progressRepository,
            OperationPolicy policy) {
        this.memberRepository = memberRepository;
        this.progressRepository = progressRepository;
        this.policy = policy;
    }

    /**
     * 본인 진행(D-488): 회원 티어 UNRANKED → IN_PROGRESS, 티어가 있고 완료된 진행 행 → COMPLETED, 그 밖 → EXEMPT. 임시 티어는
     * 이 값에만 담는다(D-485).
     */
    @Transactional(readOnly = true)
    public PlacementView view(long memberId) {
        Member member = MemberLoader.load(memberRepository, memberId);
        Optional<PlacementProgress> progress = progressRepository.findById(memberId);
        Tier tier = member.getTier();
        if (!tier.isRanked()) {
            int counted = progress.map(PlacementProgress::countedRounds).orElse(0);
            return new PlacementView(
                    PlacementStatus.IN_PROGRESS,
                    counted,
                    counted + 1,
                    progress.flatMap(PlacementProgress::provisionalTier).orElse(null),
                    tier,
                    progress.map(PlacementProgress::rounds).orElse(List.of()));
        }
        if (progress.isPresent() && progress.get().isCompleted()) {
            return new PlacementView(
                    PlacementStatus.COMPLETED,
                    progress.get().countedRounds(),
                    null,
                    null,
                    tier,
                    progress.get().rounds());
        }
        return new PlacementView(PlacementStatus.EXEMPT, 0, null, null, tier, List.of());
    }

    /**
     * 배치고사 대결을 만들기 전 진행 행을 잠근다(없으면 만든다, D-478). 잠근 뒤 회원을 DB에서 다시 읽는다(같은 트랜잭션이 앞서 올린 1차 캐시 값이 아님). 그
     * 사이 탈퇴가 커밋됐으면(ACTIVE 아님) BusinessRuleException(MATCH_FAILED)으로 되돌려 탈퇴 회원의 진행 행·대결을 남기지
     * 않는다(J24-9, 코드 리뷰 PL1-B3). 랭크 티어이거나 진행이 완료면(3판째 셈이 그 사이 커밋) 비어 있고, 부르는 쪽이 확정 티어 흐름으로
     * 간다(D-518). 부르는 쪽 트랜잭션 안에서만 돈다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PlacementBasis> lockForMatch(long memberId) {
        PlacementProgress progress = progressRepository.lockOrCreate(memberId);
        Member member = MemberLoader.load(memberRepository, memberId);
        memberRepository.refresh(member);
        if (member.getStatus() != MemberStatus.ACTIVE) {
            log.info("placement_match_inactive memberId={}", memberId);
            throw new BusinessRuleException(ReasonCode.MATCH_FAILED);
        }
        if (member.getTier().isRanked() || progress.isCompleted()) {
            return Optional.empty();
        }
        return Optional.of(progress.basis());
    }

    /**
     * 판 하나를 센다(D-481·D-483·D-516). 진행 행을 잠근 뒤(만들지 않음) 행 없음·회원 비활성(탈퇴)·랭크 티어·완료·같은 대결이면 Ignored.
     * 3판째면 같은 트랜잭션에서 Member.confirmPlacement.
     */
    @Transactional
    public PlacementCount countRound(long memberId, PlacementRoundInput in) {
        Optional<PlacementProgress> locked = progressRepository.findByIdForUpdate(memberId);
        if (locked.isEmpty()) {
            return ignored(memberId, in, "no-progress");
        }
        Optional<Member> member = memberRepository.findById(memberId);
        if (member.isEmpty() || member.get().getStatus() != MemberStatus.ACTIVE) {
            return ignored(memberId, in, "inactive");
        }
        if (member.get().getTier().isRanked()) {
            return ignored(memberId, in, "ranked");
        }
        PlacementProgress progress = locked.get();
        PlacementCount count = progress.count(in, policy.tierBoundary());
        if (count instanceof PlacementCount.Ignored ignored) {
            return ignored(memberId, in, ignored.reason());
        }
        progressRepository.save(progress);
        if (count instanceof PlacementCount.Confirmed confirmed) {
            member.get().confirmPlacement(confirmed.tier());
            memberRepository.save(member.get());
        }
        // 임시 티어는 본인 응답에만 담는다(D-485). 로그에는 결과 종류와 판 번호만 남긴다(코드 리뷰 PL1-B6)
        log.info(
                "placement_round_counted memberId={} battleId={} result={} round={}",
                memberId,
                in.battleId(),
                count.getClass().getSimpleName(),
                progress.countedRounds());
        return count;
    }

    /** 판정 정정(D-481). 진행 행이나 그 판이 없으면 아무것도 하지 않는다. */
    @Transactional
    public void correctVerdict(long memberId, long battleId, PlacementVerdict verdict) {
        progressRepository
                .findByIdForUpdate(memberId)
                .filter(p -> p.correctVerdict(battleId, verdict))
                .ifPresent(progressRepository::save);
    }

    /** 그 대결을 판으로 셌는가(판 결과 상태, D-487). */
    @Transactional(readOnly = true)
    public boolean isCounted(long memberId, long battleId) {
        return progressRepository.existsRound(memberId, battleId);
    }

    /** 진행 행이 있고 배치고사를 마쳤는가(판 결과 상태, D-487). */
    @Transactional(readOnly = true)
    public boolean isCompleted(long memberId) {
        return progressRepository
                .findById(memberId)
                .map(PlacementProgress::isCompleted)
                .orElse(false);
    }

    private static PlacementCount ignored(long memberId, PlacementRoundInput in, String reason) {
        log.info(
                "placement_round_ignored memberId={} battleId={} reason={}",
                memberId,
                in.battleId(),
                reason);
        return new PlacementCount.Ignored(reason);
    }
}
