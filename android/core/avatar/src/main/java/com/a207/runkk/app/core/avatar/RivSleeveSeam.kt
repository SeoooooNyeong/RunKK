package com.a207.runkk.app.core.avatar

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * 남자 서기 긴소매 메시(Image 「Long sleeve wardrobe top」)의 팔·몸통 이음매를 닫는다(판정 round3 소매 이음새 틈). 승인 riv는 팔 띠와
 * 몸통 띠가 이음매 열에 같은 (u, v)·무게의 꼭짓점을 따로 두는데, 겨드랑이 아래 행은 몸통 쪽 꼭짓점이 0.1~1.2 단위 안쪽에 있어(바탕 자세와
 * 체형 애니메이션 x 키 모두) 두 띠 사이로 배경이 가는 세로 줄로 비쳤다. 몸통 쪽 값([TARGETS])을 짝인 팔 쪽 값([SOURCES])으로 바꾼다.
 * 무게가 같아 어느 프레임·체형에서도 함께 움직인다. 파일은 승인본(SHA 시험) 그대로 두고 읽은 바이트에서 바꾼다([RivHipConnection]과 같은
 * 방식). 위치 표는 파이프라인 `outfit_pipeline/seam.py`의 `copy_pairs`가 riv에서 찾은 값이고(파이썬 시험이 이 표와 맞는지 본다), 값이
 * 승인 범위(절댓값 [MIN_ABS]~[MAX_ABS], 짝 차 [MAX_GAP] 이하)가 아니면 그 파일은 그대로 둔다.
 */
internal object RivSleeveSeam {
    private const val MIN_ABS = 25f
    private const val MAX_ABS = 40f
    private const val MAX_GAP = 1.5f

    /** avatar_male_idle.riv 팔 쪽 꼭짓점 값 위치(바이트, float32 리틀엔디언: 바탕 x·y, 체형 base·medium·fuller x 키). */
    internal val SOURCES = intArrayOf(
        4064059, 4064064, 4647406, 4992512, 5337618, 4064285, 4064290, 4647501,
        4992607, 5337713, 4064459, 4064464, 4647596, 4992702, 5337808, 4064685,
        4064690, 4647691, 4992797, 5337903, 4064859, 4064864, 4647786, 4992892,
        5337998, 4065085, 4065090, 4647881, 4992987, 5338093, 4065259, 4065264,
        4647976, 4993082, 5338188, 4065485, 4065490, 4648071, 4993177, 5338283,
        4065659, 4065664, 4648166, 4993272, 5338378, 4065885, 4065890, 4648261,
        4993367, 5338473, 4066059, 4066064, 4648356, 4993462, 5338568, 4066285,
        4066290, 4648451, 4993557, 5338663, 4066459, 4066464, 4648546, 4993652,
        5338758, 4066685, 4066690, 4648641, 4993747, 5338853, 4066859, 4066864,
        4648736, 4993842, 5338948, 4067085, 4067090, 4648831, 4993937, 5339043,
        4067259, 4067264, 4648926, 4994032, 5339138, 4067482, 4067487, 4649021,
        4994127, 5339233, 4067656, 4067661, 4649116, 4994222, 5339328, 4067879,
        4067884, 4649211, 4994317, 5339423
    )

    /** 같은 순서의 몸통 쪽 꼭짓점 값 위치(이 값을 [SOURCES] 값으로 바꾼다). */
    internal val TARGETS = intArrayOf(
        4064095, 4064100, 4647425, 4992531, 5337637, 4064249, 4064254, 4647482,
        4992588, 5337694, 4064495, 4064500, 4647615, 4992721, 5337827, 4064649,
        4064654, 4647672, 4992778, 5337884, 4064895, 4064900, 4647805, 4992911,
        5338017, 4065049, 4065054, 4647862, 4992968, 5338074, 4065295, 4065300,
        4647995, 4993101, 5338207, 4065449, 4065454, 4648052, 4993158, 5338264,
        4065695, 4065700, 4648185, 4993291, 5338397, 4065849, 4065854, 4648242,
        4993348, 5338454, 4066095, 4066100, 4648375, 4993481, 5338587, 4066249,
        4066254, 4648432, 4993538, 5338644, 4066495, 4066500, 4648565, 4993671,
        5338777, 4066649, 4066654, 4648622, 4993728, 5338834, 4066895, 4066900,
        4648755, 4993861, 5338967, 4067049, 4067054, 4648812, 4993918, 5339024,
        4067295, 4067300, 4648945, 4994051, 5339157, 4067446, 4067451, 4649002,
        4994108, 5339214, 4067692, 4067697, 4649135, 4994241, 5339347, 4067843,
        4067848, 4649192, 4994298, 5339404
    )

