package com.a207.runkk.web.admin.audit;

import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditRecordView;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.DateRange;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감사 기록 조회(설계 5.7.1 GET /admin/audits → AuditService.query, FR-177). 권한은 web.admin의
 * OperatorCheckInterceptor가 먼저 확인한다(D-50). 기간은 보관 기간(6개월)을 넘지 않게 186일까지 받는다. 결과는 최신순이다.
 */
@RestController
@RequestMapping("/api/v1/admin/audits")
public class AuditAdminController {

    /** 한 번에 조회할 수 있는 최대 기간(일). 보관 기간 6개월(FR-176) 안. */
    static final long MAX_DAYS = 186;

    private final AuditService auditService;

    public AuditAdminController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** actorId는 쓰지 않지만 인터셉터가 확인한 요청만 들어온다는 계약을 드러내려고 받는다. */
    @GetMapping
    List<AuditRecordView> query(
            @RequestAttribute("actorId") String actorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AuditAction action) {
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new IllegalArgumentException("조회 기간은 186일까지");
        }
        return auditService.query(new DateRange(from, to), Optional.ofNullable(action));
    }
}
