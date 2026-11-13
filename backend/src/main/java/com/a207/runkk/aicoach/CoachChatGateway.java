package com.a207.runkk.aicoach;

import java.io.IOException;
import java.util.List;

public interface CoachChatGateway {
    Stream open(long memberId, CoachQuestion question);

    /**
     * 대화의 지난 문답을 오래된 순으로 읽는다(S15P31A207-300). ai에 기록이 없으면 빈 목록이다. ai가 다른 사용자의 대화라고 하면
     * CONVERSATION_EXPIRED, 연결·응답이 잘못되면 RETRY로 거절한다.
     */
    List<CoachMessage> messages(long memberId, String conversationId);

    interface Stream extends AutoCloseable {
        /** 정상 done에서만 돌아온다. 끊긴 스트림은 IOException으로 구분한다. */
        void read(Listener listener) throws IOException;

        @Override
        void close() throws IOException;
    }

    interface Listener {
        void started(String conversationId) throws IOException;

        void delta(String text) throws IOException;

        void done() throws IOException;
    }
}
