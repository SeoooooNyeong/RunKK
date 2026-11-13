package com.a207.runkk.crew;

import com.a207.runkk.common.StandardDistance;

/**
 * 크루런 모집글 수정(피그마 F-13d·F-13e, PATCH /crew-runs/{id}). 일시는 바꿀 수 없다. 화면의 네 칸을 모두 받는다(빠진 칸을 "그대로"로 보지
 * 않는다).
 *
 * @param title 제목(공백 제외 1~30자)
 * @param body 내용(0~200자), null이면 빈 문자열
 * @param distance 거리, null이면 제한 없음. 선택지 밖 값은 지금 거리와 같을 때만(기존 1km 유지)
 * @param repeat NONE이면 반복을 끝내고 WEEKLY면 이 글로 반복을 시작한다
 */
public record CrewRunEdit(
        String title, String body, StandardDistance distance, CrewRunRepeat repeat) {

    public CrewRunEdit {
        repeat = repeat == null ? CrewRunRepeat.NONE : repeat;
    }
}
