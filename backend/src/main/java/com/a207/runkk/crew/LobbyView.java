package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.StandardDistance;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 크루런 대기실(자료사전 크루런대기실: 게시글ID + 모집희망일시 + 크루런거리 + 남은시간 + {표시명 + 준비상태}, 설계 05-32 자료사전 대기실현황: + 크루런상태 +
 * 실제출발시각 + {출발후보식별}). 열람자에 따라 canStartNow·canJoin·meRunner·measurementRecordId가 다르다.
 *
 * @param postId 크루런 게시글 ID
 * @param crewId 크루 ID
 * @param scheduledAt 모집희망일시
 * @param distance 크루런 거리
 * @param remaining 시작까지 남은 시간(지났으면 0)
 * @param status 크루런 상태
 * @param phase 논리 단계(LOBBY·COUNTDOWN·RUNNING·FINISHED·CANCELLED)
 * @param revision 변경 순번
 * @param countdownToken 출발 예약 식별값, 예약 전이면 null
 * @param startAt 측정 시작 절대 시각(예약 시각 + 3초), 예약 전이면 null
 * @param countdownParticipantIds 출발 후보(카운트다운 중 남은 후보, 출발 뒤 함께 출발한 사람)
 * @param canStartNow 열람자가 「지금 시작」을 누를 수 있다
 * @param canJoin 열람자가 중간 참여할 수 있다
 * @param meRunner 열람자가 진행 참가자다
 * @param measurementRecordId 열람자의 측정 기록 ID(진행 참가자일 때만), 없으면 null
 * @param participants 갈래요 참가자의 준비 상태
 */
public record LobbyView(
        long postId,
        long crewId,
        LocalDateTime scheduledAt,
        StandardDistance distance,
        Duration remaining,
        CrewRunStatus status,
        CrewRunPhase phase,
        long revision,
        String countdownToken,
        LocalDateTime startAt,
        List<Long> countdownParticipantIds,
        boolean canStartNow,
        boolean canJoin,
        boolean meRunner,
        String measurementRecordId,
        List<Entry> participants) {

    public LobbyView {
        countdownParticipantIds = List.copyOf(countdownParticipantIds);
        participants = List.copyOf(participants);
    }

    /**
     * 참가자 한 명.
     *
     * @param memberId 회원 ID
     * @param name 표시명
     * @param readiness 준비 상태
     * @param shownGender 다른 러너에게 보이는 표시 성별 이름(MALE·FEMALE, MemberQueryService.shownGendersOf,
     *     D-327). 앱이 크루원 캐릭터를 이 값으로 그린다(크루원 아바타, S15P31A207-236)
     */
    public record Entry(long memberId, DisplayName name, Readiness readiness, String shownGender) {}
}
