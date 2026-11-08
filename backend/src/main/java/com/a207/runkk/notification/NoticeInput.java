package com.a207.runkk.notification;

import java.util.List;

/**
 * 운영자 공지 입력(S15P31A207-275, 관리 API POST /admin/notifications/notices). 형식(개수, 길이)은
 * web.admin.notification이 검사하고 링크 규칙은 NoticeService가 검사한다.
 *
 * @param memberIds 받을 회원 ID(1~100명, 겹치면 한 번만 보낸다)
 * @param title 제목(앱 알림함 제목, 푸시 제목)
 * @param body 본문(알림함 내용, 푸시 본문)
 * @param linkUrl 누르면 바깥 브라우저로 열 구글 폼 https 주소, 없으면 null
 */
public record NoticeInput(List<Long> memberIds, String title, String body, String linkUrl) {

    public NoticeInput {
        memberIds = List.copyOf(memberIds);
    }
}
