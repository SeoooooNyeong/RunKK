package com.a207.runkk.app.domain.sync

/**
 * 시작 에셋 미리 받기의 단계(S15P31A207-345 2차). 로딩 화면 문구를 고르는 데 쓴다(남은 파일이 가장 많은 단계의 문구).
 */
enum class PreloadStage {
    /** 내 아바타 머리 받기. */
    MY_AVATAR_FETCH,

    /** 내 아바타 합성·첫 판 준비(홈 큰 아바타). */
    MY_AVATAR_PREPARE,

    /** 랭킹 첫 화면 응답과 보이는 러너 머리. */
    RANKING,

    /** 셀럽런 셀럽 목록·기록. */
    CELEB,

    /** 그 밖의 첫 화면 데이터(기록·크루·친구·홈). */
    OTHERS
}
