package com.a207.runkk.app.ui.avatar

/**
 * 얼굴 만들기 화면 단계(design-oo 05-30 6.2 상태도, 피그마 AV-7 사진 업로드 흐름 1안 62329:1710).
 * - [CHOOSE] 1 얼굴 만들기: 셀카 찍기·앨범·기본 얼굴.
 * - [CONFIRM] 2 사진 확인: 고른 사진을 크게 보이고 동의 뒤 「만들기 시작」.
 * - [SENDING] 보내는 중(접수 기다림).
 * - [WORKING] 3 만드는 중: 보낸 사진, 돌아가는 고리, 「나중에 다시 확인해도 돼요」. 화면을 떠나도 워커가 이어 간다.
 * - [DONE] 새 얼굴: 워커가 결과를 적용했다.
 * - [FAILED] 3′ 실패: 사유와 「오늘 횟수는 그대로예요」, 다른 사진 고르기·기본 얼굴 쓰기.
 */
enum class SetupPhase {
    CHOOSE,
    CONFIRM,
    SENDING,
    WORKING,
    DONE,
    FAILED
}
