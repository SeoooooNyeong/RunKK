package com.a207.runkk.watch.ui

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * 메인 캐러셀 카드 한 장의 모양(피그마 Q1~Q4 Smart Animate). 카드가 가운데에서 몇 칸 떨어졌는지(소수, 위가 음수)로 정한다.
 * [centerY]·[width]·[height]는 화면 지름 비율, [emphasis]는 가운데 카드다운 정도(1이면 라임 바탕·큰 이름·설명, 0이면 작은 카드),
 * [alpha]는 카드 흐림, [labelAlpha]는 이름 글자 흐림(바깥 장식 카드는 이름이 없다)이다. 칸 사이는 곧게 이어 붙인다.
 */
internal data class CarouselSlot(
    val centerY: Float,
    val width: Float,
    val height: Float,
    val emphasis: Float,
    val alpha: Float,
    val labelAlpha: Float
)

/** 가운데에서 [distance]칸 떨어진 카드 모양. 세 칸 이상은 화면 밖이고 보이지 않는다. */
internal fun carouselSlot(distance: Float): CarouselSlot {
    val far = abs(distance).coerceAtMost(SLOTS.lastIndex.toFloat())
    val index = far.toInt().coerceAtMost(SLOTS.lastIndex - 1)
    val t = far - index
    val from = SLOTS[index]
    val to = SLOTS[index + 1]
    return CarouselSlot(
        centerY = sign(distance) * lerp(from.centerY, to.centerY, t),
        width = lerp(from.width, to.width, t),
        height = lerp(from.height, to.height, t),
        emphasis = lerp(from.emphasis, to.emphasis, t),
        alpha = lerp(from.alpha, to.alpha, t),
        labelAlpha = lerp(from.labelAlpha, to.labelAlpha, t)
    )
}

/** 끝없이 늘어나는 카드 번호 → 모드(순환: -1은 셀럽런, 4는 자유런). */
internal fun carouselMode(index: Int): WatchMode = WatchMode.entries[Math.floorMod(index, WatchMode.entries.size)]

/**
 * 끌기를 놓았을 때 붙을 카드 번호. [start]는 끌기 전 번호, [position]은 지금 위치다. 가장 가까운 칸으로 붙되, 반 칸이 안 되어도
 * [DRAG_SETTLE_SLOTS] 이상 끌었으면 그 방향으로 한 칸 넘긴다.
 */
internal fun carouselSettle(start: Int, position: Float): Int {
    val nearest = position.roundToInt()
    val moved = position - start
    return when {
        nearest != start -> nearest
        moved >= DRAG_SETTLE_SLOTS -> start + 1
        moved <= -DRAG_SETTLE_SLOTS -> start - 1
        else -> start
    }
}

private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

/** 칸별 모양: 가운데, 한 칸(작은 카드), 두 칸(반쯤 가린 장식), 세 칸(화면 밖). */
private val SLOTS = listOf(
    CarouselSlot(centerY = 0f, width = 0.48f, height = 0.28f, emphasis = 1f, alpha = 1f, labelAlpha = 1f),
    CarouselSlot(centerY = 0.25f, width = 0.35f, height = 0.17f, emphasis = 0f, alpha = 1f, labelAlpha = 1f),
    CarouselSlot(centerY = 0.42f, width = 0.28f, height = 0.17f, emphasis = 0f, alpha = 0.85f, labelAlpha = 0f),
    CarouselSlot(centerY = 0.58f, width = 0.28f, height = 0.17f, emphasis = 0f, alpha = 0f, labelAlpha = 0f)
)

/** 이만큼(칸) 끌면 놓았을 때 한 칸 넘긴다. */
internal const val DRAG_SETTLE_SLOTS = 0.3f

/** 피그마 프로토타입 Smart Animate 곡선과 길이. */
internal val CarouselEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
internal const val CAROUSEL_DURATION_MS = 320
