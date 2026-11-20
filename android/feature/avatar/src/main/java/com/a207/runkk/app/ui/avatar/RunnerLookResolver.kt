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

    /** 다 만든 모습(최근 [MAX_RESOLVED]개). 같은 모습 값이면 같은 인스턴스를 준다(Rive 칸이 머리를 참조로 비교한다). */
    private val resolved = object : LinkedHashMap<SlotRunnerLook, RiveAvatarLook>(MAX_RESOLVED, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SlotRunnerLook, RiveAvatarLook>?): Boolean =
            size > MAX_RESOLVED
    }

    /**
     * 이미 만든 모습. 기다리지 않으므로 칸이 처음 구성될 때 바로 Rive 칸을 만든다(출발 전 데우기가 만든 상대 모습을 달리기 화면 첫
     * 구성에 쓴다, S15P31A207-335 6회차). 없으면 null이다.
     */
    fun peek(look: SlotRunnerLook): RiveAvatarLook? = synchronized(resolved) { resolved[look] }

    suspend fun resolve(look: SlotRunnerLook): RiveAvatarLook {
        peek(look)?.let { return it }
        val equipped = buildMap {
            look.topItemId?.let { put(ItemSlot.TOP, it) }
            look.bottomItemId?.let { put(ItemSlot.BOTTOM, it) }
            look.shoesItemId?.let { put(ItemSlot.SHOES, it) }
        }
        // 아무것도 입지 않았으면 상점 목록 없이 기본 의상이다.
        val items = if (equipped.isEmpty()) emptyList() else shopItems()
        val head = heads.look(look.faceKey).headLayers
        val rive = RiveAvatarLook(
            gender = if (look.isFemale) AvatarGender.FEMALE else AvatarGender.MALE,
            outfit = outfitOf(equipped, items),
            bodyShape = bodyShapeOf(look.bodyVariant),
            runningAmount = look.runAmount.coerceIn(0, MAX_RUN_AMOUNT),
            headLayers = head
        )
        // 상점 목록이나 셀카 머리를 받지 못해 기본값으로 채운 모습은 담지 않는다(다음에 다시 받는다).
        val isComplete = (equipped.isEmpty() || items.isNotEmpty()) && (!isSelfieHead(look.faceKey) || head != null)
        if (isComplete) synchronized(resolved) { resolved[look] = rive }
        return rive
    }

    private suspend fun shopItems(): List<Shop.Item> = mutex.withLock {
        items ?: avatars.shop(null).getOrNull()?.items?.also { items = it }.orEmpty()
    }

    private companion object {
        const val MAX_RUN_AMOUNT = 100
        const val MAX_RESOLVED = 16
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
