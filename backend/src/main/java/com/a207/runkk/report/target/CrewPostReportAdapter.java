package com.a207.runkk.report.target;

import com.a207.runkk.crew.CrewMembershipReader;
import com.a207.runkk.crew.CrewPostService;
import com.a207.runkk.crew.PostKind;
import com.a207.runkk.crew.ReportedPostView;
import com.a207.runkk.report.ReportTargetKind;
import com.a207.runkk.report.ReportedContent;
import com.a207.runkk.report.ReportedContentPort;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 신고 대상인 크루 게시글을 report에 보여 주고 숨긴다(설계 06-04 CrewPostReportAdapter, 조립 패키지 report.target, D-104,
 * D-115, D-135). crew의 신고용 공개 연산(reportedView·hideReported·unhideReported)만 부른다. PostKind ↔
 * ReportTargetKind 대응은 이 클래스 한 곳(H-31)이고 switch가 모든 PostKind를 다뤄 새 게시 종류가 생기면 컴파일이 깨진다.
 */
@Component
public class CrewPostReportAdapter implements ReportedContentPort {

    private final CrewPostService crewPostService;
    private final CrewMembershipReader membershipReader;

    public CrewPostReportAdapter(
            CrewPostService crewPostService, CrewMembershipReader membershipReader) {
        this.crewPostService = crewPostService;
        this.membershipReader = membershipReader;
    }

    /** 게시 종류의 신고 대상 종류. 크루런·대면 모임은 둘 다 SCHEDULE이다. */
    static ReportTargetKind targetKindOf(PostKind kind) {
        return switch (kind) {
            case CHEER -> ReportTargetKind.CHEER;
            case CREW_RUN, MEETUP -> ReportTargetKind.SCHEDULE;
            case NOTICE -> ReportTargetKind.NOTICE;
            case POLL -> ReportTargetKind.POLL;
            case BOARD -> ReportTargetKind.BOARD;
        };
    }

    @Override
    public boolean supports(ReportTargetKind kind) {
        return kind != ReportTargetKind.CHAT_MESSAGE;
    }

    /** 글이 없거나 삭제됐거나 종류가 맞지 않으면 빈 값. 숨긴 글도 hidden 참으로 돌려준다(D-115). */
    @Override
    public Optional<ReportedContent> find(ReportTargetKind kind, long targetId) {
        if (!supports(kind)) {
            return Optional.empty();
        }
        return crewPostService
                .reportedView(targetId)
                .filter(post -> targetKindOf(post.kind()) == kind)
                .map(this::content);
    }

    private ReportedContent content(ReportedPostView post) {
        return new ReportedContent(
                post.crewId(),
                post.authorId(),
                post.preview(),
                post.hidden(),
                Set.copyOf(membershipReader.activeMemberIds(post.crewId())));
    }

    @Override
    public void hide(ReportTargetKind kind, long targetId) {
        crewPostService.hideReported(targetId);
    }

    @Override
    public void unhide(ReportTargetKind kind, long targetId) {
        crewPostService.unhideReported(targetId);
    }
}
