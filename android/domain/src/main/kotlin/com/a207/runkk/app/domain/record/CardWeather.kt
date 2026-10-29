package com.a207.runkk.app.domain.record

/** 카드 앞면 날씨(D-235): 기온(°C)과 하늘 상태. 하늘 상태는 서버 SkyCondition 이름이고 모르는 값이면 null이다. */
data class CardWeather(val temperatureC: Int?, val sky: Sky?) {
    /** 서버 running.SkyCondition과 같은 이름. */
    enum class Sky {
        CLEAR,
        CLOUDY,
        OVERCAST,
        RAIN,
        SNOW
    }
}
