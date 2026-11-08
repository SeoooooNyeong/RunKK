package com.a207.runkk.web.runbody;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.runbody.RunBodyService;
import com.a207.runkk.runbody.RunBodyTrend;
import com.a207.runkk.runbody.RunBodyTrendService;
import com.a207.runkk.runimal.RunimalQuery;
import com.a207.runkk.runimal.RunimalType;
import com.a207.runkk.web.runbody.RunBodyDtos.RunBodySessionListResponse;
import com.a207.runkk.web.runbody.RunBodyDtos.RunBodySessionResponse;
import com.a207.runkk.web.runbody.RunBodyDtos.RunBodyTrendResponse;
import java.time.YearMonth;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 런바디 결과를 내준다(설계 05-19 2.5, FR-266·FR-267, 앱 계약 runbody contract.md v1.2). 본인 것만 읽는다. 결과 본문은 ai 본문
 * 그대로다(D-442). 러닝 선택 목록(G-30)은 작업에 남긴 러닝 점수만, 요약 최근 10회 변화(G-14)는 러닝 기록에서 셈한 값을 준다. 운영 기준값
 * RUNBODY_ENABLED가 꺼져 있으면 403 FEATURE_CLOSED(계약 0절).
 */
@RestController
@RequestMapping("/api/v1/runbody")
public class RunBodyController {

    /** ai 월 경로 규칙(20xx-01~12)과 같다. */
    private static final Pattern YEAR_MONTH = Pattern.compile("20[0-9]{2}-(0[1-9]|1[0-2])");

    /** 러닝 선택 목록 한 쪽의 기본 줄 수. */
    static final int DEFAULT_PAGE_SIZE = 20;

    private final RunBodyService service;
    private final RunBodyTrendService trends;
    private final RunimalQuery runimals;

    public RunBodyController(
            RunBodyService service, RunBodyTrendService trends, RunimalQuery runimals) {
        this.service = service;
        this.trends = trends;
        this.runimals = runimals;
    }

    /** 러닝 선택 목록의 분석 상태와 러닝 점수(G-30), 최신순. size 1~100(기본 20), cursor는 직전 응답 nextCursor. */
    @GetMapping("/sessions")
    ResponseEntity<RunBodySessionListResponse> sessions(
            @RequestAttribute("memberId") long memberId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        RunBodySessionListResponse.of(
                                service.sessionsOf(
                                        memberId,
                                        cursor,
                                        size == null ? DEFAULT_PAGE_SIZE : size)));
    }

    @GetMapping("/sessions/{recordId}")
    ResponseEntity<RunBodySessionResponse> session(
            @RequestAttribute("memberId") long memberId, @PathVariable String recordId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(RunBodySessionResponse.of(service.sessionOf(memberId, recordId)));
    }

    /** 요약 최근 10회 변화(G-14)와 나의 러닝 유형. */
    @GetMapping("/trend")
    ResponseEntity<RunBodyTrendResponse> trend(@RequestAttribute("memberId") long memberId) {
        RunBodyTrend trend = trends.trendOf(memberId);
        RunimalType type = runimals.typesOf(Set.of(memberId)).get(memberId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(RunBodyTrendResponse.of(trend, type == null ? null : type.name()));
    }

    @GetMapping("/months/{yearMonth}")
    ResponseEntity<String> month(
            @RequestAttribute("memberId") long memberId, @PathVariable String yearMonth) {
        if (!YEAR_MONTH.matcher(yearMonth).matches()) {
            throw new BusinessRuleException(ReasonCode.INVALID_YEAR_MONTH);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .body(service.monthOf(memberId, YearMonth.parse(yearMonth)).body());
    }
}
