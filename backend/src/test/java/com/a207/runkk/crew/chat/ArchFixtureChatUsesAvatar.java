package com.a207.runkk.crew.chat;

import com.a207.runkk.avatar.AvatarQuery;
import java.util.Collection;

/** ArchUnit 음성 시험 전용(규칙 1 허용, 설계 5.31 [임시:D-성-231]). crew.chat은 avatar의 얼굴 키 조회를 쓸 수 있다. */
public class ArchFixtureChatUsesAvatar {
    public int facesOf(AvatarQuery query, Collection<Long> ids) {
        return query.faceKeysOf(ids).size();
    }
}
