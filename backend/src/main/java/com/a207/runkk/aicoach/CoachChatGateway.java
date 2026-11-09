package com.a207.runkk.aicoach;

import java.io.IOException;

public interface CoachChatGateway {
    Stream open(long memberId, CoachQuestion question);

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
