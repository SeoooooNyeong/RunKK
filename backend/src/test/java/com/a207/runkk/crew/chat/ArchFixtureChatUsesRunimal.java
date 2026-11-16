package com.a207.runkk.crew.chat;

import com.a207.runkk.runimal.RunimalQuery;
import java.util.Collection;

/** ArchUnit 음성 시험 전용(규칙 1 위반, 설계 5.31 [임시:D-성-231]). crew.chat에 새로 열린 화살표는 avatar 하나뿐이다. */
public class ArchFixtureChatUsesRunimal {
    public int typesOf(RunimalQuery query, Collection<Long> ids) {
        return query.typesOf(ids).size();
    }
}
