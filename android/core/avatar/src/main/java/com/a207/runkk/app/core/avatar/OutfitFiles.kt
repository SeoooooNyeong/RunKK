package com.a207.runkk.app.core.avatar

/** 의상 자산 파일 읽기(assets 기준 경로 → 바이트, 없으면 null). 시험은 파일 폴더로 바꿔 끼운다. */
internal fun interface OutfitFiles {
    fun read(path: String): ByteArray?
}
