package com.a207.runkk.crew.live;

import com.a207.runkk.crew.CrewRunMessage;
import java.time.LocalDateTime;

/**
 * 크루런 변경 신호(설계 05-32 5.3, [임시:D-성-878]). 현재 값이 아닌 "무엇이 바뀌었다"는 사실만 담는다. 커밋 뒤 발송 시점의 현재 크루원 모두에게 기존
 * /ws JSON 채널(연결이 없으면 기존 폴링 대체)로 보내고, 앱은 열린 그 화면의 원본만 다시 읽는다. 모르는 resource는 무시한다.
 *
 * @param crewId 크루 ID
 * @param postId 크루런 게시글 ID
 * @param resource 바뀐 자료(SCHEDULE 목록, LOBBY 대기실, PROGRESS 진행, RESULT 결과)
 * @param revision 그 크루런의 변경 순번(역순·중복 판별)
 * @param eventId 중복 판별용 "crewId:postId:revision:resource"
 * @param occurredAt 변경 시각(서버 시간대)
 */
public record CrewRunChanged(
        long crewId,
        long postId,
        Resource resource,
        long revision,
        String eventId,
        LocalDateTime occurredAt)
        implements CrewRunMessage {

    /** 신호의 eventId 형식으로 만든다. */
    public static CrewRunChanged of(
            long crewId, long postId, Resource resource, long revision, LocalDateTime occurredAt) {
        return new CrewRunChanged(
                crewId,
                postId,
                resource,
                revision,
                crewId + ":" + postId + ":" + revision + ":" + resource.name(),
                occurredAt);
    }

    @Override
    public String type() {
        return "CrewRunChanged";
    }

    /** 바뀐 자료(설계 05-32 5.3 표). */
    public enum Resource {
        /** 모집, 수정, 취소, 참석, 시작, 종료: 열린 크루런 목록. */
        SCHEDULE,
        /** 입장, 준비, 이탈, 출발 계획, 후보 제외, 출발: 열린 그 대기실. */
        LOBBY,
        /** 중간 참여, 개인 종료, 서버 종료: 열린 그 진행 현황. */
        PROGRESS,
        /** 기록 연결, 기여 반영, 결과 변경: 열린 그 결과 화면. */
        RESULT
    }
}
