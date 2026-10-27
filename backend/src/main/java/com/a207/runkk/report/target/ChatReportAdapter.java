package com.a207.runkk.report.target;

import com.a207.runkk.crew.CrewMembershipReader;
import com.a207.runkk.crew.chat.CrewChatService;
import com.a207.runkk.crew.chat.ReportedChatView;
import com.a207.runkk.report.ReportTargetKind;
import com.a207.runkk.report.ReportedContent;
import com.a207.runkk.report.ReportedContentPort;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 신고 대상인 채팅 메시지를 report에 보여 주고 숨긴다(설계 06-04 ChatReportAdapter, 조립 패키지 report.target, D-104, D-115,
 * D-135). crew.chat의 신고용 공개 연산만 부르고, 숨기면 crew.chat이 커밋 뒤 방 안 크루원에게 CrewChatRemoved(HIDDEN)를 보낸다.
 */
@Component
public class ChatReportAdapter implements ReportedContentPort {

    private final CrewChatService crewChatService;
    private final CrewMembershipReader membershipReader;

    public ChatReportAdapter(
            CrewChatService crewChatService, CrewMembershipReader membershipReader) {
        this.crewChatService = crewChatService;
        this.membershipReader = membershipReader;
    }

    @Override
    public boolean supports(ReportTargetKind kind) {
        return kind == ReportTargetKind.CHAT_MESSAGE;
    }

    /** 메시지가 없거나 삭제됐으면 빈 값. 숨긴 메시지도 hidden 참으로 돌려준다(D-115). */
    @Override
    public Optional<ReportedContent> find(ReportTargetKind kind, long targetId) {
        if (!supports(kind)) {
            return Optional.empty();
        }
        return crewChatService.reportedView(targetId).map(this::content);
    }

    private ReportedContent content(ReportedChatView message) {
        return new ReportedContent(
                message.crewId(),
                message.authorId(),
                message.preview(),
                message.hidden(),
                Set.copyOf(membershipReader.activeMemberIds(message.crewId())));
    }

    @Override
    public void hide(ReportTargetKind kind, long targetId) {
        crewChatService.hideReported(targetId);
    }

    @Override
    public void unhide(ReportTargetKind kind, long targetId) {
        crewChatService.unhideReported(targetId);
    }
}
