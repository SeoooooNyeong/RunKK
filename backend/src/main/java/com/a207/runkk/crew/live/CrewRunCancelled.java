package com.a207.runkk.crew.live;

import com.a207.runkk.crew.CrewRunMessage;

/**
 * 크루런 취소 신호(설계 5.7.2, FR-148). 준비 완료가 최소 준비 인원(운영 기준, 기본 2명)보다 적으면 갈래요 참가자에게 보낸다.
 *
 * @param postId 크루런 게시글 ID
 * @param reason 취소 사유 문구(화면에 그대로 보인다)
 */
public record CrewRunCancelled(long postId, String reason) implements CrewRunMessage {

    @Override
    public String type() {
        return "CrewRunCancelled";
    }
}
