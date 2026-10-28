package com.a207.runkk.realtime;

/**
 * 앱 → 서버 실시간 메시지의 종류(설계 6.1.13, F.3.2, D-103). JSON type이 "Progress"이거나 없으면 InboundProgress(옛 앱
 * 호환), "ChatPresence"면 InboundChatPresence다. 모르는 type은 RealtimeMessageSerializer가 버린다.
 */
public sealed interface InboundMessage permits InboundProgress, InboundChatPresence {}
