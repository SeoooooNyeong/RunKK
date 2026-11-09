package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.time.LocalDateTime;

/**
 * 크루런 작성 때 고를 수 있는 일시(피그마 F-12b·F-12d, W6): 한국 날짜로 오늘~오늘+14일(오늘 포함 15일), 분은 5의 배수, 초·나노초 0, 지금보다 뒤.
 * 앱에서 새로 쓰는 글에만 적용한다(요청 변환에서 부른다). 반복 회차(+7일)와 기존 글은 이 규칙을 거치지 않는다.
 */
public final class CrewRunSlot {

    /** 고를 수 있는 날 수(오늘 포함). */
    public static final int SELECTABLE_DAYS = 15;

    /** 시각 단위(분). */
    public static final int MINUTE_STEP = 5;

    private CrewRunSlot() {}

    /** 규칙에 맞지 않으면 CREW_RUN_INVALID. now는 서버 시간대(Asia/Seoul) 시각이다. */
    public static void requireSelectable(LocalDateTime scheduledAt, LocalDateTime now) {
        if (scheduledAt == null
                || !scheduledAt.isAfter(now)
                || scheduledAt.getSecond() != 0
                || scheduledAt.getNano() != 0
                || scheduledAt.getMinute() % MINUTE_STEP != 0
                || scheduledAt
                        .toLocalDate()
                        .isAfter(now.toLocalDate().plusDays(SELECTABLE_DAYS - 1L))) {
            throw new BusinessRuleException(ReasonCode.CREW_RUN_INVALID);
        }
    }
}
