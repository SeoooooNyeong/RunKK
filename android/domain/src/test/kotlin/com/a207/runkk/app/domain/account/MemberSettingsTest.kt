package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Distance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemberSettingsTest {
    private fun settings(defaultDistance: String?) = MemberSettings(
        values = defaultDistance?.let { mapOf(MemberSettings.BATTLE_DEFAULT_DISTANCE to it) }.orEmpty(),
        consents = emptyMap()
    )

    @Test
    fun `대결 설정의 기본 대결 거리를 랜덤 대결 거리로 읽는다(FR-109)`() {
        assertEquals(Distance(1_000), settings("KM1").battleDefaultDistance)
        assertEquals(Distance(3_000), settings("KM3").battleDefaultDistance)
        assertEquals(Distance(5_000), settings("KM5").battleDefaultDistance)
        assertEquals(Distance(10_000), settings("KM10").battleDefaultDistance)
    }

    @Test
    fun `값이 없거나 랜덤 대결 거리가 아니면 null`() {
        assertNull(settings(null).battleDefaultDistance)
        assertNull(settings("HALF").battleDefaultDistance)
        assertNull(settings("FULL").battleDefaultDistance)
        assertNull(settings("5K").battleDefaultDistance)
    }
}
