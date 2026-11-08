package com.a207.runkk.reward;

/**
 * 리워드 적립 사유(자료사전 적립사유, 설계 1.2.8). 협동 레이스 목표 달성 적립은 목표가 없어져 없다(N-15). EVENT는 이벤트 포인트(코치 승리 등,
 * S15P31A207-275)이고 하루 상한을 받지 않으며 그날 합계에도 넣지 않는다.
 */
public enum AccrualReason {
    VALID_RUN,
    BATTLE_WIN,
    EVENT
}
