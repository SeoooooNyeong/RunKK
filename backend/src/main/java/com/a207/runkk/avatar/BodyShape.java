package com.a207.runkk.avatar;

/** 체형 1~4단계(자료사전 체형, 상태도 2.4.12). 4단계가 기본 몸이고 단계가 낮을수록 통통하다([임시:D-성-179]). */
public enum BodyShape {
    STAGE1,
    STAGE2,
    STAGE3,
    STAGE4;

    /**
     * 화면·렌더러용 3단계 체형(W8·W9, S15P31A207-299·300): Rive body_shape 0 기본 몸(STAGE4), 50 조금
     * 통통(STAGE2·STAGE3), 100 가장 통통(STAGE1). 서버 계산·저장·알림은 4단계(FR-163) 그대로다([임시:D-성-179]).
     */
    public int variant() {
        return switch (this) {
            case STAGE4 -> 0;
            case STAGE2, STAGE3 -> 1;
            case STAGE1 -> 2;
        };
    }

    /** 한 단계 아래. 1단계는 그대로(FR-163). */
    public BodyShape lowerOneStep() {
        return this == STAGE1 ? STAGE1 : values()[ordinal() - 1];
    }
}
