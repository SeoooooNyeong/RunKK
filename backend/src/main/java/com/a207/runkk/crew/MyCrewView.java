package com.a207.runkk.crew;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 본인 크루 현황(크루 탭 첫 화면). 소속 크루가 있으면 그 정보, 없으면 null이고, 대기 중인 본인 가입 신청을 함께 준다(신청 취소 FR-143). 설계 5.7.1에
 * 없던 조회라 B 단계에서 더했다(앱–서버 빈 곳).
 *
 * @param crew 소속 크루, 없으면 null
 * @param pendingRequests 대기 중인 본인 가입 신청(신청 일시순)
 * @param rejoinAvailableAt 24시간 가입·생성 제한 중이면 풀리는 시각, 아니면 null(피그마 F-02c, S15P31A207-295)
 */
public record MyCrewView(
        CrewDetailView crew, List<PendingJoin> pendingRequests, LocalDateTime rejoinAvailableAt) {

    public MyCrewView {
        pendingRequests = List.copyOf(pendingRequests);
    }

    /**
     * 대기 중인 가입 신청 한 건.
     *
     * @param requestId 가입 신청 ID
     * @param crewId 크루 ID
     * @param crewName 크루명(크루가 지워졌으면 null)
     * @param requestedAt 신청 일시
     */
    public record PendingJoin(
            long requestId, long crewId, String crewName, LocalDateTime requestedAt) {}
}
