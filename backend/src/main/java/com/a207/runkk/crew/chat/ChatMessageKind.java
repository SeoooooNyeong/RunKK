package com.a207.runkk.crew.chat;

/**
 * 채팅 메시지 종류(피그마 맞춤 W5, 사용자 결정 C7, S15P31A207-296). TEXT는 크루원이 보낸 글, RUN_FINISHED는 크루원이 유효 러닝을 마쳤을 때
 * 서버가 남기는 시스템 메시지(닉네임·거리만)다.
 */
public enum ChatMessageKind {
    TEXT,
    RUN_FINISHED
}
