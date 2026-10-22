package com.a207.runkk.policy;

import java.util.List;

/**
 * 체형 단계 규칙(설계 6.1.9). 최근 7일 러닝 횟수 구간 하한 4개(기본 0 / 1~2 / 3~4 / 5+).
 *
 * @param bands 구간 하한, 오름차순 4개
 * @param restDaysBeforeDecline 이 일수만큼 쉬면 한 단계 내려간다
 */
public record BodyShapeRule(List<Integer> bands, int restDaysBeforeDecline) {

    public BodyShapeRule {
        bands = List.copyOf(bands);
    }

    /**
     * 최근 7일 러닝 횟수에 맞는 체형 단계 번호(1~4). avatar.BodyShape의 단계 순서와 같다. policy가 avatar를 import하면 12.2 의존
     * 목록(policy → audit·common)을 벗어나므로 번호로 돌려준다.
     */
    public int stageFor(int runsIn7Days) {
        if (runsIn7Days < 0) {
            throw new IllegalArgumentException("러닝 횟수는 0 이상: " + runsIn7Days);
        }
        return (int) bands.stream().filter(lower -> runsIn7Days >= lower).count();
    }
}
