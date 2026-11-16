package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.avatar.AvatarBodyShape
import com.a207.runkk.app.core.avatar.AvatarGender
import com.a207.runkk.app.core.avatar.AvatarOutfit
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.SlotRunnerLook
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/** 다른 러너 모습 값을 렌더러 값으로 만든다([임시:D-성-260]): 내 아바타와 같은 재료(outfitOf, bodyShapeOf, 셀카 머리)를 쓴다. */
class RunnerLookResolverTest {
    private val base = FakeAvatars()
    private var shopCalls = 0
    private var shopFails = false
    private val repo = object : AvatarRepository by base {
        override suspend fun shop(slot: ItemSlot?): Outcome<Shop> {
            shopCalls++
            if (shopFails) return Outcome.Failure(AppError.Network)
            return Outcome.Success(
                Shop(
                    listOf(
                        Shop.Item(12, ItemSlot.TOP, "크루 티", 300, true, true, "top_crew_tee"),
                        Shop.Item(21, ItemSlot.BOTTOM, "레깅스", 300, true, true, "bottom_leggings"),
                        Shop.Item(31, ItemSlot.SHOES, "카본 레이서", 300, true, true, "shoes_carbon_racer")
                    ),
                    0
                )
            )
        }
    }
    private val resolver = RunnerLookResolver(repo, headsOf(repo))

    @Test
    fun `의상과 체형이 outfitOf와 bodyShapeOf로 옮겨진다`() = runTest {
        val look = SlotRunnerLook(
            isFemale = true,
            topItemId = 12,
            bottomItemId = 21,
            shoesItemId = 31,
            bodyVariant = 1,
            runAmount = 42
        )

        val rive = resolver.resolve(look)

        assertEquals(AvatarGender.FEMALE, rive.gender)
        assertEquals(AvatarOutfit(top = 2, bottom = 2, shoes = 3), rive.outfit)
        assertEquals(
            outfitOf(
                mapOf(ItemSlot.TOP to 12L, ItemSlot.BOTTOM to 21L, ItemSlot.SHOES to 31L),
                repo.shop(null).items()
            ),
            rive.outfit
        )
        assertEquals(AvatarBodyShape.CHANGE_1, rive.bodyShape)
        assertEquals(bodyShapeOf(1), rive.bodyShape)
        assertEquals(42, rive.runningAmount)
        assertNull(rive.headLayers, "얼굴 키가 없으면 성별 기본 머리다")
    }

    @Test
    fun `상점 목록은 한 번만 받고 안 입은 러너는 받지 않는다`() = runTest {
        resolver.resolve(SlotRunnerLook(isFemale = false))
        assertEquals(0, shopCalls, "아무것도 입지 않았으면 기본 의상이다")

        resolver.resolve(SlotRunnerLook(isFemale = false, topItemId = 12))
        resolver.resolve(SlotRunnerLook(isFemale = true, shoesItemId = 31))

        assertEquals(1, shopCalls)
    }

    @Test
    fun `상점 목록을 받지 못하면 기본 의상으로 그리고 다음에 다시 받는다`() = runTest {
        shopFails = true
        val failed = resolver.resolve(SlotRunnerLook(isFemale = false, topItemId = 12))
        shopFails = false
        val recovered = resolver.resolve(SlotRunnerLook(isFemale = false, topItemId = 12))

        assertEquals(AvatarOutfit(), failed.outfit)
        assertEquals(AvatarOutfit(top = 2), recovered.outfit)
        assertEquals(2, shopCalls)
    }

    @Test
    fun `모르는 아이템과 범위 밖 값은 기본값으로 둔다`() = runTest {
        val rive = resolver.resolve(SlotRunnerLook(isFemale = false, topItemId = 999, bodyVariant = 9, runAmount = 250))

        assertEquals(AvatarGender.MALE, rive.gender)
        assertEquals(AvatarOutfit(), rive.outfit)
        assertEquals(AvatarBodyShape.BASE, rive.bodyShape)
        assertEquals(100, rive.runningAmount)
    }

    @Test
    fun `모습을 받지 못한 러너는 표시 성별의 기본 모습이다`() {
        val female = defaultRiveLook(isFemale = true)
        val unknown = defaultRiveLook(isFemale = null)

        assertEquals(AvatarGender.FEMALE, female.gender)
        assertEquals(AvatarGender.MALE, unknown.gender)
        assertEquals(AvatarOutfit(), female.outfit)
        assertEquals(AvatarBodyShape.BASE, female.bodyShape)
        assertEquals(0, female.runningAmount)
    }

    private fun Outcome<Shop>.items(): List<Shop.Item> = (this as Outcome.Success).value.items
}
