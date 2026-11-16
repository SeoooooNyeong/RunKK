package com.a207.runkk.crew.live;

import com.a207.runkk.crew.CrewRunMessage;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 크루런 출발 사실(설계 05-32 5.3, [임시:D-성-883]). 출발이 확정되면 그때 대기실 입장자 전원(준비하지 않은 사람 포함)에게 보낸다. 자동 측정은
 * participantIds(확정 진행 참가자)만 하고, 나머지는 「함께 뛰기」(POST /crew-runs/{id}/join) 또는 「나가기」를 고른다. startAt은 측정
 * 시작 절대 시각이고 앱은 다시 3초를 더하지 않는다. 같은 countdownToken은 한 번만 처리한다.
 *
 * @param crewId 크루 ID
 * @param postId 크루런 게시글 ID
 * @param revision 그 크루런의 변경 순번
 * @param countdownToken 출발 예약 식별값
 * @param startAt 측정 시작 절대 시각(예약 시각 + 3초)
 * @param participantIds 확정 진행 참가자 회원 ID(후보 중 취소, 퇴장하지 않은 사람, 0명일 수 있다)
 */
public record CrewRunDeparture(
        long crewId,
        long postId,
        long revision,
        String countdownToken,
        LocalDateTime startAt,
        List<Long> participantIds)
        implements CrewRunMessage {

    public CrewRunDeparture {
        participantIds = List.copyOf(participantIds);
    }

    @Override
    public String type() {
        return "CrewRunDeparture";
    }
}
