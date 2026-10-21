package com.a207.runkk.web.record;

import com.a207.runkk.analysis.RankingService;
import com.a207.runkk.common.AggregationPeriod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개인·크루 랭킹(설계 5.7.1 GET /rankings/personal, GET /rankings/crew → RankingService.personalRanking,
 * crewRanking, FR-070, FR-071, D-36). 0시 스냅숏만 읽는다(D-25).
 */
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingController {

    private static final String MEMBER_ID = "memberId";

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/personal")
    RecordDtos.RankingResponse personal(
            @RequestAttribute(MEMBER_ID) long memberId,
            @RequestParam(defaultValue = "WEEKLY") AggregationPeriod period) {
        return RecordDtos.RankingResponse.of(rankingService.personalRanking(memberId, period));
    }

    @GetMapping("/crew")
    RecordDtos.RankingResponse crew(
            @RequestAttribute(MEMBER_ID) long memberId,
            @RequestParam(defaultValue = "WEEKLY") AggregationPeriod period) {
        return RecordDtos.RankingResponse.of(rankingService.crewRanking(memberId, period));
    }
}
