package com.a207.runkk.crew.chat.live;

/**
 * 실시간 CrewChatRemoved의 사유(설계 5.7.2, D-101, D-104). 작성자·운영진 삭제는 DELETED, 신고 숨김은 HIDDEN이다.
 * common.ReasonCode가 아니라 메시지 자기 enum이고 앱 해독기는 모르는 상수가 오면 그 메시지를 버린다(D-85).
 */
public enum ChatRemovalReason {
    DELETED,
    HIDDEN
}
