package com.a207.runkk.web.record;

import com.a207.runkk.analysis.GrowthService;
import com.a207.runkk.analysis.RecordDetailView;
import com.a207.runkk.analysis.RecordQueryService;
import com.a207.runkk.analysis.ReportService;
import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.running.EvaluationView;
import com.a207.runkk.running.RunDeletionService;
import com.a207.runkk.running.RunQueryService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    private final RecordListAssembler recordListAssembler;
    private final RunDeletionService runDeletionService;
    private final RunQueryService runQueryService;

    public RecordController(
            RecordQueryService recordQueryService,
            GrowthService growthService,
            ReportService reportService,
            RecordListAssembler recordListAssembler,
            RunDeletionService runDeletionService,
            RunQueryService runQueryService) {
        this.recordQueryService = recordQueryService;
        this.growthService = growthService;
        this.reportService = reportService;
        this.recordListAssembler = recordListAssembler;
        this.runDeletionService = runDeletionService;
        this.runQueryService = runQueryService;
    }

    @GetMapping("/members/{id}/runs")
    RecordDtos.RecordListResponse list(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long ownerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return recordListAssembler.assemble(memberId, ownerId, new DateRange(from, to));
    }

    /** 기록 상세. 본인 기록이면 그 러닝이 일으킨 티어 평가(evaluation)를 붙인다(v0.14 D-291). */
    @GetMapping("/runs/{id}")
    RecordDtos.RecordDetailResponse detail(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") String recordId) {
        RecordDetailView view = recordQueryService.detail(memberId, recordId);
        EvaluationView evaluation =
                view.owner() ? runQueryService.evaluationOf(recordId).orElse(null) : null;
        return RecordDtos.RecordDetailResponse.of(view, evaluation);
    }

    /**
     * 집계 제외 기록 삭제(FR-225, v0.14 D-231, 12.2 W2). 되돌릴 수 없다. 204. 없으면 409 RECORD_NOT_FOUND, 남의 기록은
     * 403 PERMISSION_DENIED, 유효·재판정 대기 기록은 409 RECORD_VALID_NOT_DELETABLE.
     */
    @DeleteMapping("/runs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") String recordId) {
        runDeletionService.delete(memberId, recordId);
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
