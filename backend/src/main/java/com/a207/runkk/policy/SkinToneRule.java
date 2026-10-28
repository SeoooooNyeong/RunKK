package com.a207.runkk.policy;

import java.time.LocalTime;
import java.util.List;

/**
 * 시간대별 피부색 규칙(설계 6.1.9). 시간대 시작 시각 5개(기본 새벽 04, 아침 07, 낮 11, 저녁 17, 밤 21)가 24시간을 빈틈없이 나눈다.
 *
 * @param bands 시간대 시작 시각, 오름차순 5개
 */
public record SkinToneRule(List<LocalTime> bands) {

    public SkinToneRule {
        bands = List.copyOf(bands);
    }

    /**
     * 시각이 속한 시간대 번호(0~4). avatar.SkinTone의 DAWN·MORNING·DAYTIME·EVENING·NIGHT 순서와 같다. 첫 시작 시각 전은
     * 마지막 시간대(밤)다. policy가 avatar를 import하지 않도록 번호로 돌려준다.
     */
    public int toneOf(LocalTime t) {
        int band = bands.size() - 1;
        for (int i = 0; i < bands.size(); i++) {
            if (!t.isBefore(bands.get(i))) {
                band = i;
            }
        }
        return band;
    }
}
