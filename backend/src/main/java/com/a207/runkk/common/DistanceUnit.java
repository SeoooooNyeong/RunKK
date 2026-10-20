package com.a207.runkk.common;

/** 거리 표시 단위(FR-009). 저장 값은 언제나 미터다. */
public enum DistanceUnit {
    KM(1000.0, "km"),
    MI(1609.344, "mi");

    private final double metersPerUnit;
    private final String symbol;

    DistanceUnit(double metersPerUnit, String symbol) {
        this.metersPerUnit = metersPerUnit;
        this.symbol = symbol;
    }

    /** 거리를 이 단위의 값으로 바꾼다. */
    public double convert(Distance distance) {
        return distance.meters() / metersPerUnit;
    }

    double metersPerUnit() {
        return metersPerUnit;
    }

    String symbol() {
        return symbol;
    }
}
