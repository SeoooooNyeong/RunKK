package com.a207.runkk.running;

/**
 * 위치 포인트의 신호 상태(자료사전 신호상태 = 정상 | 불안정, FR-033). UNSTABLE 포인트는 신호 공백 뒤 첫 포인트로, 그 앞 구간은 거리·속도 계산에서 뺀다.
 */
public enum SignalState {
    NORMAL,
    UNSTABLE
}
