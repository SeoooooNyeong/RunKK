package com.a207.runkk.crew.chat;

import com.a207.runkk.crew.RaceContributionAdded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 크루원이 유효 러닝을 마치면 크루 채팅에 러닝 완료 시스템 메시지를 남긴다(피그마 맞춤 W5, 사용자 결정 C7, S15P31A207-296). 기록이 크루 주간 합산에 처음
 * 더해진 커밋 뒤(RaceContributionAdded) 애플리케이션 실행기에서 돌아 기록 업로드 응답을 늦추지 않는다. 실패는 로그만 남기고 보정하지 않는다(지난 러닝을
 * 늦게 알리지 않는다).
 */
@Component
class ChatRunFinishedListener {

    private static final Logger log = LoggerFactory.getLogger(ChatRunFinishedListener.class);

    private final CrewChatService chatService;

    ChatRunFinishedListener(CrewChatService chatService) {
        this.chatService = chatService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContributed(RaceContributionAdded e) {
        try {
            chatService.postRunFinished(
                    e.crewId(), e.memberId(), e.recordId(), e.distance(), e.startedAt());
        } catch (RuntimeException failure) {
            log.error(
                    "run_finished_chat_failed crewId={} recordId={} error={}",
                    e.crewId(),
                    e.recordId(),
                    failure.getClass().getSimpleName(),
                    failure);
        }
    }
}
