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
    private final RunnerAvailability runnerAvailability;
    private final PlacementMatcher placementMatcher;
    private final Clock clock;

    public MatchingService(
            MemberQueryService memberQueryService,
            MemberSettingService memberSettingService,
            MatchQueue matchQueue,
            RunnerAvailability runnerAvailability,
            PlacementMatcher placementMatcher,
            Clock clock) {
        this.memberQueryService = memberQueryService;
        this.memberSettingService = memberSettingService;
        this.matchQueue = matchQueue;
        this.runnerAvailability = runnerAvailability;
        this.placementMatcher = placementMatcher;
        this.clock = clock;
    }

    /**
     * 4.1 의사결정표(05-21 D-478): 거리 → 위치 동의 → 티어. 언랭은 배치고사 대결을 대기열 없이 바로 만들고(PlacementMatcher.start,
     * RANKED_ONLY는 더 내지 않음), 잠근 뒤 이미 랭크 티어였으면 확정 티어 흐름으로 간다(D-518). 확정 티어: 이미 대기·대결·초대 대기 중 → 대기 등록
     * + 기본 대결 거리 기억. 대기열을 쓸 수 없으면 QUEUE_UNAVAILABLE.
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
        LocalDateTime now = LocalDateTime.now(clock);
        if (!tier.isRanked()) {
            Optional<Battle> placement = placementMatcher.start(memberId, distance, now);
            if (placement.isPresent()) {
                memberSettingService.rememberBattleDistance(memberId, distance);
                Battle battle = placement.get();
                return MatchAcceptance.placement(
                        distance, battle.placementRound().orElseThrow(), battle.getId());
            }
            tier = tierOf(memberId); // 잠근 사이 3판째 셈이 커밋돼 랭크 티어가 됐다(D-518)
        }
        Tier ranked = tier;
        boolean queued = queueing(() -> enqueueIfFree(memberId, ranked, distance, now));
        if (!queued) {
            throw new BusinessRuleException(ReasonCode.ALREADY_IN_BATTLE);
        }
        try {
            memberSettingService.rememberBattleDistance(memberId, distance);
        } catch (RuntimeException e) {
            queueing(() -> matchQueue.remove(memberId)); // 요청이 실패했는데 대기열에 남지 않게 되돌린다
            throw e;
        }
        return MatchAcceptance.waitingFor(distance);
    }

    private boolean enqueueIfFree(
            long memberId, Tier tier, StandardDistance distance, LocalDateTime now) {
        if (runnerAvailability.isBusy(memberId, now)) { // 친구 대결 초대 대기 중도 바쁘다(FR-080, D-220)
            return false;
        }
        return matchQueue.enqueue(MatchTicket.open(memberId, tier, distance, now));
    }

    /**
     * 지금 매칭 대기 상태와 기본 대결 거리(GET /battles/match). 대기 중이면 대기 거리, 아니면 대기해제. 설계 5.7.1에 없던 조회로, 앱 대결 화면이
     * 회원 설정 없이 기본 대결 거리를 읽게 한다(FR-080, FR-109).
     */
    public MatchAcceptance status(long memberId) {
        Optional<MatchTicket> ticket = queueing(() -> matchQueue.findWaiting(memberId));
        StandardDistance remembered = defaultDistanceOf(memberId);
        return ticket.filter(t -> t.getStatus() == MatchStatus.WAITING)
                .map(t -> new MatchAcceptance(true, t.getDistance(), remembered))
                .orElseGet(() -> MatchAcceptance.released(remembered));
    }

    private StandardDistance defaultDistanceOf(long memberId) {
        return memberSettingService.battleSettingsOf(memberId).defaultDistance();
    }

    /**
     * MatchQueue.removeIfWaiting 한 번으로 정한다(D-83, 설계 부록 E.2 K-1): REMOVED면 대기해제, ASSIGNED면
     * 거부(ALREADY_MATCHED, FR-085), ABSENT면 거부(대기 중 아님. 사유 이름은 srs 6.3 4.1 ELSE 갈래 "이미 매칭됨"을 따른다).
     * 상태 확인과 삭제를 따로 부르지 않는다.
     */
    public MatchAcceptance cancel(long memberId) {
        QueueRemoval removal = queueing(() -> matchQueue.removeIfWaiting(memberId));
        if (removal != QueueRemoval.REMOVED) {
            throw new BusinessRuleException(ReasonCode.ALREADY_MATCHED);
        }
        return MatchAcceptance.released(defaultDistanceOf(memberId));
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
