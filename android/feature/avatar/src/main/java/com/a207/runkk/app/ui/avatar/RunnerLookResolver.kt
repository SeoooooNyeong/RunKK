package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.avatar.AvatarBodyShape
import com.a207.runkk.app.core.avatar.AvatarGender
import com.a207.runkk.app.core.avatar.AvatarOutfit
import com.a207.runkk.app.core.avatar.RiveAvatarLook
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.core.navigation.SlotRunnerLook
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 다른 러너의 모습 값([SlotRunnerLook])을 Rive가 그릴 수 있는 렌더러 값으로 만든다([임시:D-성-260]). 내 아바타와 같은 재료를 쓴다: 의상은
 * 상점 목록(`avatars.shop(null)`, 한 번 받아 둔다)과 [outfitOf], 체형은 [bodyShapeOf], 머리는 [SelfieHeads.look]이다. 새 서버 호출은 없다.
 * 상점 목록을 받지 못하면 기본 의상으로 그리고 다음에 다시 받는다. 머리를 받지 못하면 성별 기본 머리다.
 */
@Singleton
class RunnerLookResolver @Inject constructor(private val avatars: AvatarRepository, private val heads: SelfieHeads) {
    private val mutex = Mutex()
    private var items: List<Shop.Item>? = null

    suspend fun resolve(look: SlotRunnerLook): RiveAvatarLook {
        val equipped = buildMap {
            look.topItemId?.let { put(ItemSlot.TOP, it) }
            look.bottomItemId?.let { put(ItemSlot.BOTTOM, it) }
            look.shoesItemId?.let { put(ItemSlot.SHOES, it) }
        }
        return RiveAvatarLook(
            gender = if (look.isFemale) AvatarGender.FEMALE else AvatarGender.MALE,
            // 아무것도 입지 않았으면 상점 목록 없이 기본 의상이다.
            outfit = outfitOf(equipped, if (equipped.isEmpty()) emptyList() else shopItems()),
            bodyShape = bodyShapeOf(look.bodyVariant),
            runningAmount = look.runAmount.coerceIn(0, MAX_RUN_AMOUNT),
            headLayers = heads.look(look.faceKey).headLayers
        )
    }

    private suspend fun shopItems(): List<Shop.Item> = mutex.withLock {
        items ?: avatars.shop(null).getOrNull()?.items?.also { items = it }.orEmpty()
    }

    private companion object {
        const val MAX_RUN_AMOUNT = 100
    }
}

/** 표시 성별의 기본 모습(기본 의상·기본 체형·러닝량 0·기본 머리): 모습을 받지 못한 러너([임시:D-성-253]). */
internal fun defaultRiveLook(isFemale: Boolean?): RiveAvatarLook = RiveAvatarLook(
    gender = if (isFemale == true) AvatarGender.FEMALE else AvatarGender.MALE,
    outfit = AvatarOutfit(),
    bodyShape = AvatarBodyShape.BASE,
    runningAmount = 0,
    headLayers = null
)
