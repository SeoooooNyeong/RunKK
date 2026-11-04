package com.a207.runkk.web.record;

import com.a207.runkk.analysis.DashboardService;
import com.a207.runkk.analysis.DashboardView;
import com.a207.runkk.battle.BattleHistoryService;
import com.a207.runkk.battle.BattleOutcome;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대시보드(설계 5.7.1 GET /dashboard → DashboardService.dashboard, FR-050~FR-057, FR-077). 기준일이 없으면 오늘.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final BattleHistoryService battleHistoryService;

    public DashboardController(
            DashboardService dashboardService, BattleHistoryService battleHistoryService) {
        this.dashboardService = dashboardService;
        this.battleHistoryService = battleHistoryService;
    }

    @GetMapping
    RecordDtos.DashboardResponse dashboard(
            @RequestAttribute("memberId") long memberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate baseDate) {
        DashboardView view = dashboardService.dashboard(memberId, baseDate);
        Long battleId = view.latestRun() == null ? null : view.latestRun().battleId();
        BattleOutcome outcome =
                battleId == null
                        ? null
                        : battleHistoryService
                                .outcomesOf(memberId, List.of(battleId))
                                .get(battleId);
        return RecordDtos.DashboardResponse.of(view, outcome);
    }
}
