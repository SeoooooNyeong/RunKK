package com.a207.runkk.crew;

import com.a207.runkk.crew.chat.ChatCursor;

/** ArchUnit 음성 시험 전용(규칙 1 위반, TC-6209 ⑤). crew는 crew.chat을 import하지 않는다(crew.chat → crew 한 방향). */
public class ArchFixtureCrewUsesChat {
    public int sizeOf(ChatCursor cursor) {
        return cursor.size();
    }
}
