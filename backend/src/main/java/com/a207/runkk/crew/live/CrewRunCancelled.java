package com.a207.runkk.crew.live;

import com.a207.runkk.crew.CrewRunMessage;

/**
 * 크루런 취소 신호(설계 5.7.2, FR-148). 준비 완료가 2명 미만이면 갈래요 참가자에게 보낸다.
 *
 * @param postId 크루런 게시글 ID
 * @param reason 취소 사유 문구
 */
public record CrewRunCancelled(long postId, String reason) implements CrewRunMessage {

    @Override
    public String type() {
        return "CrewRunCancelled";
    }
}
