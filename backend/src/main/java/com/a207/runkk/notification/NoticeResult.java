package com.a207.runkk.notification;

import java.util.List;

/**
 * 운영자 공지 결과(S15P31A207-275). 푸시는 커밋 뒤 보내므로 결과에 넣지 않는다(기기 토큰이 없거나 FCM이 실패해도 알림함에는 있다).
 *
 * @param requested 겹침을 뺀 받을 회원 수
 * @param stored 알림함에 넣은 수
 * @param skippedMemberIds 없거나 탈퇴해 보내지 않은 회원 ID
 */
public record NoticeResult(int requested, int stored, List<Long> skippedMemberIds) {

    public NoticeResult {
        skippedMemberIds = List.copyOf(skippedMemberIds);
    }
}
