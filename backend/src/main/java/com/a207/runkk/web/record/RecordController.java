package com.a207.runkk.web.record;

import com.a207.runkk.analysis.GrowthService;
import com.a207.runkk.analysis.RecordListView;
import com.a207.runkk.analysis.RecordQueryService;
import com.a207.runkk.analysis.ReportService;
import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.DateRange;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기록 목록·상세·카드·성장·리포트(설계 5.7.1 GET /members/{id}/runs, GET /runs/{id}, GET /runs/{id}/card, GET
 * /growth, GET /report). 다른 러너의 비공개·크루 공개 기록은 409(RECORD_PRIVATE·RECORD_CREW_ONLY), 남의 카드는 403이다.
 */
@RestController
@RequestMapping("/api/v1")
public class RecordController {

    private static final String MEMBER_ID = "memberId";

    private final RecordQueryService recordQueryService;
    private final GrowthService growthService;
    private final ReportService reportService;

    public RecordController(
            RecordQueryService recordQueryService,
            GrowthService growthService,
            ReportService reportService) {
        this.recordQueryService = recordQueryService;
        this.growthService = growthService;
        this.reportService = reportService;
    }

    @GetMapping("/members/{id}/runs")
    RecordDtos.RecordListResponse list(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long ownerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        RecordListView view = recordQueryService.list(memberId, ownerId, new DateRange(from, to));
        return new RecordDtos.RecordListResponse(
                view.ownerId(), view.runs().stream().map(RecordDtos.RunSummaryDto::of).toList());
    }

    @GetMapping("/runs/{id}")
    RecordDtos.RecordDetailResponse detail(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") String recordId) {
        return RecordDtos.RecordDetailResponse.of(recordQueryService.detail(memberId, recordId));
    }

    @GetMapping("/runs/{id}/card")
    RecordDtos.RunCardResponse card(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") String recordId) {
        return RecordDtos.RunCardResponse.of(recordQueryService.cardData(memberId, recordId));
    }

    @GetMapping("/growth")
    RecordDtos.GrowthResponse growth(
            @RequestAttribute(MEMBER_ID) long memberId,
            @RequestParam(defaultValue = "WEEKLY") AggregationPeriod period) {
        return RecordDtos.GrowthResponse.of(growthService.growth(memberId, period));
    }

    @GetMapping("/report")
    RecordDtos.ReportResponse report(@RequestAttribute(MEMBER_ID) long memberId) {
        return RecordDtos.ReportResponse.of(reportService.report(memberId));
    }
}
