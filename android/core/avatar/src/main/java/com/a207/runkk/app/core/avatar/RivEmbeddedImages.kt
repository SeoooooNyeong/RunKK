package com.a207.runkk.app.core.avatar

/**
 * riv 바이트에 내장된 이미지(이름 → PNG)를 네이티브 Rive 없이 읽는다. 미리 준비([prepareRiveAvatarLook])가 Rive 뷰를 만들기 전에
 * 서기 합성을 하려면 로더(`FrontAvatarAssetLoader`·`RunAvatarAssetLoader`)가 받을 원본 바이트가 필요하기 때문이다.
 *
 * Rive 이진 형식에서 내장 이미지는 `ImageAsset`(형식 105, 이름 = 속성 203 문자열) 객체 바로 뒤의 `FileAssetContents`(형식 106,
 * 바이트 = 속성 212) 객체다: `… CB 01 <길이> <이름> 00 | 6A D4 01 <varuint 길이> <PNG>`. 이름은 확장자를 뺀다(로더와 같음).
 * PNG 서명과 길이가 맞는 것만 받고, 찾은 내용 안은 다시 찾지 않는다. 읽기 전용이고 막는 읽기라 IO에서 부른다.
 */
internal object RivEmbeddedImages {
    private const val CONTENTS_TYPE: Byte = 0x6A
    private const val BYTES_KEY_LOW: Byte = 0xD4.toByte()
    private const val BYTES_KEY_HIGH: Byte = 0x01
    private const val NAME_KEY_LOW: Byte = 0xCB.toByte()
    private const val NAME_KEY_HIGH: Byte = 0x01
    private const val MAX_NAME = 0x7F
    private const val VARUINT_MORE = 0x80
    private const val VARUINT_BITS = 0x7F
    private const val VARUINT_SHIFT = 7
    private const val MAX_VARUINT_BYTES = 5
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun read(riv: ByteArray): Map<String, ByteArray> {
        val images = LinkedHashMap<String, ByteArray>()
        var i = 1
        while (i + 3 < riv.size) {
            if (riv[i] == CONTENTS_TYPE && riv[i - 1] == 0.toByte() && riv[i + 1] == BYTES_KEY_LOW &&
                riv[i + 2] == BYTES_KEY_HIGH
            ) {
                val found = contentsAt(riv, i)
                if (found != null) {
                    val (name, start, length) = found
                    images[name.substringBeforeLast('.')] = riv.copyOfRange(start, start + length)
                    i = start + length
                    continue
                }
            }
            i++
        }
        return images
    }

    /** [at](형식 106 자리)의 내용이 맞으면 (앞 객체 이름, PNG 시작, 길이). */
    private fun contentsAt(riv: ByteArray, at: Int): Triple<String, Int, Int>? {
        val (length, start) = varuint(riv, at + 3) ?: return null
        if (length < PNG_SIGNATURE.size || start + length > riv.size) return null
        if (PNG_SIGNATURE.indices.any { riv[start + it] != PNG_SIGNATURE[it] }) return null
        val name = nameBefore(riv, at - 1) ?: return null
        return Triple(name, start, length)
    }

    /** 앞 객체 끝([end] = 0 바이트) 바로 앞의 이름 속성(203) 문자열. */
    private fun nameBefore(riv: ByteArray, end: Int): String? = (1..MAX_NAME).firstNotNullOfOrNull { length ->
        val lengthAt = end - length - 1
        val isName = lengthAt >= 2 && riv[lengthAt].toInt() == length && riv[lengthAt - 2] == NAME_KEY_LOW &&
            riv[lengthAt - 1] == NAME_KEY_HIGH
        if (!isName) return@firstNotNullOfOrNull null
        val name = String(riv, lengthAt + 1, length, Charsets.US_ASCII)
        name.takeIf { text -> text.all { it.code in PRINTABLE } }
    }

    private fun varuint(bytes: ByteArray, from: Int): Pair<Int, Int>? {
        var value = 0L
        var shift = 0
        var at = from
        repeat(MAX_VARUINT_BYTES) {
            if (at >= bytes.size) return null
            val byte = bytes[at++].toInt() and 0xFF
            value = value or ((byte and VARUINT_BITS).toLong() shl shift)
            if (byte and VARUINT_MORE == 0) return if (value <= Int.MAX_VALUE) value.toInt() to at else null
            shift += VARUINT_SHIFT
        }
        return null
    }

    private val PRINTABLE = 0x20..0x7E
}
