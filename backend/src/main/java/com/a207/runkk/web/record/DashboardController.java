package com.a207.runkk.web.record;

import com.a207.runkk.analysis.DashboardService;
import java.time.LocalDate;
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

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    RecordDtos.DashboardResponse dashboard(
            @RequestAttribute("memberId") long memberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate baseDate) {
        return RecordDtos.DashboardResponse.of(dashboardService.dashboard(memberId, baseDate));
    }
}
