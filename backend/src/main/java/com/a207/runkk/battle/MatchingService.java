package com.a207.runkk.battle;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSettingService;
import com.a207.runkk.member.MemberSummary;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 랜덤 대결 매칭 대기 등록·취소(설계 6.1.6, srs.md 6.3 4.1, SQ-03). 회원 티어·위치 동의는 MemberQueryService로 읽고 기본 대결 거리는
 * MemberSettingService로 바꾼다. Member 엔티티를 읽지 않는다(design-review.md A44).
 */
@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final MemberQueryService memberQueryService;
    private final MemberSettingService memberSettingService;
    private final MatchQueue matchQueue;
    private final BattleRepository battleRepository;
    private final Clock clock;

    public MatchingService(
            MemberQueryService memberQueryService,
            MemberSettingService memberSettingService,
            MatchQueue matchQueue,
            BattleRepository battleRepository,
            Clock clock) {
        this.memberQueryService = memberQueryService;
        this.memberSettingService = memberSettingService;
        this.matchQueue = matchQueue;
        this.battleRepository = battleRepository;
        this.clock = clock;
    }

    /**
     * 4.1 의사결정표: 거리·위치 동의 → 언랭 → 이미 대기·대결 중 → 대기 등록 + 기본 대결 거리 기억. 대기열을 쓸 수 없으면 QUEUE_UNAVAILABLE.
     */
    @Transactional
    public MatchAcceptance requestMatch(long memberId, StandardDistance distance) {
        if (!distance.isRandomBattle()) {
            throw new BusinessRuleException(ReasonCode.BATTLE_DISTANCE_INVALID);
        }
        if (!memberQueryService.hasConsent(memberId, ConsentItem.LOCATION)) {
            throw new BusinessRuleException(ReasonCode.LOCATION_CONSENT_REQUIRED);
        }
        Tier tier = tierOf(memberId);
        if (!tier.isRanked()) {
            throw new BusinessRuleException(ReasonCode.RANKED_ONLY);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        boolean queued = queueing(() -> enqueueIfFree(memberId, tier, distance, now));
        if (!queued) {
            throw new BusinessRuleException(ReasonCode.ALREADY_IN_BATTLE);
        }
        memberSettingService.rememberBattleDistance(memberId, distance);
        return MatchAcceptance.waitingFor(distance);
    }

    private boolean enqueueIfFree(
            long memberId, Tier tier, StandardDistance distance, LocalDateTime now) {
        if (matchQueue.findWaiting(memberId).isPresent()
                || battleRepository.existsActive(memberId)) {
            return false;
        }
        return matchQueue.enqueue(MatchTicket.open(memberId, tier, distance, now));
    }

    /** WAITING이면 지우고 대기해제. 이미 배정됐으면 ALREADY_MATCHED(FR-085). 대기 중이 아니면 대기해제(재요청 제한 없음, Q-31). */
    public MatchAcceptance cancel(long memberId) {
        Optional<MatchTicket> ticket = queueing(() -> matchQueue.findWaiting(memberId));
        if (ticket.isPresent() && ticket.get().getStatus() == MatchStatus.WAITING) {
            queueing(() -> matchQueue.remove(memberId));
            return MatchAcceptance.released();
        }
        if (ticket.isPresent() || battleRepository.existsActive(memberId)) {
            throw new BusinessRuleException(ReasonCode.ALREADY_MATCHED);
        }
        return MatchAcceptance.released();
    }

    private Tier tierOf(long memberId) {
        return Optional.ofNullable(memberQueryService.summaries(List.of(memberId)).get(memberId))
                .map(MemberSummary::tier)
                .orElse(Tier.UNRANKED);
    }

    /** 대기열 저장소 장애(5.5 장애: Redis)는 "잠시 후 다시 시도"로 바꾼다. */
    private static <T> T queueing(Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException e) {
            log.warn("match_queue_unavailable error={}", e.getClass().getSimpleName());
            throw new BusinessRuleException(ReasonCode.QUEUE_UNAVAILABLE);
        }
    }
}
