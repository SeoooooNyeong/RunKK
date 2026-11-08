package com.a207.runkk.event;

import java.util.List;

/**
 * 이벤트 응모자 명단 한 쪽(관리, 추첨용, S15P31A207-275). 응모 조건을 만족한 ACTIVE 회원만 담고 회원 ID 내림차순이다. 개인정보는 회원 ID와 닉네임만
 * 싣는다.
 *
 * @param eventId 이벤트 ID
 * @param slug 주소 이름
 * @param kind 종류
 * @param celebrityId 셀럽 기록으로 거른 명단이면 그 ID, 아니면 null
 * @param total 거른 뒤 전체 응모자 수(이 쪽만이 아니다)
 * @param entrants 이 쪽의 응모자
 * @param nextCursor 다음 쪽 커서(이 쪽 마지막 회원 ID), 없으면 null
 */
public record EventEntrants(
        long eventId,
        String slug,
        EventKind kind,
        Long celebrityId,
        int total,
        List<Entrant> entrants,
        Long nextCursor) {

    public EventEntrants {
        entrants = List.copyOf(entrants);
    }

    /**
     * 응모자 한 명. items는 내 참여(GET /events/{slug}/me)와 같은 모양이다: COACH_CELEB은 셀럽마다 한 항목(이긴 첫 판정 시각),
     * RUN_DISTANCE는 기준 거리 이상 첫 기록 하나(시작 시각과 거리).
     *
     * @param memberId 회원 ID
     * @param nickname 닉네임
     * @param entryCount 응모 수
     * @param items 응모 항목
     */
    public record Entrant(
            long memberId, String nickname, int entryCount, List<EventEntry.Item> items) {

        public Entrant {
            items = List.copyOf(items);
        }
    }
}
