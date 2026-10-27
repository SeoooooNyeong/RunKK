package com.a207.runkk.report;

import java.util.Optional;

/**
 * 신고 대상 콘텐츠 포트(설계 6.1.12a, D-104, D-115, D-135). report가 정의하고 조립 패키지 report.target이 구현한다
 * (CrewPostReportAdapter: CHEER·SCHEDULE·NOTICE·POLL·BOARD, ChatReportAdapter: CHAT_MESSAGE).
 * report는 crew·crew.chat을 모른다.
 */
public interface ReportedContentPort {

    /** 이 구현이 맡는 대상 종류인가. */
    boolean supports(ReportTargetKind kind);

    /** 대상이 없거나 삭제됐거나 종류가 맞지 않으면 빈 값. 숨긴 콘텐츠는 돌려주고 hidden을 참으로 싣는다(D-115). */
    Optional<ReportedContent> find(ReportTargetKind kind, long targetId);

    /** 콘텐츠를 숨긴다(crew_post.hidden, crew_chat_message.hidden). 없거나 이미 숨겼으면 그대로. */
    void hide(ReportTargetKind kind, long targetId);

    /** 숨김을 푼다(운영자 기각). */
    void unhide(ReportTargetKind kind, long targetId);
}
