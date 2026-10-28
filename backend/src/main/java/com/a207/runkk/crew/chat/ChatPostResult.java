package com.a207.runkk.crew.chat;

/**
 * 채팅 보내기 결과(설계 6.1.7a, FR-208).
 *
 * @param message 저장된 메시지 줄
 * @param created 처음 만들었으면 참(201), 같은 clientMessageId 재전송이면 거짓(200)
 */
public record ChatPostResult(ChatPage.Line message, boolean created) {}
