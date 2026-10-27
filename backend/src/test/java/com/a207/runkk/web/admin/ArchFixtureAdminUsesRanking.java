package com.a207.runkk.web.admin;

import com.a207.runkk.analysis.RankingService;

/** ArchUnit 음성 시험 전용(규칙 1 위반, TC-6831). 랭킹 다시 계산은 web.admin.ranking이 맡는다(D-136). */
public class ArchFixtureAdminUsesRanking {
    public Object recompute(RankingService rankings) {
        return rankings.recompute();
    }
}
