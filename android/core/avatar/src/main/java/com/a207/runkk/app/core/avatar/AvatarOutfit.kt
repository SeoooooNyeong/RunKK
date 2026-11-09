package com.a207.runkk.app.core.avatar

/**
 * 부위별 의상 번호(상의·하의·신발 각각 0..[MAX_ID]). 0은 기본 의상(흰 반팔티, 검은 반바지, 흰 신발)이고, 추가 의상은 기본형을
 * 대체하지 않는 별도 선택지다. 번호별 의상은 PoC OutfitStyles 이름 순서와 같다.
 */
data class AvatarOutfit(val top: Int = 0, val bottom: Int = 0, val shoes: Int = 0) {
    init {
        require(top in 0..MAX_ID && bottom in 0..MAX_ID && shoes in 0..MAX_ID) { "outfit id must be 0..$MAX_ID" }
    }

    val isDefault: Boolean get() = top == 0 && bottom == 0 && shoes == 0

    companion object {
        /** 부위별 마지막 의상 번호. */
        const val MAX_ID = 6
    }
}
