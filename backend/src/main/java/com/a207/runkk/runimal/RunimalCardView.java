package com.a207.runkk.runimal;

import java.util.List;

/**
 * Runimal 유형 카드(자료사전 유형카드, FR-151): 유형명 + 아이콘 + 한 줄 설명 + 축별 우세 성향·비율.
 *
 * @param type 유형
 * @param title 유형명
 * @param icon 아이콘(동물 이름 텍스트)
 * @param description 한 줄 설명
 * @param provisional 임시 유형 카드인가
 * @param axes 축 막대 4개(동행·거리·코스·습관 순)
 */
public record RunimalCardView(
        RunimalType type,
        String title,
        String icon,
        String description,
        boolean provisional,
        List<AxisBar> axes) {

    public RunimalCardView {
        axes = List.copyOf(axes);
    }

    static RunimalCardView of(RunimalType type, AxisRatios ratios, boolean provisional) {
        List<AxisBar> bars =
                List.of(RunimalAxis.values()).stream()
                        .map(
                                axis -> {
                                    AxisLean lean = type.lean(axis);
                                    return new AxisBar(
                                            axis,
                                            lean,
                                            axis.label(lean),
                                            ratios.percentOf(axis, lean));
                                })
                        .toList();
        return new RunimalCardView(
                type, type.displayName(), type.icon(), type.description(), provisional, bars);
    }

    /**
     * 축 막대 하나("솔로 78%").
     *
     * @param axis 축
     * @param lean 우세 성향
     * @param label 성향 이름
     * @param percent 그 성향 비율(%)
     */
    public record AxisBar(RunimalAxis axis, AxisLean lean, String label, int percent) {}
}
