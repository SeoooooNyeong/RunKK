package com.a207.runkk.runimal;

/**
 * 판정 결과(설계 6.1.8 RunimalJudge).
 *
 * @param type 유형
 * @param ratios 축별 A쪽 비율
 */
record Judged(RunimalType type, AxisRatios ratios) {}
