package com.a207.runkk.common;

import java.time.ZoneId;

/** 서버 시간대의 단일 출처(한국 시간). 시계 빈(RunkkApplication)·스케줄 설정·응답 시각 변환이 모두 이 값을 쓴다. */
public final class ServerZone {

    /** 서버 시간대(Asia/Seoul). */
    public static final ZoneId ID = ZoneId.of("Asia/Seoul");

    private ServerZone() {}
}
