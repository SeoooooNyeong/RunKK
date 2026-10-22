package com.a207.runkk.running;

/**
 * 러닝 시작 무렵의 날씨(설계 6.1.3, FR-184).
 *
 * @param temperatureC 기온(섭씨)
 * @param sky 하늘 상태
 */
public record Weather(int temperatureC, SkyCondition sky) {

    public Weather {
        if (sky == null) {
            throw new IllegalArgumentException("하늘 상태가 비었다");
        }
    }
}
