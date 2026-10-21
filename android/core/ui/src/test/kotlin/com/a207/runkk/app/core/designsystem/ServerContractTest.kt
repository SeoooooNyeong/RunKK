package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.designsystem.testing.ContractEnums
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 서버로 가는 값(성향 태그 id·사유 코드)이 서버 enum 이름 목록(android/contract/enums, 서버에서 뽑은 것)과 같은지 대조한다.
 * 앱이 API 계약 전에 추정한 이름(SOLO·NEW_ROUTE 등)으로 가입이 400이던 결함의 재발 방지다.
 */
class ServerContractTest {
    private val tags = RunkkTags.categories.flatMap { it.tags }

    @Test
    fun `성향 태그 id는 서버 PersonalityTag 이름이고 19개가 모두 있다`() {
        val server = ContractEnums.names("PersonalityTag")
        assertEquals(server, tags.map { it.id }.toSet(), "앱 태그 id와 서버 PersonalityTag가 다르다")
        assertEquals(19, tags.size)
    }

    @Test
    fun `성향 태그 분류는 서버 분류와 같고 크루 분위기만 크루 전용이다`() {
        val serverCategory = ContractEnums.lines("PersonalityTag.categories.txt")
            .associate { it.substringBefore(' ') to it.substringAfter(' ') }
        val appCategories = RunkkTags.categories.map { category ->
            category.tags.map { serverCategory.getValue(it.id) }.toSet().also {
                assertEquals(1, it.size, "분류 ${category.nameRes}: 한 분류에 서버 분류가 섞였다 $it")
            }.single()
        }
        assertEquals(serverCategory.values.toSet(), appCategories.toSet())
        assertEquals(7, appCategories.distinct().size)
        RunkkTags.categories.forEach { category ->
            assertEquals(
                category.isCrewOnly,
                category.tags.all { serverCategory.getValue(it.id) == "CREW_MOOD" },
                "분류 ${category.nameRes}: 크루 전용 표시가 서버 분류(CREW_MOOD)와 다르다"
            )
        }
    }

    @Test
    fun `공용 오류 문구의 사유 코드는 서버 ReasonCode이거나 알려진 앱 코드다`() {
        val unknown = RunkkErrorText.REJECTED.keys - ContractEnums.knownReasonCodes
        assertTrue(unknown.isEmpty(), "서버에 없는 사유 코드: $unknown")
    }
}
