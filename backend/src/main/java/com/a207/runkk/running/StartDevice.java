package com.a207.runkk.running;

/**
 * 기록을 시작한 기기(사용자 결정 W23, D-378, FR-245). 워치 단독 측정 기록은 WATCH, 폰 측정(워치에서 시작을 눌러도 폰이 재는 동행 모드 포함,
 * FR-183)은 PHONE이다. 이 값을 보내지 않는 옛 앱의 기록은 PHONE이다.
 */
public enum StartDevice {
    PHONE,
    WATCH
}
