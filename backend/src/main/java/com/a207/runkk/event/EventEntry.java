package com.a207.runkk.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 이벤트 참여(GET /events/{slug}/me, S15P31A207-275). 응모 수는 끝낸 항목 수다.
 *
 * @param slug 주소 이름
 * @param entryCount 내 응모 수
 * @param maxEntryCount 최대 응모 수(항목 수)
 * @param items 응모 항목(COACH_CELEB는 셀럽마다 하나, RUN_DISTANCE는 하나)
 */
public record EventEntry(String slug, int entryCount, int maxEntryCount, List<Item> items) {

    public EventEntry {
        items = List.copyOf(items);
    }

    /**
     * 응모 항목 하나.
     *
     * @param key 항목 키("celebrity-11", "run-1km")
     * @param label 표시 이름(셀럽 표시명, "1km")
     * @param done 끝냈는가
     * @param doneAt 끝낸 시각(첫 승리 판정 시각, 가장 이른 기록 시작 시각), 아니면 null
     * @param distanceMeters 응모한 기록 거리(RUN_DISTANCE이고 끝냈을 때만), 아니면 null
     */
    public record Item(
            String key, String label, boolean done, LocalDateTime doneAt, Long distanceMeters) {}
}
