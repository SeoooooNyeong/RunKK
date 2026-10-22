package com.a207.runkk.crew.live;

import com.a207.runkk.crew.CrewRunMessage;

/**
 * 크루런 종료 신호(설계 5.7.2, FR-191). 앱은 결과 화면(GET /crew-runs/{id}/result)을 연다.
 *
 * @param postId 크루런 게시글 ID
 */
public record CrewRunEnded(long postId) implements CrewRunMessage {

    @Override
    public String type() {
        return "CrewRunEnded";
    }
}
