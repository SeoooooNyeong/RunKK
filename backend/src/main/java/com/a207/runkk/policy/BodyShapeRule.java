package com.a207.runkk.policy;

import java.util.List;

/**
 * 체형 단계 규칙(설계 6.1.9). 체형은 쉰 날로만 정한다(D-811): 쉰 날이 [restDaysBeforeDecline] 이하면 기본 몸, 넘으면 하루에 한 단계씩
 * 통통해진다.
 *
 * @param bands 최근 7일 러닝 횟수 구간 하한 4개. 체형 계산에 쓰지 않는다. 저장된 운영 기준값을 그대로 읽으려고 남긴다
 * @param restDaysBeforeDecline 이 일수를 넘게 쉬면 한 단계 통통해진다
 */
public record BodyShapeRule(List<Integer> bands, int restDaysBeforeDecline) {

    public BodyShapeRule {
        bands = List.copyOf(bands);
    }
}
