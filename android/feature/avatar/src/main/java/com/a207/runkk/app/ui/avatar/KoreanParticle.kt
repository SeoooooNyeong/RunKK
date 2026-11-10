package com.a207.runkk.app.ui.avatar

/**
 * 목적격 조사(을/를): 마지막 글자가 받침 있는 한글이면 「을」, 아니면 「를」(실기기 DV3-6). 끝의 괄호·공백·기호는 건너뛰고(「러닝 캡 (한정)」은
 * 「정」), 숫자로 끝나면 숫자 읽는 소리로 본다(0 영·1 일·3 삼·6 육·7 칠·8 팔은 받침 있음, 2 이·4 사·5 오·9 구는 없음, 리뷰 RC-5).
 * 영문 등 다른 글자로 끝나면 받침 없음으로 본다. 아이템 이름 뒤에 붙여 「새벽 바람막이를 살까요?」처럼 쓴다.
 */
internal fun objectParticle(word: String): String = if (hasFinalConsonant(word)) "을" else "를"

private fun hasFinalConsonant(word: String): Boolean {
    val last = word.lastOrNull { it.isLetterOrDigit() } ?: return false
    return when {
        last in HANGUL_FIRST..HANGUL_LAST -> (last - HANGUL_FIRST) % FINAL_COUNT != 0
        last in '0'..'9' -> last in DIGITS_WITH_FINAL
        else -> false
    }
}

private const val HANGUL_FIRST = '가'
private const val HANGUL_LAST = '힣'
private const val FINAL_COUNT = 28

/** 읽는 소리에 받침이 있는 숫자: 영·일·삼·육·칠·팔. */
private const val DIGITS_WITH_FINAL = "013678"
