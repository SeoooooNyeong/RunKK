package com.a207.runkk.runbody;

import java.util.Collection;

/**
 * 러닝 점수(러닝 선택 G-30 "러닝 점수 78"). ai 결과에는 종합 점수가 없어(schemas.py SessionResultOut, axes 다섯 축의 score만
 * 있다) 점수가 있는 축들의 평균을 반올림한다. 미산출 축(score null, 예: 케이던스 없는 form)은 빼고 센다. 오각형에서 미산출 축을 0점으로 그리지 않는 것(앱
 * 계약 1.1)과 같은 이유다. 점수가 있는 축이 하나도 없으면 점수도 없다.
 */
public final class RunBodyScore {

    public static final int MIN = 0;
    public static final int MAX = 100;

    private RunBodyScore() {}

    /** 축 점수들의 평균(반올림, 0.5는 올림). null은 뺀다. 모두 null이거나 비었으면 null. */
    public static Integer overall(Collection<Integer> axisScores) {
        long sum = 0;
        int count = 0;
        for (Integer score : axisScores) {
            if (score == null) {
                continue;
            }
            if (score < MIN || score > MAX) {
                throw new IllegalArgumentException("축 점수는 0~100: " + score);
            }
            sum += score;
            count++;
        }
        if (count == 0) {
            return null;
        }
        return (int) ((sum * 2 + count) / (2L * count));
    }

    /** 이전 러닝 점수 대비 변화. 둘 중 하나라도 없으면 null. */
    public static Integer change(Integer score, Integer previous) {
        if (score == null || previous == null) {
            return null;
        }
        return score - previous;
    }
}
