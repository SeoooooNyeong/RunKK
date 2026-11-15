package com.a207.runkk.app.core.avatar

import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * 프로세스 공용 합성 결과 캐시(design-oo 05-30 5.4, D-737, DR-10). 남녀 × 동작마다 마지막 모습 하나만 두어
 * 상한이 4묶음이다. 키는 성별·동작·머리 동일성(생성 머리 참조, 내장 머리는 null)·[AvatarLookKey]다.
 *
 * - [prepareRiveAvatarLook]이 Rive 뷰 없이 채우고, [RiveAvatarView]는 합성 결과를 여기에도 넣는다.
 * - 뷰는 합성하기 전에 [hit]을 먼저 본다. 맞으면 합성 스레드를 거치지 않고 바로 텍스처를 넣는다(준비된 경우의 첫 표시).
 * - 맞지 않아도 같은 머리면 [seed]로 몸 분리 결과(피부 판정)를 다시 쓴다.
 *
 * 값은 읽기 전용이라 주 스레드와 합성 스레드가 함께 읽는다.
 */
internal class SharedToneCache {
    /** 자리 하나. 생성 머리는 약한 참조로만 잡는다(캐시가 지난 머리 레이어를 붙잡지 않는다). */
    class Entry(head: GeneratedHeadLayers?, val look: AvatarLookKey, val cache: AvatarToneCache) {
        private val headRef = head?.let(::WeakReference)

        /** 넣을 때의 생성 머리. 내장 머리였거나 그 머리가 사라졌으면 null. */
        val head: GeneratedHeadLayers? get() = headRef?.get()

        /** [other]와 같은 머리인가(참조 동일성). 사라진 생성 머리는 어떤 머리와도(내장 머리 null 포함) 맞지 않는다. */
        fun isFor(other: GeneratedHeadLayers?): Boolean = if (headRef ==
            null
        ) {
            other == null
        } else {
            headRef.get() === other && other != null
        }
    }

    private val entries = ConcurrentHashMap<Pair<AvatarGender, AvatarMotion>, Entry>()

    val size: Int get() = entries.size

    fun entry(gender: AvatarGender, motion: AvatarMotion): Entry? = entries[gender to motion]

    /** [motion] 자리를 [cache] 가운데 그 동작의 텍스처로 바꾼다. */
    fun put(
        gender: AvatarGender,
        motion: AvatarMotion,
        head: GeneratedHeadLayers?,
        look: AvatarLookKey,
        cache: AvatarToneCache
    ) {
        entries[gender to motion] = Entry(head, look, cache.only(motion))
    }

    /** [motions] 모두가 같은 머리·모습으로 들어 있으면 합친 캐시, 하나라도 아니면 null. */
    fun hit(
        gender: AvatarGender,
        motions: Set<AvatarMotion>,
        head: GeneratedHeadLayers?,
        look: AvatarLookKey
    ): AvatarToneCache? {
        if (motions.isEmpty()) return null
        val found = motions.map { motion ->
            entries[gender to motion]?.takeIf { it.isFor(head) && it.look == look } ?: return null
        }
        return found.fold(AvatarToneCache()) { merged, entry -> merged.merged(entry.cache) }
    }

    /** 같은 머리의 동작별 캐시를 합친 것(모습이 달라도 몸 분리·남은 텍스처를 다시 쓴다). 없으면 빈 캐시. */
    fun seed(gender: AvatarGender, head: GeneratedHeadLayers?): AvatarToneCache = AvatarMotion.entries
        .mapNotNull { motion -> entries[gender to motion]?.takeIf { it.isFor(head) } }
        .fold(AvatarToneCache()) { merged, entry -> merged.merged(entry.cache) }

    fun clear() = entries.clear()

    companion object {
        /** 앱 프로세스 공용. */
        val shared = SharedToneCache()
    }
}

/** 정면(서기) 플레이어가 쓰는 몸 자리(서기 의상 슬롯). 나머지는 달리기 자리다. */
private fun isStandAsset(name: String) = OutfitSlot.of(name) == OutfitSlot.STAND

/** [motion]이 쓰는 텍스처만 남긴 캐시. */
internal fun AvatarToneCache.only(motion: AvatarMotion): AvatarToneCache {
    val stand = motion == AvatarMotion.STAND
    return AvatarToneCache(
        skinBases = skinBases.filterKeys { isStandAsset(it) == stand },
        skinRenders = skinRenders.filterKeys { isStandAsset(it) == stand },
        face = face.takeIf { stand },
        frontOverlay = frontOverlay.takeIf { stand },
        runOverlay = runOverlay.takeIf { !stand }
    )
}

/** 두 캐시를 합친다. 겹치면 [other]가 이긴다. */
internal fun AvatarToneCache.merged(other: AvatarToneCache): AvatarToneCache = AvatarToneCache(
    skinBases = skinBases + other.skinBases,
    skinRenders = skinRenders + other.skinRenders,
    face = other.face ?: face,
    frontOverlay = other.frontOverlay ?: frontOverlay,
    runOverlay = other.runOverlay ?: runOverlay
)
