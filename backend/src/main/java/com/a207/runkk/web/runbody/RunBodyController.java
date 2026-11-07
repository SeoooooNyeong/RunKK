package com.a207.runkk.web.runbody;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.runbody.RunBodyService;
import com.a207.runkk.web.runbody.RunBodyDtos.RunBodySessionResponse;
import java.time.YearMonth;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 런바디 결과를 내준다(설계 05-19 2.5, FR-266·FR-267, 앱 계약 runbody contract.md v1.0). 본인 것만 읽는다. 결과 본문은 ai 본문
 * 그대로다(D-442). 운영 기준값 RUNBODY_ENABLED가 꺼져 있으면 403 FEATURE_CLOSED(계약 0절).
 */
@RestController
@RequestMapping("/api/v1/runbody")
public class RunBodyController {

    /** ai 월 경로 규칙(20xx-01~12)과 같다. */
    private static final Pattern YEAR_MONTH = Pattern.compile("20[0-9]{2}-(0[1-9]|1[0-2])");

    private final RunBodyService service;

    public RunBodyController(RunBodyService service) {
        this.service = service;
    }

    @GetMapping("/sessions/{recordId}")
    ResponseEntity<RunBodySessionResponse> session(
            @RequestAttribute("memberId") long memberId, @PathVariable String recordId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(RunBodySessionResponse.of(service.sessionOf(memberId, recordId)));
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
