package com.a207.runkk.notification;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림함(자료사전 알림함, FR-178). 최신순이고 오늘·이번 주·그 전 묶음은 화면이 한다.
 *
 * @param items 알림 목록
 */
public record InboxView(List<Item> items) {

    public InboxView {
        items = List.copyOf(items);
    }

    /**
     * 알림함 한 줄.
     *
     * @param id 알림 ID
     * @param type 알림 종류
     * @param content 내용
     * @param occurredAt 발생 일시
     * @param read 읽음 여부
     * @param target 이동 대상
     */
    public record Item(
            long id,
            NotificationType type,
            String content,
            LocalDateTime occurredAt,
            boolean read,
            NavigationTarget target) {}
}
