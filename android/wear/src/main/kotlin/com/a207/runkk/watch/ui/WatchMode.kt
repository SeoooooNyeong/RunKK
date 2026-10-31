package com.a207.runkk.watch.ui

/**
 * 워치 메인 카드 종류(3.5, FR-243). 카드 순서는 자유런, 배틀런, 셀프런, 셀럽런이고 마지막 다음은 처음으로 이어진다(순환).
 * 크루런은 워치에서 하지 않는다(W04).
 */
enum class WatchMode {
    FREE,
    BATTLE,
    SELF,
    CELEB;

    /** 다음 카드(셀럽런 다음은 자유런). */
    fun next(): WatchMode = entries[(ordinal + 1) % entries.size]

    /** 이전 카드(자유런 이전은 셀럽런). */
    fun previous(): WatchMode = entries[(ordinal + entries.size - 1) % entries.size]
}
