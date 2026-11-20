package com.a207.runkk.app.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class FaceUseTest {
    @Test
    fun `자리마다 층 크기 - 목록 256, 큰 프로필 512, 큰 아바타 1024`() {
        assertEquals(listOf(256, 512, 1024), FaceUse.entries.map { it.size() })
    }
}
