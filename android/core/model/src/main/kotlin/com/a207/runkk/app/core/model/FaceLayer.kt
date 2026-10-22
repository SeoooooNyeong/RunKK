package com.a207.runkk.app.core.model

/**
 * 셀카 아바타 얼굴 부위(서버 avatar 부위 이름). 선언 순서가 그리는 순서다(아래 → 위): 귀, 눈, 코, 입, 머리.
 */
enum class FacePart {
    EARS,
    EYES,
    NOSE,
    MOUTH,
    HAIR
}

/** 얼굴 부위 그림 한 장(PNG 바이트). 아바타·홈·프로필이 같은 그림을 겹쳐 그린다. */
class FaceLayer(val part: FacePart, val png: ByteArray) {
    override fun equals(other: Any?): Boolean = other is FaceLayer && other.part == part && other.png.contentEquals(png)

    override fun hashCode(): Int = 31 * part.hashCode() + png.contentHashCode()

    override fun toString(): String = "FaceLayer($part, ${png.size}B)"
}
