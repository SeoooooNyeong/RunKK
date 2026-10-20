package com.a207.runkk.policy;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.TagCategory;
import java.time.Duration;

/**
 * Runimal 축 판정 기준(설계 6.1.9).
 *
 * @param companionRatio 동행 축 비율
 * @param shortDistance 단거리 기준 거리
 * @param shortRatio 거리 축 비율
 * @param sameCourseRatio 코스 축 비율
 * @param habitWindow 습관 축 시간 창
 * @param habitRatio 습관 축 비율
 */
public record RunimalRule(
        double companionRatio,
        Distance shortDistance,
        double shortRatio,
        double sameCourseRatio,
        Duration habitWindow,
        double habitRatio) {

    /**
     * 축의 판정 비율. 축은 Runimal 4축과 같은 이름인 성향 태그 1~4분류(common.TagCategory)로 받는다. policy가
     * runimal.RunimalAxis를 import하면 12.2 의존 목록(policy → audit·common)을 벗어난다.
     */
    public double threshold(TagCategory axis) {
        return switch (axis) {
            case COMPANION -> companionRatio;
            case DISTANCE -> shortRatio;
            case COURSE -> sameCourseRatio;
            case HABIT -> habitRatio;
            default -> throw new IllegalArgumentException("Runimal 축이 아니다: " + axis);
        };
    }
}
