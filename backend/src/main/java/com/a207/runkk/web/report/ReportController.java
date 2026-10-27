package com.a207.runkk.web.report;

import com.a207.runkk.report.ReportCommand;
import com.a207.runkk.report.ReportReason;
import com.a207.runkk.report.ReportResult;
import com.a207.runkk.report.ReportService;
import com.a207.runkk.report.ReportStatus;
import com.a207.runkk.report.ReportTargetKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원의 콘텐츠 신고(설계 5.7.1 POST /reports → ReportService.submit, 부록 F F.2.2 신고, FR-214, FR-215, D-104).
 * 신고자는 인증 필터가 붙인 회원 ID다. 오류: 409
 * REPORT_TARGET_NOT_FOUND·NOT_CREW_MEMBER·TARGET_INVALID·ALREADY_REPORTED, 400 BAD_REQUEST(기타 사유 내용
 * 없음·200자 초과, ReportCommand 생성자, D-127).
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private static final String MEMBER_ID = "memberId";

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    ReportResponse submit(
            @RequestAttribute(MEMBER_ID) long memberId, @Valid @RequestBody ReportRequest request) {
        ReportResult result = reportService.submit(memberId, request.toCommand());
        return new ReportResponse(result.reportId(), result.status());
    }

    /**
     * 신고 요청. 신고내용은 앞뒤 공백을 지운 뒤 OTHER면 1~200자 필수, 다른 사유는 0~200자(코드 포인트, D-143, D-149)이고 검사는
     * report.ReportCommand 생성자가 한다(400).
     *
     * @param targetKind 대상 종류
     * @param targetId 게시글 ID 또는 채팅 메시지 ID
     * @param reason 사유
     * @param detail 신고내용
     */
    record ReportRequest(
            @NotNull ReportTargetKind targetKind,
            @NotNull Long targetId,
            @NotNull ReportReason reason,
            String detail) {

        ReportCommand toCommand() {
            return new ReportCommand(targetKind, targetId, reason, detail);
        }
    }

    /**
     * 신고 접수 결과.
     *
     * @param reportId 신고 ID
     * @param status 접수 뒤 사례 상태(RECEIVED 또는 AUTO_HIDDEN)
     */
    record ReportResponse(long reportId, ReportStatus status) {}
}