    /**
     * 반팔·민소매 표면 메시(Image 「Existing wardrobe top」) 몸통 띠 바깥 끝 꼭짓점의 체형 x 키 위치(판정 round4: base·singlet 남자 체형 100
     * 오른쪽 겨드랑이 아래 틈). 체형 키에서 팔 띠와의 겹침이 바탕 자세보다 줄어 벌어진 곳만 [OVERLAP_VALUES](팔 쪽 키 + 바탕 겹침)로
     * 바꾼다. 표는 파이프라인 `seam.overlap_patch`가 riv에서 찾은 값이다.
     */
    internal val OVERLAP_TARGETS = intArrayOf(
        4977502, 5322608, 4977559, 5322665, 4977692, 5322798, 4977749, 5322855,
        4977882, 5322988, 4977939, 5323045, 4978072, 5323178, 4978129, 5323235,
        4978262, 5323368, 4978319, 5323425, 4978452, 5323558, 4978509, 5323615
    )

    internal val OVERLAP_VALUES = floatArrayOf(
        -29.55476951599121f, -32.344032287597656f, 30.303617477416992f, 33.11725997924805f,
        -29.78829002380371f, -32.66720962524414f, 30.68305206298828f, 33.58557891845703f,
        -30.270648956298828f, -33.21796417236328f, 31.080997467041016f, 34.05262756347656f,
        -30.812795639038086f, -33.79150390625f, 31.64537811279297f, 34.645835876464844f,
        -31.369779586791992f, -34.397804260253906f, 32.167083740234375f, 35.21597671508789f,
        -32.01605224609375f, -35.093505859375f, 32.687557220458984f, 35.787498474121094f
    )

    /** [riv]가 승인 남자 서기 riv면 이음매 몸통 쪽 꼭짓점 값을 팔 쪽 값으로 바꾸고 반팔 표면 겹침을 지킨다(바로 고친다). 아니면 그대로 둔다. */
    fun closeInPlace(riv: ByteArray) {
        if (!matches(riv) || !matchesOverlap(riv)) return
        val buffer = ByteBuffer.wrap(riv).order(ByteOrder.LITTLE_ENDIAN)
        SOURCES.indices.forEach { buffer.putFloat(TARGETS[it], buffer.getFloat(SOURCES[it])) }
        OVERLAP_TARGETS.indices.forEach { buffer.putFloat(OVERLAP_TARGETS[it], OVERLAP_VALUES[it]) }
    }

    private fun matchesOverlap(riv: ByteArray): Boolean {
        if (OVERLAP_TARGETS.max() + 4 > riv.size) return false
        val buffer = ByteBuffer.wrap(riv).order(ByteOrder.LITTLE_ENDIAN)
        return OVERLAP_TARGETS.indices.all {
            val current = buffer.getFloat(OVERLAP_TARGETS[it])
            abs(current) in MIN_ABS..MAX_ABS && abs(current - OVERLAP_VALUES[it]) <= MAX_GAP
        }
    }

    private fun matches(riv: ByteArray): Boolean {
        if (maxOf(SOURCES.max(), TARGETS.max()) + 4 > riv.size) return false
        val buffer = ByteBuffer.wrap(riv).order(ByteOrder.LITTLE_ENDIAN)
        return SOURCES.indices.all {
            val source = buffer.getFloat(SOURCES[it])
            val target = buffer.getFloat(TARGETS[it])
            abs(source) in MIN_ABS..MAX_ABS && abs(target) in MIN_ABS..MAX_ABS && abs(source - target) <= MAX_GAP
        }
    }
}
