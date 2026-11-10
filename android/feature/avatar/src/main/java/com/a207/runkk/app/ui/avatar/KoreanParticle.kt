package com.a207.runkk.app.ui.avatar

/**
 * 목적격 조사(을/를): 마지막 글자가 받침 있는 한글이면 「을」, 아니면 「를」(실기기 DV3-6). 한글이 아니면(영문·숫자) 받침 없음으로 본다.
 * 아이템 이름 뒤에 붙여 「새벽 바람막이를 살까요?」처럼 쓴다.
 */
internal fun objectParticle(word: String): String = if (hasFinalConsonant(word)) "을" else "를"

private fun hasFinalConsonant(word: String): Boolean {
    val last = word.trimEnd().lastOrNull() ?: return false
    if (last !in HANGUL_FIRST..HANGUL_LAST) return false
    return (last - HANGUL_FIRST) % FINAL_COUNT != 0
}

private const val HANGUL_FIRST = '가'
private const val HANGUL_LAST = '힣'
private const val FINAL_COUNT = 28
