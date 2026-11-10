package com.a207.runkk.app.core.avatar

/**
 * 승인 Rive 파일의 검수용 바탕을 투명하게 한다(실기기 결함 DV-성-3). 네 파일의 아트보드에는 PoC가 리뷰 화면 바탕색과 맞추려고 넣은
 * 사각형 도형(이름 「Background」·「Review background」, 아트보드 전체, SolidColor FFF6EFE8)이 맨 아래에 있어 앱 카드 배경을
 * 가린다. Android Rive 런타임에는 도형을 숨기는 API가 없고 파일은 승인본(SHA 시험) 그대로 둬야 하므로, 읽은 바이트 사본에서 그 도형의
 * 첫 SolidColor 색(키 37, 리틀엔디언 ARGB)의 알파 바이트만 0으로 바꾼다. 도형·색 구조는 Rive 이진 형식(객체 형식 키 varuint, 속성 키
 * varuint + 값, 0으로 끝) 그대로다.
 */
internal object RivReviewBackground {
    private const val NAME_KEY: Byte = 4
    private const val SOLID_COLOR_TYPE: Byte = 0x12
    private const val COLOR_VALUE_KEY: Byte = 0x25
    private const val ALPHA_OFFSET = 3 + 3
    private const val SEARCH_WINDOW = 128
    private const val MAX_NAME = 0x7f
    private const val NAME_SUFFIX = "background"

    /** [riv]의 사본. 이름이 background로 끝나는 도형마다 그 뒤 첫 SolidColor를 투명하게 한다. 없으면 같은 내용의 사본이다. */
    fun hidden(riv: ByteArray): ByteArray {
        val out = riv.copyOf()
        var i = 0
        while (i < out.size - 2) {
            val length = out[i + 1].toInt()
            if (out[i] == NAME_KEY && length in NAME_SUFFIX.length..MAX_NAME && i + 2 + length <= out.size &&
                isBackgroundName(out, i + 2, length)
            ) {
                val nameEnd = i + 2 + length
                solidColorAfter(out, nameEnd)?.let { out[it + ALPHA_OFFSET] = 0 }
                i = nameEnd
            } else {
                i++
            }
        }
        return out
    }

    private fun isBackgroundName(bytes: ByteArray, start: Int, length: Int): Boolean {
        val name = String(bytes, start, length, Charsets.US_ASCII)
        return name.all { it.code in PRINTABLE } && name.lowercase().endsWith(NAME_SUFFIX)
    }

    /** [from] 뒤 [SEARCH_WINDOW] 바이트 안의 첫 SolidColor 객체 시작(앞 객체 끝 0 + 형식 18 + 색 키 37). 없으면 null. */
    private fun solidColorAfter(bytes: ByteArray, from: Int): Int? {
        val last = minOf(bytes.size - (ALPHA_OFFSET + 1), from + SEARCH_WINDOW)
        return (from..last).firstOrNull { at ->
            bytes[at] == 0.toByte() && bytes[at + 1] == SOLID_COLOR_TYPE && bytes[at + 2] == COLOR_VALUE_KEY
        }
    }

    private val PRINTABLE = 0x20..0x7e
}
