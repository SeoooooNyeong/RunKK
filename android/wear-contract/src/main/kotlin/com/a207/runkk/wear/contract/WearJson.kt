package com.a207.runkk.wear.contract

import kotlinx.serialization.json.Json

/**
 * 폰과 워치가 같은 설정으로 직렬화한다. 한쪽에만 항목이 더해져도 해독이 깨지지 않게 모르는 항목은 무시한다.
 * 기본값이 있는 항목(heartRateCollectionAllowed = false)도 늘 쓴다: 기본값을 모르는 옛 상대가 항목 누락으로 해독에 실패하지 않게.
 */
internal val wearJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
