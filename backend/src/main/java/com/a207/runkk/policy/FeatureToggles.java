package com.a207.runkk.policy;

/**
 * 축소 운영 스위치(설계 6.1.9, plan.md 3.1.1)와 2차 배포 기능 공개 스위치.
 *
 * @param selfieEnabled 셀카 생성 사용
 * @param aiCommentEnabled AI 코멘트 사용
 * @param crewReleased 크루 공개(CREW_RELEASED, 기본 false)
 * @param avatarReleased 아바타 꾸미기 공개(AVATAR_RELEASED, 기본 false, D138)
 * @param runbodyEnabled 런바디 ai 분석 사용(RUNBODY_ENABLED, 기본 false, 05-19 D-440)
 */
public record FeatureToggles(
        boolean selfieEnabled,
        boolean aiCommentEnabled,
        boolean crewReleased,
        boolean avatarReleased,
        boolean runbodyEnabled) {

    /** 크루·아바타 공개 전 값. */
    public FeatureToggles(boolean selfieEnabled, boolean aiCommentEnabled) {
        this(selfieEnabled, aiCommentEnabled, false, false, false);
    }

    /** 아바타 공개 전 값. */
    public FeatureToggles(boolean selfieEnabled, boolean aiCommentEnabled, boolean crewReleased) {
        this(selfieEnabled, aiCommentEnabled, crewReleased, false, false);
    }

    /** 런바디 공개 전 값. */
    public FeatureToggles(
            boolean selfieEnabled,
            boolean aiCommentEnabled,
            boolean crewReleased,
            boolean avatarReleased) {
        this(selfieEnabled, aiCommentEnabled, crewReleased, avatarReleased, false);
    }
}
