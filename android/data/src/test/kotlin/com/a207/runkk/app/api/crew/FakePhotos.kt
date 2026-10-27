package com.a207.runkk.app.api.crew

/**
 * 시험용 사진 읽기·인코더. 주소마다 원본 바이트를 [originals]에 두고, 인코더는 시도(긴 변, 품질)를 [attempts]에 남긴 뒤 [encodedSize]
 * 바이트짜리 JPEG 머리를 돌려준다. 원본의 첫 바이트 뒤 두 바이트가 너비·높이 정보 대신 [size]로 그림 크기를 정한다.
 */
class FakePhotos {
    val originals = mutableMapOf<String, ByteArray>()
    val attempts = mutableListOf<Pair<Int, Int>>()
    var size: Pair<Int, Int> = 4_000 to 3_000

    /** (긴 변, 품질) → 결과 크기. 기본은 1MB보다 작다. */
    var encodedSize: (Int, Int) -> Int = { _, _ -> 500_000 }

    /** decode가 불렸을 때 그림을 돌려줄지(깨진 원본은 거짓). */
    var isDecodable = true

    internal val codec =
        object : PhotoCompressor.Codec {
            override fun decode(original: ByteArray): PhotoCompressor.Image? =
                if (isDecodable) PhotoCompressor.Image(size.first, size.second, Unit) else null

            override fun encode(image: PhotoCompressor.Image, longEdge: Int, quality: Int): ByteArray {
                attempts += longEdge to quality
                return jpeg(encodedSize(longEdge, quality))
            }
        }

    internal val compressor = PhotoCompressor({ originals[it] }, codec)

    companion object {
        /** JPEG 머리(FF D8 FF)로 시작하는 [size]바이트. */
        fun jpeg(size: Int): ByteArray = ByteArray(size).also {
            it[0] = 0xFF.toByte()
            it[1] = 0xD8.toByte()
            it[2] = 0xFF.toByte()
        }
    }
}
