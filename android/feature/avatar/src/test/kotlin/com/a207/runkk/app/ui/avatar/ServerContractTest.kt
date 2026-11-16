package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.designsystem.testing.ContractEnums
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 아바타·상점 값(기본 캐릭터·부위·체형·피부톤·사유 코드)을 서버 enum 이름 목록과 대조한다. */
class ServerContractTest {
    @Test
    fun `기본 캐릭터 id는 서버 DefaultCharacter와 같다`() {
        assertEquals(ContractEnums.names("DefaultCharacter"), DefaultCharacter.entries.map { it.name }.toSet())
    }

    @Test
    fun `착용 부위는 서버 ItemSlot과 같다`() {
        assertEquals(ContractEnums.names("ItemSlot"), ItemSlot.entries.map { it.name }.toSet())
    }

    @Test
    fun `다른 러너 모습으로 받는 부위(상의·하의·신발)는 서버 ItemSlot 안에 있고 렌더러가 그리는 부위와 같다(D-성-246)`() {
        val drawn = setOf(ItemSlot.TOP, ItemSlot.BOTTOM, ItemSlot.SHOES)
        assertTrue(ContractEnums.names("ItemSlot").containsAll(drawn.map { it.name }))
        // 모습 값(SlotRunnerLook)이 싣는 부위는 이 셋뿐이다: 각 부위에 아이템을 넣으면 그 부위 의상 번호만 바뀐다.
        val items = listOf(
            Shop.Item(1, ItemSlot.TOP, "상의", 0, true, true, "top_lime_singlet"),
            Shop.Item(2, ItemSlot.BOTTOM, "하의", 0, true, true, "bottom_running_shorts"),
            Shop.Item(3, ItemSlot.SHOES, "신발", 0, true, true, "shoes_basic_runner")
        )
        val outfit = outfitOf(mapOf(ItemSlot.TOP to 1L, ItemSlot.BOTTOM to 2L, ItemSlot.SHOES to 3L), items)
        assertEquals(listOf(1, 1, 1), listOf(outfit.top, outfit.bottom, outfit.shoes))
    }

    @Test
    fun `체형 단계는 서버 BodyShape 이름에서 읽힌다`() {
        ContractEnums.names("BodyShape").forEach { name ->
            assertNotNull(bodyStage(name), "서버 체형 $name 을 앱이 읽지 못한다")
        }
        assertEquals(ContractEnums.names("BodyShape").size, BODY_STAGES)
    }

    @Test
    fun `아바타 오류 문구의 사유 코드는 서버 ReasonCode이거나 셀카 실패 사유다`() {
        val known = ContractEnums.knownReasonCodes + ContractEnums.names("FailureReason")
        val unknown = AvatarErrorText.REJECTED.keys - known
        assertTrue(unknown.isEmpty(), "서버에 없는 사유 코드: $unknown")
    }

    @Test
    fun `서버 셀카 실패 사유마다 안내 문구가 있다`() {
        val missing = ContractEnums.names("FailureReason") - AvatarErrorText.REJECTED.keys
        assertTrue(missing.isEmpty(), "안내가 없는 셀카 실패 사유: $missing")
    }
}
