package com.a207.runkk.report;

import java.util.List;

/**
 * 관리툴 신고 사례 목록 한 쪽(설계 6.1.12a). PageCursor.slice 결과를 옮긴 것이다.
 *
 * @param cases 사례(openedAt 오래된 순)
 * @param nextCursor 다음 페이지 커서(이 쪽 마지막 사례 ID), 없으면 null
 */
public record ReportCaseListView(List<ReportCaseView> cases, Long nextCursor) {

    public ReportCaseListView {
        cases = List.copyOf(cases);
    }
}
