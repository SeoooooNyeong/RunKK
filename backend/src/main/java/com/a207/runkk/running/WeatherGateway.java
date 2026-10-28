package com.a207.runkk.running;

import com.a207.runkk.common.GeoPoint;
import java.time.LocalDateTime;
import java.util.Optional;

/** 외부 날씨 조회 포트(설계 6.1.3, FR-184). 구현은 infra.integration.weather.KmaWeatherGateway. */
public interface WeatherGateway {

    /** 좌표는 소수 둘째 자리로 낮춘 값이다(NFR-038). 3초 제한, 실패면 비어 있음. */
    Optional<Weather> lookup(GeoPoint coarsePoint, LocalDateTime at);
}
