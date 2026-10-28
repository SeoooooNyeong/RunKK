package com.a207.runkk.app.core.designsystem

/**
 * 입력 글자 수 규칙(D-143): 보이는 글자(코드 포인트)로 센다. 이모지처럼 UTF-16 두 칸을 쓰는 글자도 한 자이고, 자를 때 서로게이트
 * 쌍을 가르지 않는다. 서버도 같은 기준으로 세는 항목(닉네임, 채팅, 신고 상세, 게시글 본문, 투표 질문·선택지, 장소명)에 쓴다.
 * 크루 이름·크루 소개·제목은 서버가 UTF-16 길이로 세므로 쓰지 않는다.
 */
fun String.codePointLength(): Int = codePointCount(0, length)

/** 앞에서 [max]개의 코드 포인트만 남긴다(이모지를 반으로 자르지 않는다). */
fun String.takeCodePoints(max: Int): String {
    if (codePointLength() <= max) return this
    return substring(0, offsetByCodePoints(0, max))
}
