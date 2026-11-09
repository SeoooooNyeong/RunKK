package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Tier;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 가입 대기 목록(자료사전 가입대기목록, FR-136). Runimal 유형은 web.crew가 회원 ID로 붙인다(D-46과 같은 방식).
 *
 * @param crewId 크루 ID
 * @param requests 대기 중 신청, 신청순
 */
public record PendingRequestsView(long crewId, List<Line> requests) {

    public PendingRequestsView {
        requests = List.copyOf(requests);
    }

    /**
     * 신청 한 건.
     *
     * @param requestId 가입 신청 ID
     * @param memberId 신청자
     * @param name 표시명
     * @param tier 티어, 없으면 null
     * @param requestedAt 신청 일시
     * @param profile 레벨·나이대·성별(공개 설정 반영, 피그마 맞춤 W7 F-25)
     */
    public record Line(
            long requestId,
            long memberId,
            DisplayName name,
            Tier tier,
            LocalDateTime requestedAt,
            CrewMemberProfile profile) {}
}
