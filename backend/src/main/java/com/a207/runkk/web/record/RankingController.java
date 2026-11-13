package com.a207.runkk.web.record;

import com.a207.runkk.analysis.RankingService;
import com.a207.runkk.analysis.RankingView;
import com.a207.runkk.avatar.RunnerLookQuery;
import com.a207.runkk.common.AggregationPeriod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개인·크루 랭킹(설계 5.7.1 GET /rankings/personal, GET /rankings/crew → RankingService.personalRanking,
 * crewRanking, FR-070, FR-071, D-36). 0시 스냅숏만 읽는다(D-25). 개인 랭킹 줄에는 그 회원의 모습(look: 얼굴·의상·체형·러닝량)을 조회
 * 때 붙인다(S15P31A207-300). analysis → avatar 화살표를 만들지 않도록 web.crew의 크루원 얼굴처럼 표현 계층이 붙인다(D-46).
 */
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingController {

    private static final String MEMBER_ID = "memberId";

    private final RankingService rankingService;
    private final RunnerLookQuery runnerLookQuery;

    public RankingController(RankingService rankingService, RunnerLookQuery runnerLookQuery) {
        this.rankingService = rankingService;
        this.runnerLookQuery = runnerLookQuery;
    }

    @GetMapping("/personal")
    RecordDtos.RankingResponse personal(
            @RequestAttribute(MEMBER_ID) long memberId,
            @RequestParam(defaultValue = "WEEKLY") AggregationPeriod period) {
        RankingView view = rankingService.personalRanking(memberId, period);
        return RecordDtos.RankingResponse.of(view, runnerLookQuery.looksOf(view.activeMemberIds()));
    }

    @GetMapping("/crew")
    RecordDtos.RankingResponse crew(
            @RequestAttribute(MEMBER_ID) long memberId,
            @RequestParam(defaultValue = "WEEKLY") AggregationPeriod period) {
        return RecordDtos.RankingResponse.of(rankingService.crewRanking(memberId, period));
    }
}
