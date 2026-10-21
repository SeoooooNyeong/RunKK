package com.a207.runkk.runimal;

import com.a207.runkk.policy.RunimalRule;
import java.util.EnumMap;
import java.util.Map;

/** 축마다 A쪽 비율 0~1(설계 6.1.8). 카드 막대 "솔로 78%"에 쓴다(FR-151). */
public final class AxisRatios {

    private static final double PERCENT = 100.0;

    private final Map<RunimalAxis, Double> ratios;

    public AxisRatios(Map<RunimalAxis, Double> ratios) {
        for (RunimalAxis axis : RunimalAxis.values()) {
            Double value = ratios.get(axis);
            if (value == null || value < 0 || value > 1) {
                throw new IllegalArgumentException("축 비율은 4축 모두 0~1: " + axis + "=" + value);
            }
        }
        this.ratios = Map.copyOf(new EnumMap<>(ratios));
    }

    public double ratioOfA(RunimalAxis a) {
        return ratios.get(a);
    }

    public AxisLean dominant(RunimalAxis a, RunimalRule rule) {
        return a.lean(ratioOfA(a), rule);
    }

    /** 우세한 쪽 비율(%). */
    public int percentOfDominant(RunimalAxis a) {
        double ratio = ratioOfA(a);
        return (int) Math.round(Math.max(ratio, 1 - ratio) * PERCENT);
    }

    /** 그 성향 쪽 비율(%). A면 A쪽 비율, B면 1 − A쪽 비율. */
    public int percentOf(RunimalAxis a, AxisLean lean) {
        double ratio = lean == AxisLean.A ? ratioOfA(a) : 1 - ratioOfA(a);
        return (int) Math.round(ratio * PERCENT);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AxisRatios other && ratios.equals(other.ratios);
    }

    @Override
    public int hashCode() {
        return ratios.hashCode();
    }

    @Override
    public String toString() {
        return "AxisRatios" + ratios;
    }
}
