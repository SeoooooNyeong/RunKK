package com.a207.runkk.app.core.model

/**
 * 얼굴을 그릴 자리(Jira 308). 셀카 머리 층을 받을 크기를 자리마다 고른다: 목록 원(랭킹·크루·채팅 행, 80dp 이하) 256, 큰 프로필 원
 * (계정·다른 러너 프로필, 약 100dp) 512, 큰 아바타 1024. 같은 머리를 더 큰 크기로 이미 가지고 있으면 작은 크기를 따로 받지 않는다.
 * 저장소(api.*)와 화면이 함께 보므로 얼굴 층(FaceLayer)과 같은 core.model에 둔다.
 */
enum class FaceUse(private val pixels: Int) {
    LIST(256),
    PROFILE(512),
    FULL(1024);

    /** 받을 층 한 변(px, 1024 공통 캔버스를 줄인 크기). */
    fun size(): Int = pixels
}
