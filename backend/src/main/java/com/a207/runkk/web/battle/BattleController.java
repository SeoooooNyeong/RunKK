package com.a207.runkk.web.battle;

import com.a207.runkk.battle.BattleHistoryService;
import com.a207.runkk.battle.BattleResultService;
import com.a207.runkk.battle.MatchingService;
import com.a207.runkk.battle.RecordBattleService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대결 엔드포인트(설계 5.7.1: /battles/*). 엔드포인트 하나를 서비스 연산 하나에 잇고 요청·응답 형식만 바꾼다. 결과 화면은
 * BattleResultAssembler가 조합한다(D-33).
 */
@RestController
@RequestMapping("/api/v1/battles")
public class BattleController {

    private static final String MEMBER_ID = "memberId";

    /** 이력 기본 조회 기간(조회 기간을 주지 않았을 때). */
    private static final int DEFAULT_HISTORY_MONTHS = 12;

    private final MatchingService matchingService;
    private final RecordBattleService recordBattleService;
    private final BattleResultService battleResultService;
    private final BattleHistoryService battleHistoryService;
    private final BattleResultAssembler battleResultAssembler;
    private final Clock clock;

    BattleController(
            MatchingService matchingService,
            RecordBattleService recordBattleService,
            BattleResultService battleResultService,
            BattleHistoryService battleHistoryService,
            BattleResultAssembler battleResultAssembler,
            Clock clock) {
        this.matchingService = matchingService;
        this.recordBattleService = recordBattleService;
        this.battleResultService = battleResultService;
        this.battleHistoryService = battleHistoryService;
        this.battleResultAssembler = battleResultAssembler;
        this.clock = clock;
    }

    @PostMapping("/match")
    BattleDtos.MatchAcceptanceResponse requestMatch(
            @RequestAttribute(MEMBER_ID) long memberId,
            @Valid @RequestBody BattleDtos.DistanceRequest request) {
        return BattleDtos.MatchAcceptanceResponse.of(
                matchingService.requestMatch(memberId, standard(request.distanceMeters())));
    }

    /** 매칭 대기 상태와 기본 대결 거리(설계 5.7.1에 없어 더한 조회, FR-080·FR-109). */
    @GetMapping("/match")
    BattleDtos.MatchAcceptanceResponse matchStatus(@RequestAttribute(MEMBER_ID) long memberId) {
        return BattleDtos.MatchAcceptanceResponse.of(matchingService.status(memberId));
    }

    @DeleteMapping("/match")
    BattleDtos.MatchAcceptanceResponse cancel(@RequestAttribute(MEMBER_ID) long memberId) {
        return BattleDtos.MatchAcceptanceResponse.of(matchingService.cancel(memberId));
    }

    @GetMapping("/self/candidates")
    BattleDtos.CandidatesResponse selfCandidates(@RequestAttribute(MEMBER_ID) long memberId) {
        return BattleDtos.CandidatesResponse.of(recordBattleService.selfCandidates(memberId));
    }

    @GetMapping("/celebrity/candidates")
    BattleDtos.CandidatesResponse celebrityCandidates(
            @RequestAttribute(MEMBER_ID) long memberId,
            @RequestParam(required = false) Long distanceMeters) {
        Optional<StandardDistance> filter =
                Optional.ofNullable(distanceMeters).map(BattleController::standard);
        return BattleDtos.CandidatesResponse.of(
                recordBattleService.celebrityCandidates(memberId, filter));
    }

    @PostMapping("/self")
    BattleDtos.RecordBattleStartResponse startSelfBattle(
            @RequestAttribute(MEMBER_ID) long memberId,
            @Valid @RequestBody BattleDtos.DistanceRequest request) {
        return BattleDtos.RecordBattleStartResponse.of(
                recordBattleService.startSelfBattle(memberId, standard(request.distanceMeters())));
    }

    @PostMapping("/celebrity")
    BattleDtos.RecordBattleStartResponse startCelebrityBattle(
            @RequestAttribute(MEMBER_ID) long memberId,
            @Valid @RequestBody BattleDtos.CelebrityBattleRequest request) {
        return BattleDtos.RecordBattleStartResponse.of(
                recordBattleService.startCelebrityBattle(memberId, request.celebrityId()));
    }

    @PostMapping("/{id}/started")
    ResponseEntity<Void> markStarted(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable long id) {
        recordBattleService.markStarted(memberId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/forfeit")
    ResponseEntity<Void> forfeit(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable long id) {
        battleResultService.forfeit(memberId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/result")
    BattleDtos.BattleResultResponse result(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable long id) {
        return battleResultAssembler.assemble(memberId, id);
    }

    /**
     * type: RANDOM(고스트 포함)·SELF·CELEBRITY. from·to가 없으면 최근 12개월. size를 주면 그만큼만, 다음 페이지는 응답
     * nextCursor를 cursor로(주지 않으면 전부, 기존과 같다).
     */
    @GetMapping("/history")
    BattleDtos.BattleHistoryResponse history(
            @RequestAttribute(MEMBER_ID) long memberId,
            @ModelAttribute BattleDtos.HistoryQuery query) {
        LocalDate today = LocalDate.now(clock);
        DateRange range =
                new DateRange(
                        query.from() == null
                                ? today.minusMonths(DEFAULT_HISTORY_MONTHS)
                                : query.from(),
                        query.to() == null ? today : query.to());
        return BattleDtos.BattleHistoryResponse.of(
                battleHistoryService.history(
                        memberId, range, Optional.ofNullable(query.type()), query.page()));
    }

    /** 미터 → 기준 거리. 기준 거리가 아니면 BATTLE_DISTANCE_INVALID. */
    private static StandardDistance standard(long meters) {
        return Arrays.stream(StandardDistance.values())
                .filter(d -> d.distance().meters() == meters)
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.BATTLE_DISTANCE_INVALID));
    }
}
