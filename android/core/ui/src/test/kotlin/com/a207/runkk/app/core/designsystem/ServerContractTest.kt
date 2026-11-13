package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.designsystem.testing.ContractEnums
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 서버로 가는 값(크루 태그 id·사유 코드)이 서버 enum 이름 목록(android/contract/enums, 서버에서 뽑은 것)과 같은지 대조한다.
 * 앱이 API 계약 전에 추정한 이름(SOLO·NEW_ROUTE 등)으로 가입이 400이던 결함의 재발 방지다.
 */
class ServerContractTest {
    private val tags = RunkkTags.categories.flatMap { it.tags }

    @Test
    fun `크루 태그 id는 서버 CrewTag 이름이고 8개가 모두 있다`() {
        assertEquals(ContractEnums.names("CrewTag"), tags.map { it.id }.toSet(), "앱 태그 id와 서버 CrewTag가 다르다")
        assertEquals(8, tags.size)
    }

    @Test
    fun `평일 줄과 주말 줄은 서버 이름의 요일 묶음과 같다`() {
        val (weekday, weekend) = RunkkTags.categories
        assertTrue(weekday.tags.all { it.id.startsWith("WEEKDAY_") && !it.isWeekend })
        assertTrue(weekend.tags.all { it.id.startsWith("WEEKEND_") && it.isWeekend })
    }

    @Test
    fun `공용 오류 문구의 사유 코드는 서버 ReasonCode이거나 알려진 앱 코드다`() {
        val unknown = RunkkErrorText.REJECTED.keys - ContractEnums.knownReasonCodes
        assertTrue(unknown.isEmpty(), "서버에 없는 사유 코드: $unknown")
    }
}
