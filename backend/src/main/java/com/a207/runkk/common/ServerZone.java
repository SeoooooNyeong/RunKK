package com.a207.runkk.common;

import java.time.ZoneId;
import java.util.TimeZone;

/** 서버 시간대의 단일 출처(한국 시간). 시계 빈(RunkkApplication)·스케줄 설정·응답 시각 변환이 모두 이 값을 쓴다. */
public final class ServerZone {

    /** 서버 시간대(Asia/Seoul). */
    public static final ZoneId ID = ZoneId.of("Asia/Seoul");

    private ServerZone() {}

    /**
     * JVM 기본 시간대를 서버 시간대로 맞춘다(DF-073). 시작점(RunkkApplication.main)이 스프링을 띄우기 전에 부른다. JDBC 드라이버는 연결 때
     * JVM 기본 시간대를 DB 세션 시간대로 보내므로, 이 값이 UTC면 SQL의 now()와 LOCALTIMESTAMP, Flyway 시드의 now()가 UTC 벽시계
     * 값이 된다. 컨테이너 설정(TZ, -Duser.timezone)이 빠져도 서버 시각, DB 세션, 로그가 같은 한국 시각을 쓰게 코드에서도 정한다.
     */
    public static void useAsJvmDefault() {
        TimeZone.setDefault(TimeZone.getTimeZone(ID));
    }
}
