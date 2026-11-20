package com.a207.runkk.app.core.avatar

import java.util.Base64

/**
 * 셀카 생성 결과 입력 정책(PoC SelfiePolicy·GeneratedAvatarLayers.decode): 결과 JSON과 Data URL 길이 상한, PNG Data URL 형식,
 * 1024×1024 공통 좌표. 사진 업로드 한도와 작업 번호 검사는 기존 셀카 비동기 경로(SelfieEncoder, api.avatar)가 맡는다.
 */
internal object HeadResultPolicy {
    /** 8 PNG와 메타가 같은 좌표를 쓰는 캔버스 한 변. */
    const val CANVAS = 1024

    /** 결과 JSON 문자 수 상한(PoC MAX_RESPONSE 48MiB). */
    const val MAX_RESULT_CHARS = 48 * 1024 * 1024

    /** PNG Data URL 한 개의 문자 수 상한(PoC 12MiB). */
    const val MAX_DATA_URL_CHARS = 12 * 1024 * 1024

    const val DATA_URL_PREFIX = "data:image/png;base64,"

    private val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private const val IHDR_WIDTH_OFFSET = 16
    private const val IHDR_END = 24

    fun checkResultLength(chars: Int) {
        require(chars in 1..MAX_RESULT_CHARS) { "avatar result is empty or too large" }
    }

    /** "data:image/png;base64,…"를 풀어 1024×1024 PNG 바이트를 돌려준다. 형식·크기가 다르면 IllegalArgumentException. */
    fun decodePng(field: String, dataUrl: String): ByteArray {
        require(dataUrl.length <= MAX_DATA_URL_CHARS && dataUrl.startsWith(DATA_URL_PREFIX)) {
            "$field is not a PNG data URL"
        }
        val bytes = try {
            Base64.getMimeDecoder().decode(dataUrl.substring(DATA_URL_PREFIX.length))
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("$field is not base64", error)
        }
        return checkPng(field, bytes)
    }

    /** [bytes]가 1024×1024 PNG면 그대로, 아니면 IllegalArgumentException(층별로 받은 머리, S15P31A207-300). */
    fun checkPng(field: String, bytes: ByteArray): ByteArray {
        require(pngSize(bytes) == CANVAS to CANVAS) { "$field must be a ${CANVAS}x$CANVAS PNG" }
        return bytes
    }

    /** PNG 머리(서명과 IHDR)의 가로·세로. PNG가 아니면 null. 그림을 풀지 않고 읽는다. */
    fun pngSize(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < IHDR_END || !PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }) return null
        fun int(offset: Int) = (offset until offset + 4).fold(0) { value, index ->
            (value shl 8) or
                (bytes[index].toInt() and 0xff)
        }
        return int(IHDR_WIDTH_OFFSET) to int(IHDR_WIDTH_OFFSET + 4)
    }
}
