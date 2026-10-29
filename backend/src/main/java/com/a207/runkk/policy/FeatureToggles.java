package com.a207.runkk.policy;

/**
 * 축소 운영 스위치 두 개(설계 6.1.9, plan.md 3.1.1).
 *
 * @param selfieEnabled 셀카 생성 사용
 * @param aiCommentEnabled AI 코멘트 사용
 */
public record FeatureToggles(
        boolean selfieEnabled, boolean aiCommentEnabled, boolean crewReleased) {

    /** 크루 공개 전(crewReleased 거짓) 값. */
    public FeatureToggles(boolean selfieEnabled, boolean aiCommentEnabled) {
        this(selfieEnabled, aiCommentEnabled, false);
    }
}
