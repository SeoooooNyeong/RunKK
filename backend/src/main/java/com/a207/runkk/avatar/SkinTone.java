package com.a207.runkk.avatar;

/** 시간대별 피부색 5톤(자료사전 피부색, FR-164). 상수 순서가 policy.SkinToneRule.toneOf의 시간대 번호와 같다. */
public enum SkinTone {
    DAWN,
    MORNING,
    DAYTIME,
    EVENING,
    NIGHT;

    /** SkinToneRule.toneOf가 돌려준 0~4 시간대 번호의 톤. */
    public static SkinTone ofBand(int band) {
        if (band < 0 || band >= values().length) {
            throw new IllegalArgumentException("시간대 번호는 0~4: " + band);
        }
        return values()[band];
    }
}
