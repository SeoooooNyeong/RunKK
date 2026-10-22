package com.a207.runkk.analysis;

/** 지표 변화 방향(설계 6.1.4 FitnessSummary.distanceTrend·paceTrend·frequencyTrend, 규칙 문구 선택용, FR-066). */
public enum Trend {
    IMPROVING,
    STEADY,
    DECLINING;

    /** 1회 평균 거리 변화가 이 값(미터)을 넘으면 향상·하락으로 본다. */
    static final long DISTANCE_STEP_METERS = 200;

    /** 평균 페이스 변화가 이 값(km당 초)을 넘으면 향상·하락으로 본다. */
    static final int PACE_STEP_SECONDS = 5;

    /** 주당 횟수 변화가 이 값을 넘으면 향상·하락으로 본다. */
    static final double FREQUENCY_STEP = 0.5;

    static Trend ofDistance(long changeMeters) {
        return of(changeMeters, DISTANCE_STEP_METERS);
    }

    /** 페이스는 줄어들수록(빨라질수록) 향상이다. */
    static Trend ofPace(int changeSeconds) {
        return of(-changeSeconds, PACE_STEP_SECONDS);
    }

    static Trend ofFrequency(double change) {
        return of(change, FREQUENCY_STEP);
    }

    private static Trend of(double change, double step) {
        if (change > step) {
            return IMPROVING;
        }
        return change < -step ? DECLINING : STEADY;
    }
}
