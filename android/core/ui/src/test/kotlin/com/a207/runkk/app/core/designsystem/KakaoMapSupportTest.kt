package com.a207.runkk.app.core.designsystem

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 측정 지도를 띄울지(C-005, FR-034, D-322 (3), D-340): 키·지도 라이브러리가 있고 실행 중 지도 오류가 없어야 한다. */
class KakaoMapSupportTest {
    @Test
    fun `키와 지도 라이브러리가 있으면 지도를 띄운다`() {
        assertTrue(kakaoMapEnabled("key", hasMapLibrary = true))
    }

    @Test
    fun `키가 없거나 라이브러리가 없으면 격자다`() {
        assertFalse(kakaoMapEnabled(" ", hasMapLibrary = true))
        assertFalse(kakaoMapEnabled("key", hasMapLibrary = false))
    }

    @Test
    fun `K16-2 실행 중 지도 오류가 나면 격자로 바꾼다`() {
        assertFalse(kakaoMapEnabled("key", hasMapLibrary = true, isMapFailed = true))
    }
}
