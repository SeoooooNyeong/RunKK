package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 중간 참여 결과(설계 05-32 5.2 ParticipationResponse, 자료사전 합류현황). 본인 측정 시작 시각과 측정 기록 ID는 다시 불러도 같다. 다른 사람의
 * 기록 ID는 싣지 않는다.
 *
 * @param postId 크루런 게시글 ID
 * @param crewId 크루 ID
 * @param revision 변경 순번
 * @param joinedAt 본인 측정 시작 시각(출발 후보는 출발 시각, 중간 참여자는 승인 시각)
 * @param measurementRecordId 본인 측정 기록 ID(서버 예약 UUID, 저장 성공의 증거가 아님)
 * @param alreadyJoined 이미 진행 참가자였다(재요청)
 * @param runners 진행 참가자(참여 순)
 */
public record ParticipationView(
        long postId,
        long crewId,
        long revision,
        LocalDateTime joinedAt,
        String measurementRecordId,
        boolean alreadyJoined,
        List<Runner> runners) {

    public ParticipationView {
        runners = List.copyOf(runners);
    }

    /**
     * 진행 참가자 한 명(공개 값만).
     *
     * @param memberId 회원 ID
     * @param name 표시명
     * @param joinedAt 개인 측정 시작 시각
     * @param ended 자기 크루런을 끝냈다
     */
    public record Runner(long memberId, DisplayName name, LocalDateTime joinedAt, boolean ended) {}
}
