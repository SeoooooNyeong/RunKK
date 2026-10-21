package com.a207.runkk.avatar;

/** 체형 1~4단계(자료사전 체형, 상태도 2.4.12). 상수 순서가 policy.BodyShapeRule.stageFor의 단계 번호와 같다. */
public enum BodyShape {
    STAGE1,
    STAGE2,
    STAGE3,
    STAGE4;

    /** BodyShapeRule.stageFor가 돌려준 1~4 단계 번호의 체형. */
    public static BodyShape ofStage(int stage) {
        if (stage < 1 || stage > values().length) {
            throw new IllegalArgumentException("체형 단계는 1~4: " + stage);
        }
        return values()[stage - 1];
    }

    /** 한 단계 아래. 1단계는 그대로(FR-163). */
    public BodyShape lowerOneStep() {
        return this == STAGE1 ? STAGE1 : values()[ordinal() - 1];
    }
}
